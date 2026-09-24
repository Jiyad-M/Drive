package com.example.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.data.db.RoadObstacleDao
import com.example.data.model.RoadObstacleEntity
import com.example.data.model.TelemetryData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SensorAndRoadTracker(
    private val context: Context,
    private val obstacleDao: RoadObstacleDao,
    private val audioAlertManager: AudioAlertManager
) : SensorEventListener {

    private val TAG = "SensorAndRoadTracker"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    // Bump & Road verification tracking
    private var activeVerifyingObstacleId: String? = null
    private var verifiedLiftInWindow = false
    private var verifiedJerkInWindow = false
    private var verifiedSlowdownInWindow = false
    private var lastSpikeTimestamp = 0L

    // Accelerometer differential tracking (Jerk & Pothole/Gutter dip)
    private var lastAccelMagnitude = 9.8f
    private var lastAccelZ = 9.8f
    private var lastSensorTimestampNanos = 0L
    private var lastGutterDipTimestamp = 0L

    // Speed history for deceleration tracking (last 3.5 seconds)
    private val recentSpeedHistory = mutableListOf<Pair<Long, Float>>()
    // Locations where vehicle slowed down (lat, lon, count)
    private val slowdownLocationMap = mutableListOf<SlowdownHotspot>()

    data class SlowdownHotspot(
        val lat: Double,
        val lon: Double,
        var count: Int,
        var lastTime: Long
    )

    // Distance & Odometer Tracking
    private var lastLocationLat: Double? = null
    private var lastLocationLon: Double? = null
    var dailyRunKm: Double = 0.0
    var totalOdometerKm: Double = 12450.0
    var serviceRemainingKm: Double = 4850.0
    var serviceIntervalKm: Double = 5000.0
    private var distanceAccumulatedSinceSave: Double = 0.0
    var onOdometerUpdated: ((daily: Double, total: Double, service: Double) -> Unit)? = null

    // Sensitivity & Configuration
    var bumpSensitivityThreshold: Float = 14.0f // m/s^2 vertical/total magnitude spike
    var soundEnabled: Boolean = true
    var fakeBumpStrikeLimit: Int = 1 // Quick suppression of fake map bumps
    var isSimulating: Boolean = false

    // Cache of active obstacles in memory for fast distance calculations
    private var cachedObstacles: List<RoadObstacleEntity> = emptyList()

    init {
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        scope.launch {
            obstacleDao.getActiveObstacles().collect { list ->
                cachedObstacles = list
            }
        }
    }

    fun startListening() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true

                val now = System.currentTimeMillis()
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z)

                // Jerk calculation (rate of change of acceleration)
                val dt = if (lastSensorTimestampNanos > 0) {
                    (event.timestamp - lastSensorTimestampNanos) / 1_000_000_000f
                } else 0.05f

                val jerk = if (dt > 0.005f) {
                    abs(magnitude - lastAccelMagnitude) / dt
                } else 0f

                lastAccelMagnitude = magnitude
                lastAccelZ = z
                lastSensorTimestampNanos = event.timestamp

                _telemetry.update { current ->
                    current.copy(verticalAccel = magnitude)
                }

                // 1. Detect BIG GUTTER (Pothole / Road Drain):
                // Sudden downward drop (z < 5.8 m/s²) followed within 400ms by rebound impact jerk
                if (z < 5.8f) {
                    lastGutterDipTimestamp = now
                } else if (now - lastGutterDipTimestamp in 50..400 && (z > 13.5f || jerk > 35f)) {
                    lastGutterDipTimestamp = 0L
                    onBigGutterDetected(magnitude)
                }

                // 2. Detect LIFT (upward vertical spike on Z axis)
                val isUpwardLift = (magnitude > bumpSensitivityThreshold) || (z > bumpSensitivityThreshold)
                if (isUpwardLift) {
                    onVerticalBumpSpikeDetected(magnitude, isJerk = false)
                }

                // 3. Detect JERKING (suspension oscillation / rough bump crossing)
                val isJerking = jerk > 30f && (magnitude > 12.0f || z > 12.0f)
                if (isJerking && !isUpwardLift) {
                    onVerticalBumpSpikeDetected(magnitude, isJerk = true)
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagneticValues, 0, 3)
                hasGeomagnetic = true
            }
        }

        if (hasGravity && hasGeomagnetic) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, gravityValues, geomagneticValues)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (degrees < 0) degrees += 360f

                val cardinal = getCardinalDirection(degrees)
                _telemetry.update { current ->
                    current.copy(
                        compassDegrees = degrees,
                        compassCardinal = cardinal
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Requirement: Big gutter single beep
     * Auto-detect and store real road gutters/potholes in the database
     */
    fun onBigGutterDetected(magnitude: Float) {
        val now = System.currentTimeMillis()
        if (now - lastSpikeTimestamp < 1000L) return
        lastSpikeTimestamp = now

        val currentTelemetry = _telemetry.value
        val speedKmH = currentTelemetry.currentSpeedKmH
        val lat = currentTelemetry.latitude
        val lon = currentTelemetry.longitude

        if ((speedKmH >= 8f || isSimulating) && lat != 0.0 && lon != 0.0) {
            val existingNearby = cachedObstacles.any { obs ->
                obs.type == "BIG_GUTTER" && calculateDistanceMeters(lat, lon, obs.latitude, obs.longitude) < 25.0
            }

            if (!existingNearby) {
                val newGutter = RoadObstacleEntity(
                    id = "gutter_${System.currentTimeMillis()}",
                    type = "BIG_GUTTER",
                    latitude = lat,
                    longitude = lon,
                    heading = currentTelemetry.compassDegrees,
                    title = "Road Gutter / Pothole",
                    isAutoDetected = true,
                    isConfirmedReal = true,
                    sensorHitCount = 1
                )
                scope.launch {
                    obstacleDao.insertObstacle(newGutter)
                    // Big Gutter: 1 single beep
                    audioAlertManager.playGutterAlert(newGutter.id, soundEnabled)
                }
            }
        }
    }

    /**
     * Requirement: "3 time slow down vehcle or a lift or jurking add a bumb.
     * Store on data base for real road situation."
     */
    fun onVerticalBumpSpikeDetected(magnitude: Float, isJerk: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now - lastSpikeTimestamp < 800L) return
        lastSpikeTimestamp = now

        if (isJerk) {
            verifiedJerkInWindow = true
        } else {
            verifiedLiftInWindow = true
        }

        val currentTelemetry = _telemetry.value
        val speedKmH = currentTelemetry.currentSpeedKmH
        val lat = currentTelemetry.latitude
        val lon = currentTelemetry.longitude
        val heading = currentTelemetry.compassDegrees

        Log.d(TAG, "Bump sensor reaction detected (Lift: ${!isJerk}, Jerk: $isJerk, mag: $magnitude m/s², speed: $speedKmH)")

        if ((speedKmH >= 8f || isSimulating) && lat != 0.0 && lon != 0.0) {
            // Find if there is an existing obstacle within 25 meters
            val existing = cachedObstacles.firstOrNull { obs ->
                calculateDistanceMeters(lat, lon, obs.latitude, obs.longitude) < 25.0
            }

            if (existing != null) {
                // Confirm existing bump or promote it to real
                val updatedHitCount = existing.sensorHitCount + 1
                val confirmed = existing.copy(
                    isConfirmedReal = true,
                    isSuppressedFake = false,
                    strikeCount = 0,
                    sensorHitCount = updatedHitCount
                )
                scope.launch {
                    obstacleDao.insertObstacle(confirmed)
                }
            } else {
                // Add confirmed real speed bump based on physical lift or jerk
                val newBump = RoadObstacleEntity(
                    id = "auto_bump_${System.currentTimeMillis()}",
                    type = "SPEED_BUMP",
                    latitude = lat,
                    longitude = lon,
                    heading = heading,
                    title = "Verified Speed Bump",
                    isAutoDetected = true,
                    isConfirmedReal = true,
                    sensorHitCount = 1
                )
                scope.launch {
                    obstacleDao.insertObstacle(newBump)
                }

                _telemetry.update { current ->
                    current.copy(
                        lastDetectedBumpTime = now,
                        alertStatusMessage = "Verified Bump Added to Database"
                    )
                }
            }
        }
    }

    /**
     * Requirement: "3 time slow down vehcle or a lift or jurking add a bumb."
     * Track vehicle deceleration / slowdowns
     */
    private fun trackVehicleSlowdown(lat: Double, lon: Double, currentSpeedKmH: Float) {
        val now = System.currentTimeMillis()
        recentSpeedHistory.add(now to currentSpeedKmH)
        val cutoff = now - 3500L
        recentSpeedHistory.removeAll { it.first < cutoff }

        if (recentSpeedHistory.size >= 4) {
            val maxRecent = recentSpeedHistory.maxOf { it.second }
            val speedDrop = maxRecent - currentSpeedKmH

            // If vehicle dropped speed significantly (>= 7 km/h) approaching this spot
            if (speedDrop >= 7.0f && currentSpeedKmH in 8.0f..35.0f) {
                verifiedSlowdownInWindow = true

                // Check slowdown hotspot map
                var hotspot = slowdownLocationMap.firstOrNull {
                    calculateDistanceMeters(lat, lon, it.lat, it.lon) < 25.0
                }

                if (hotspot == null) {
                    hotspot = SlowdownHotspot(lat, lon, 1, now)
                    slowdownLocationMap.add(hotspot)
                } else if (now - hotspot.lastTime > 15_000L) {
                    // Count unique slowdown event (at least 15s between passes)
                    hotspot.count++
                    hotspot.lastTime = now

                    // Requirement: "3 time slow down vehcle ... add a bumb"
                    if (hotspot.count >= 3) {
                        val existingNearby = cachedObstacles.any { obs ->
                            calculateDistanceMeters(lat, lon, obs.latitude, obs.longitude) < 25.0
                        }

                        if (!existingNearby) {
                            Log.i(TAG, "3rd Slowdown detected at location -> Auto-registering real Speed Bump!")
                            val newBump = RoadObstacleEntity(
                                id = "slowdown_bump_${System.currentTimeMillis()}",
                                type = "SPEED_BUMP",
                                latitude = lat,
                                longitude = lon,
                                heading = _telemetry.value.compassDegrees,
                                title = "Verified Speed Bump (3x Slowdown)",
                                isAutoDetected = true,
                                isConfirmedReal = true,
                                sensorHitCount = 3
                            )
                            scope.launch {
                                obstacleDao.insertObstacle(newBump)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Process GPS Location update
     */
    fun onLocationUpdate(lat: Double, lon: Double, speedMps: Float, bearing: Float = 0f) {
        val speedKmH = speedMps * 3.6f

        // Track vehicle slowdown patterns
        trackVehicleSlowdown(lat, lon, speedKmH)

        // Dynamic Alert Distance: 50m for slow speed, scaling up with velocity
        val dynamicAlertDistance = if (speedKmH <= 30f) {
            50.0
        } else {
            50.0 + (speedKmH - 30.0) * 1.5
        }

        // Filter valid obstacles:
        // Do NOT alert if suppressed fake!
        val activeCandidates = cachedObstacles.filter { !it.isSuppressedFake }

        var nearest: RoadObstacleEntity? = null
        var minDistance = Double.MAX_VALUE

        for (obs in activeCandidates) {
            val dist = calculateDistanceMeters(lat, lon, obs.latitude, obs.longitude)
            if (dist < minDistance) {
                minDistance = dist
                nearest = obs
            }
        }

        val hasNearest = nearest != null && minDistance < 1000.0
        val isInsideAlertZone = hasNearest && minDistance <= dynamicAlertDistance

        var isApproachingBump = false
        var isApproachingSignal = false
        var isApproachingGutter = false
        var statusMsg = "Road Clear - Normal Speed"

        if (hasNearest && isInsideAlertZone && nearest != null) {
            when (nearest.type) {
                "BIG_GUTTER" -> {
                    isApproachingGutter = true
                    statusMsg = "CAUTION: Big Gutter / Pothole ahead (${minDistance.toInt()}m)!"
                    // Requirement: Big gutter single beep
                    audioAlertManager.playGutterAlert(nearest.id, soundEnabled)
                    checkFakeBumpCrossing(nearest, minDistance)
                }

                "SPEED_BUMP" -> {
                    isApproachingBump = true
                    statusMsg = "ATTENTION: Speed Bump ahead (${minDistance.toInt()}m)!"
                    // Requirement: Bump 3 beep
                    audioAlertManager.playSpeedBumpAlert(nearest.id, soundEnabled)
                    checkFakeBumpCrossing(nearest, minDistance)
                }

                "TRAFFIC_SIGNAL" -> {
                    isApproachingSignal = true
                    statusMsg = "CAUTION: Traffic Signal ahead (${minDistance.toInt()}m)!"
                    // Requirement: Signal long beep
                    audioAlertManager.playTrafficSignalAlert(nearest.id, soundEnabled)
                }
            }
        } else if (hasNearest) {
            statusMsg = "Next: ${nearest?.title} in ${minDistance.toInt()}m"
        }

        // Distance & Odometer tracking
        if (lastLocationLat != null && lastLocationLon != null) {
            val deltaMeters = calculateDistanceMeters(lastLocationLat!!, lastLocationLon!!, lat, lon)
            if (deltaMeters in 1.0..350.0 && (speedKmH > 1.5f || isSimulating)) {
                val deltaKm = deltaMeters / 1000.0
                accumulateTravelDistance(deltaKm)
            }
        }
        lastLocationLat = lat
        lastLocationLon = lon

        _telemetry.update { current ->
            current.copy(
                latitude = lat,
                longitude = lon,
                currentSpeedKmH = speedKmH,
                compassDegrees = if (bearing > 0) bearing else current.compassDegrees,
                compassCardinal = if (bearing > 0) getCardinalDirection(bearing) else current.compassCardinal,
                nearestObstacle = if (hasNearest) nearest else null,
                nearestDistanceMeters = if (hasNearest) minDistance else 0.0,
                dynamicAlertDistanceMeters = dynamicAlertDistance,
                isApproachingBump = isApproachingBump,
                isApproachingSignal = isApproachingSignal,
                isApproachingGutter = isApproachingGutter,
                alertStatusMessage = statusMsg,
                dailyRunKm = dailyRunKm,
                totalOdometerKm = totalOdometerKm,
                serviceRemainingKm = serviceRemainingKm,
                serviceIntervalKm = serviceIntervalKm
            )
        }
    }

    fun accumulateTravelDistance(deltaKm: Double) {
        if (deltaKm <= 0.0) return
        dailyRunKm += deltaKm
        totalOdometerKm += deltaKm
        serviceRemainingKm = (serviceRemainingKm - deltaKm).coerceAtLeast(0.0)
        distanceAccumulatedSinceSave += deltaKm

        _telemetry.update { current ->
            current.copy(
                dailyRunKm = dailyRunKm,
                totalOdometerKm = totalOdometerKm,
                serviceRemainingKm = serviceRemainingKm,
                serviceIntervalKm = serviceIntervalKm
            )
        }

        if (distanceAccumulatedSinceSave >= 0.05) {
            distanceAccumulatedSinceSave = 0.0
            onOdometerUpdated?.invoke(dailyRunKm, totalOdometerKm, serviceRemainingKm)
        }
    }

    fun setOdometerState(daily: Double, total: Double, remaining: Double, interval: Double) {
        dailyRunKm = daily
        totalOdometerKm = total
        serviceRemainingKm = remaining
        serviceIntervalKm = interval
        _telemetry.update { current ->
            current.copy(
                dailyRunKm = daily,
                totalOdometerKm = total,
                serviceRemainingKm = remaining,
                serviceIntervalKm = interval
            )
        }
    }

    fun resetDailyKm() {
        dailyRunKm = 0.0
        _telemetry.update { it.copy(dailyRunKm = 0.0) }
        onOdometerUpdated?.invoke(dailyRunKm, totalOdometerKm, serviceRemainingKm)
    }

    fun resetServiceCountdown() {
        serviceRemainingKm = serviceIntervalKm
        _telemetry.update { it.copy(serviceRemainingKm = serviceIntervalKm) }
        onOdometerUpdated?.invoke(dailyRunKm, totalOdometerKm, serviceRemainingKm)
    }

    fun updateServiceInterval(newInterval: Double) {
        serviceIntervalKm = newInterval
        if (serviceRemainingKm > newInterval) {
            serviceRemainingKm = newInterval
        }
        _telemetry.update {
            it.copy(serviceIntervalKm = newInterval, serviceRemainingKm = serviceRemainingKm)
        }
        onOdometerUpdated?.invoke(dailyRunKm, totalOdometerKm, serviceRemainingKm)
    }

    /**
     * Requirement:
     * "Map given bumb alert if there is no bumb a lot( 50 3 bumb each given. Ther is no a single bumb)
     * Use compass and accilmator censer if other posible sencer to investgate."
     *
     * When vehicle enters <= 14m of map bump: start observation window.
     * When vehicle departs > 16m: check if ANY bump signature was present
     * (lift, jerk, or slowdown). If NONE occurred:
     * IMMEDIATELY strike and suppress the fake map bump!
     */
    private fun checkFakeBumpCrossing(obstacle: RoadObstacleEntity, distance: Double) {
        if (distance <= 14.0) {
            if (activeVerifyingObstacleId != obstacle.id) {
                activeVerifyingObstacleId = obstacle.id
                verifiedLiftInWindow = false
                verifiedJerkInWindow = false
                verifiedSlowdownInWindow = false
            }
        } else if (distance > 16.0 && activeVerifyingObstacleId == obstacle.id) {
            val hadSensorResponse = verifiedLiftInWindow || verifiedJerkInWindow || verifiedSlowdownInWindow
            val bumpId = obstacle.id
            activeVerifyingObstacleId = null

            if (!hadSensorResponse) {
                // False map bump! Car passed through without feeling any bump or slowing down!
                val newStrikes = obstacle.strikeCount + 1
                Log.w(TAG, "Fake map obstacle detected ($bumpId): zero lift, jerk, or slowdown! Suppressing.")

                scope.launch {
                    // Suppress immediately so it stops giving false alerts!
                    obstacleDao.updateStrike(bumpId, newStrikes, suppressed = true)
                }

                _telemetry.update { current ->
                    current.copy(
                        alertStatusMessage = "Fake Map Alert Suppressed (Unfelt by Sensors)"
                    )
                }
            } else {
                Log.d(TAG, "Obstacle $bumpId verified by car sensors (Lift: $verifiedLiftInWindow, Jerk: $verifiedJerkInWindow, Slowdown: $verifiedSlowdownInWindow)")
                // Reinforce in database
                scope.launch {
                    val updated = obstacle.copy(
                        isConfirmedReal = true,
                        isSuppressedFake = false,
                        strikeCount = 0,
                        sensorHitCount = obstacle.sensorHitCount + 1
                    )
                    obstacleDao.insertObstacle(updated)
                }
            }
        }
    }

    private fun getCardinalDirection(degrees: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
        val index = ((degrees + 22.5f) / 45f).toInt() % 8
        return directions[index]
    }

    companion object {
        fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}
