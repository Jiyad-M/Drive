package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey
    val packageName: String,
    val activityName: String = "",
    val appName: String,
    val isAllowed: Boolean = true,
    val orderIndex: Int = 0,
    val isAppPair: Boolean = false,
    val secondaryPackageName: String = "",
    val secondaryAppName: String = "",
    val shortcutIntentUri: String = "",
    val shortcutId: String = ""
)

@Entity(tableName = "road_obstacles")
data class RoadObstacleEntity(
    @PrimaryKey
    val id: String,
    val type: String, // "SPEED_BUMP", "TRAFFIC_SIGNAL", "BIG_GUTTER"
    val latitude: Double,
    val longitude: Double,
    val heading: Float = 0f,
    val title: String = "",
    val isAutoDetected: Boolean = false,
    val strikeCount: Int = 0, // number of times crossed with zero vertical bump sensor response
    val isSuppressedFake: Boolean = false,
    val isConfirmedReal: Boolean = false, // confirmed by 3 slowdowns, lift, or jerk
    val sensorHitCount: Int = 1, // count of sensor confirmations
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "kiosk_settings")
data class KioskSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val pinCode: String = "1234",
    val wallpaperTheme: String = "map", // "map", "carbon", "midnight", "amber", "emerald"
    val customWallpaperColor: Long = 0xFF0B132BL,
    val soundEnabled: Boolean = true,
    val bumpSensitivity: Float = 14.0f, // vertical accel threshold in m/s^2 (normal gravity is ~9.8)
    val speedUnit: String = "KMH", // "KMH" or "MPH"
    val baseAlertDistanceMeters: Double = 50.0,
    val fakeBumpThreshold: Int = 1, // Suppress quickly if map bump is unfelt
    val simulationMode: Boolean = false,
    val dailyRunKm: Double = 0.0,
    val lastDailyDate: String = "",
    val totalOdometerKm: Double = 12450.0,
    val serviceIntervalKm: Double = 5000.0,
    val serviceRemainingKm: Double = 4850.0,
    // Launcher & Kiosk Lockdown
    val kioskLockEnabled: Boolean = true,
    val restrictBackgroundData: Boolean = true,
    // Battery Mode (Charger connected screen on, Disconnect off completely within delay)
    val batteryModeEnabled: Boolean = true,
    val disconnectDelaySec: Int = 5,
    val denyPowerButtonWakeup: Boolean = true,
    val doubleTapWakeupEnabled: Boolean = true,
    // 3-times Power Press to wake display on battery
    val power3TimesWakeupEnabled: Boolean = true,
    // Cash Received Voice Announcement
    val cashAnnouncementEnabled: Boolean = true,
    val cashTtsEnabled: Boolean = true,
    // App Pair (Dual app / split screen shortcut)
    val appPairPackage: String = "com.google.android.apps.maps",
    val appPairName: String = "Google Maps",
    val appPairIconPath: String = "",
    val showAppPairOnDashboard: Boolean = true
)

enum class ObstacleType(val displayName: String) {
    SPEED_BUMP("Speed Bump"),
    TRAFFIC_SIGNAL("Traffic Signal"),
    BIG_GUTTER("Big Gutter / Pothole")
}

data class CashAlertItem(
    val id: Long = System.currentTimeMillis(),
    val amountText: String,
    val sender: String,
    val fullMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class TelemetryData(
    val currentSpeedKmH: Float = 0f,
    val compassDegrees: Float = 0f,
    val compassCardinal: String = "N",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val verticalAccel: Float = 9.8f,
    val lastDetectedBumpTime: Long = 0L,
    val isApproachingBump: Boolean = false,
    val isApproachingSignal: Boolean = false,
    val isApproachingGutter: Boolean = false,
    val nearestObstacle: RoadObstacleEntity? = null,
    val nearestDistanceMeters: Double = 0.0,
    val dynamicAlertDistanceMeters: Double = 50.0,
    val alertStatusMessage: String = "System Ready - Scanning Road",
    val dailyRunKm: Double = 0.0,
    val serviceRemainingKm: Double = 4850.0,
    val serviceIntervalKm: Double = 5000.0,
    val totalOdometerKm: Double = 12450.0,
    val isChargerConnected: Boolean = false,
    val batteryPercent: Int = 100,
    val isStandbyActive: Boolean = false,
    val disconnectCountdown: Int? = null,
    val lastCashAlert: CashAlertItem? = null
)
