package com.example.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
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

    // Bump verification tracking
    private var activeVerifyingObstacleId: String? = null
    private var verifiedVerticalSpikeInWindow = false
    private var lastSpikeTimestamp = 0L

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
    var fakeBumpStrikeLimit: Int = 2
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

                // Calculate vertical / jerk magnitude
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z)

                // Detect sudden upward movement (either Z axis spike or deviation from 9.8m/s^2)
                val isUpwardSpike = (magnitude > bumpSensitivityThreshold) || (z > bumpSensitivityThreshold)

                _telemetry.update { current ->
                    current.copy(verticalAccel = magnitude)
                }

                if (isUpwardSpike) {
                    onVerticalBumpSpikeDetected(magnitude)
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
                // Azimuth in degrees [0, 360)
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
     * Called whenever a physical or simulated bump spike occurs
     */
    fun onVerticalBumpSpikeDetected(magnitude: Float) {
        val now = System.currentTimeMillis()
        if (now - lastSpikeTimestamp < 800L) return // Debounce quick spikes
        lastSpikeTimestamp = now

        verifiedVerticalSpikeInWindow = true

        val currentTelemetry = _telemetry.value
        val speedKmH = currentTelemetry.currentSpeedKmH
        val lat = currentTelemetry.latitude
        val lon = currentTelemetry.longitude
        val heading = currentTelemetry.compassDegrees

        Log.d(TAG, "Vertical bump spike detected: $magnitude m/s², speed: $speedKmH km/h")

        // User requirement: "also add bumbs withv up movement of car(Use gps, acclamatory censer, compass )"
        // Condition: Vehicle must be in motion (> 10 km/h or simulation mode) and valid GPS
        if ((speedKmH >= 10f || isSimulating) && lat != 0.0 && lon != 0.0) {
            // Check if there is already a registered bump within 25 meters
            val existingNearby = cachedObstacles.any { obs ->
                obs.type == "SPEED_BUMP" && calculateDistanceMeters(lat, lon, obs.latitude, obs.longitude) < 25.0
            }

            if (!existingNearby) {
                // Auto-add new speed bump detected by car's upward movement
                val newBump = RoadObstacleEntity(
                    id = "auto_bump_${System.currentTimeMillis()}",
                    type = "SPEED_BUMP",
                    latitude = lat,
                    longitude = lon,
                    heading = heading,
                    title = "Sensor-Detected Bump (${currentTelemetry.compassCardinal} $heading°)",
                    isAutoDetected = true,
                    strikeCount = 0,
                    isSuppressedFake = false
                )
                scope.launch {
                    obstacleDao.insertObstacle(newBump)
                    audioAlertManager.playNewBumpDetectedChime()
                }

                _telemetry.update { current ->
                    current.copy(
                        lastDetectedBumpTime = now,
                        alertStatusMessage = "Bump Auto-Logged via Accelerometer & Compass!"
                    )
                }
            }
        }
    }

    /**
     * Process GPS Location update
     */
    fun onLocationUpdate(lat: Double, lon: Double, speedMps: Float, bearing: Float = 0f) {
        val speedKmH = speedMps * 3.6f

        // Requirement: "If slow speed alert befor 50m incrase with speed of gehcle."
        // Base alert distance: 50m for slow speed (<= 30 km/h).
        // For higher speed, reaction distance scales: 50m + (speedKmH - 30) * 1.5m
        val dynamicAlertDistance = if (speedKmH <= 30f) {
            50.0
        } else {
            50.0 + (speedKmH - 30.0) * 1.5
        }

        // Find nearest active obstacle
        var nearest: RoadObstacleEntity? = null
        var minDistance = Double.MAX_VALUE

        for (obs in cachedObstacles) {
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
        var statusMsg = "Road Clear - Normal Speed"

        if (hasNearest && isInsideAlertZone && nearest != null) {
            if (nearest.type == "SPEED_BUMP") {
                isApproachingBump = true
                statusMsg = "ATTENTION: Speed Bump ahead in ${minDistance.toInt()}m!"
                // Trigger 3-beep alert for bump
                audioAlertManager.playSpeedBumpAlert(nearest.id, soundEnabled)

                // Track bump crossing window for fake bump detection
                checkFakeBumpCrossing(nearest, minDistance)
            } else if (nearest.type == "TRAFFIC_SIGNAL") {
                isApproachingSignal = true
                statusMsg = "CAUTION: Traffic Signal ahead in ${minDistance.toInt()}m!"
                // Trigger long beep alert for signal light
                audioAlertManager.playTrafficSignalAlert(nearest.id, soundEnabled)
            }
        } else if (hasNearest) {
            statusMsg = "Next: ${nearest?.title} in ${minDistance.toInt()}m"
        }

        // Real distance tracking for Daily Kilometer & Periodical Service Countdown
        if (lastLocationLat != null && lastLocationLon != null) {
            val deltaMeters = calculateDistanceMeters(lastLocationLat!!, lastLocationLon!!, lat, lon)
            // Filter realistic movement: e.g. delta between 1.0m and 350.0m, and vehicle is moving or in simulation
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
                alertStatusMessage = statusMsg,
                dailyRunKm = dailyRunKm,
                totalOdometerKm = totalOdometerKm,
                serviceRemainingKm = serviceRemainingKm,
                serviceIntervalKm = serviceIntervalKm
            )
        }
    }

    /**
     * Accumulate distance in km for daily run and countdown periodical service
     */
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

        // Persist when at least 50 meters (0.05 km) accumulated
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
     * Requirement: "If data give fake bumb detect it via snsers, it happen repeatedly update"
     * Handles crossing a known bump location.
     * When vehicle enters <= 15m: mark activeVerifyingObstacleId and reset spike tracker.
     * When vehicle departs > 18m from previously tracked bump:
     * Check if verifiedVerticalSpikeInWindow was true.
     * If false: it was a fake bump! Increment strikeCount.
     * If strikeCount reaches threshold: mark isSuppressedFake = true!
     */
    private fun checkFakeBumpCrossing(obstacle: RoadObstacleEntity, distance: Double) {
        if (distance <= 15.0) {
            if (activeVerifyingObstacleId != obstacle.id) {
                activeVerifyingObstacleId = obstacle.id
                verifiedVerticalSpikeInWindow = false
            }
        } else if (distance > 18.0 && activeVerifyingObstacleId == obstacle.id) {
            // Vehicle has now passed the bump coordinates
            val wasVerified = verifiedVerticalSpikeInWindow
            val bumpId = obstacle.id
            activeVerifyingObstacleId = null

            if (!wasVerified) {
                // No vertical accelerometer spike detected when passing over this bump!
                val newStrikes = obstacle.strikeCount + 1
                val shouldSuppress = newStrikes >= fakeBumpStrikeLimit
                Log.w(TAG, "Fake bump strike registered for $bumpId! Strikes: $newStrikes (Suppress: $shouldSuppress)")

                scope.launch {
                    obstacleDao.updateStrike(bumpId, newStrikes, shouldSuppress)
                }

                _telemetry.update { current ->
                    current.copy(
                        alertStatusMessage = if (shouldSuppress) {
                            "Fake Bump Verified: Auto-Removed (Repeatedly Unfelt)"
                        } else {
                            "Warning: Bump was not felt by car sensors (Strike $newStrikes/$fakeBumpStrikeLimit)"
                        }
                    )
                }
            } else {
                Log.d(TAG, "Bump $bumpId verified by accelerometer sensor!")
            }
        }
    }

    // Helper functions
    private fun getCardinalDirection(degrees: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
        val index = ((degrees + 22.5f) / 45f).toInt() % 8
        return directions[index]
    }

    companion object {
        fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0 // Earth radius in meters
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
