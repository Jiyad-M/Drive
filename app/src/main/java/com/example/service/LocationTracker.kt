package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationTracker(
    private val context: Context,
    private val sensorAndRoadTracker: SensorAndRoadTracker
) {
    private val TAG = "LocationTracker"
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var locationCallback: LocationCallback? = null
    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    var currentLat: Double = 37.7749
    var currentLon: Double = -122.4194
    var currentSpeedMps: Float = 0f

    @SuppressLint("MissingPermission")
    fun startRealLocationUpdates() {
        try {
            // Priority Balanced / High Accuracy
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500)
                .setMinUpdateIntervalMillis(800)
                .setMinUpdateDistanceMeters(1.0f)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    currentLat = location.latitude
                    currentLon = location.longitude
                    currentSpeedMps = location.speed
                    sensorAndRoadTracker.onLocationUpdate(
                        currentLat,
                        currentLon,
                        currentSpeedMps,
                        location.bearing
                    )
                }
            }

            fusedClient.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )

            // Also request last known location immediately
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    currentLat = loc.latitude
                    currentLon = loc.longitude
                    currentSpeedMps = loc.speed
                    sensorAndRoadTracker.onLocationUpdate(currentLat, currentLon, currentSpeedMps, loc.bearing)
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted, falling back or waiting for permission: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start fused location updates: ${e.message}")
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedClient.removeLocationUpdates(it)
            locationCallback = null
        }
        stopSimulation()
    }

    /**
     * Simulation mode drives forward from the current location towards obstacles
     * to test 3-beep bump alert, long beep traffic signal, speed-based alert distance,
     * accelerometer bump detection, and fake bump detection!
     */
    fun startSimulation(speedKmH: Float = 48f) {
        stopSimulation()
        sensorAndRoadTracker.isSimulating = true
        val speedMps = speedKmH / 3.6f

        simulationJob = scope.launch {
            var step = 0
            while (isActive) {
                // Move vehicle northward (~0.0001 degrees lat is ~11 meters)
                val deltaLat = (speedMps * 0.5) / 111_000.0
                currentLat += deltaLat
                currentSpeedMps = speedMps

                sensorAndRoadTracker.onLocationUpdate(
                    currentLat,
                    currentLon,
                    currentSpeedMps,
                    0f // Heading North
                )

                // Every 12 steps (~6 seconds), simulate hitting a physical bump with accelerometer spike
                step++
                if (step % 12 == 0) {
                    sensorAndRoadTracker.onVerticalBumpSpikeDetected(16.5f)
                }

                delay(500)
            }
        }
    }

    fun stopSimulation() {
        sensorAndRoadTracker.isSimulating = false
        simulationJob?.cancel()
        simulationJob = null
    }

    fun isSimulating(): Boolean = simulationJob?.isActive == true
}
