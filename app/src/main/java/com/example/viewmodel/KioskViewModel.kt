package com.example.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.R
import com.example.data.api.OverpassService
import com.example.data.db.AppDatabase
import com.example.data.model.AllowedAppEntity
import com.example.data.model.CashAlertItem
import com.example.data.model.KioskSettingsEntity
import com.example.data.model.RoadObstacleEntity
import com.example.data.model.TelemetryData
import com.example.service.AudioAlertManager
import com.example.service.CashNotificationListenerService
import com.example.service.InstalledAppItem
import com.example.service.InstalledAppsManager
import com.example.service.KioskAccessibilityService
import com.example.service.KioskDeviceAdminReceiver
import com.example.service.KioskTrackingForegroundService
import com.example.service.LocationTracker
import com.example.service.PowerAndEmergencyManager
import com.example.service.SensorAndRoadTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KioskViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val allowedAppDao = db.allowedAppDao()
    private val obstacleDao = db.roadObstacleDao()
    private val settingsDao = db.kioskSettingsDao()

    val audioAlertManager = AudioAlertManager(application)
    val sensorTracker = SensorAndRoadTracker(application, obstacleDao, audioAlertManager)
    val locationTracker = LocationTracker(application, sensorTracker)
    val installedAppsManager = InstalledAppsManager(application, allowedAppDao)
    val powerManager = PowerAndEmergencyManager(application, audioAlertManager)

    val allowedApps: StateFlow<List<AllowedAppEntity>> = allowedAppDao.getAllowedApps()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allObstacles: StateFlow<List<RoadObstacleEntity>> = obstacleDao.getAllObstacles()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val settings: StateFlow<KioskSettingsEntity> = settingsDao.getSettings()
        .map { it ?: KioskSettingsEntity() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            KioskSettingsEntity()
        )

    data class PowerState(
        val isCharging: Boolean,
        val batPct: Int,
        val standby: Boolean,
        val disconnectCd: Int?
    )

    private val powerStateFlow = combine(
        powerManager.isChargerConnected,
        powerManager.batteryPercent,
        powerManager.isStandbyActive,
        powerManager.disconnectCountdown
    ) { isCharging, batPct, standby, disconnectCd ->
        PowerState(isCharging, batPct, standby, disconnectCd)
    }

    val telemetry: StateFlow<TelemetryData> = combine(
        sensorTracker.telemetry,
        powerStateFlow,
        CashNotificationListenerService.lastCashAlert
    ) { sensorTel, powerState, cashAlert ->
        sensorTel.copy(
            isChargerConnected = powerState.isCharging,
            batteryPercent = powerState.batPct,
            isStandbyActive = powerState.standby,
            disconnectCountdown = powerState.disconnectCd,
            lastCashAlert = cashAlert
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TelemetryData())

    private val _installedAppsList = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedAppsList: StateFlow<List<InstalledAppItem>> = _installedAppsList.asStateFlow()

    private val _isFetchingObstacles = MutableStateFlow(false)
    val isFetchingObstacles: StateFlow<Boolean> = _isFetchingObstacles.asStateFlow()

    // 5-tap corner counter logic
    private val _cornerTapCount = MutableStateFlow(0)
    val cornerTapCount: StateFlow<Int> = _cornerTapCount.asStateFlow()

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog.asStateFlow()

    // Requirement: Hide periodical service countdown inside daily kilometer
    private val _isServiceCountdownRevealed = MutableStateFlow(false)
    val isServiceCountdownRevealed: StateFlow<Boolean> = _isServiceCountdownRevealed.asStateFlow()

    // Power wake denied hint
    private val _showWakeDeniedHint = MutableStateFlow(false)
    val showWakeDeniedHint: StateFlow<Boolean> = _showWakeDeniedHint.asStateFlow()

    private var tapResetJob: Job? = null

    init {
        powerManager.startListening()

        // Start foreground tracking service so kilometers and sensors keep working when other apps are open
        KioskTrackingForegroundService.startService(application, sensorTracker.dailyRunKm)

        powerManager.onPowerWakeDeniedCallback = {
            _showWakeDeniedHint.value = true
            // If device admin active, force sleep device immediately
            KioskDeviceAdminReceiver.lockDevice(application)
        }

        viewModelScope.launch {
            // Keep Accessibility Service allowed packages in sync
            combine(allowedApps, settings) { apps, s ->
                val set = apps.filter { it.isAllowed }.map { it.packageName }.toSet()
                KioskAccessibilityService.updateAllowedPackages(set, s.kioskLockEnabled)
            }.collect {}
        }

        viewModelScope.launch {
            // Load settings
            val currentSettings = settingsDao.getSettingsSnapshot() ?: KioskSettingsEntity().also {
                settingsDao.saveSettings(it)
            }

            // Sync from database if database values are higher
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            sensorTracker.odometerPersistence.syncFromDatabase(
                daily = currentSettings.dailyRunKm,
                total = currentSettings.totalOdometerKm,
                remaining = currentSettings.serviceRemainingKm,
                interval = currentSettings.serviceIntervalKm,
                dateStr = currentSettings.lastDailyDate
            )

            // Setup callback to persist odometer updates to Room
            sensorTracker.onOdometerUpdated = { daily, total, serviceRem ->
                viewModelScope.launch {
                    val curr = settingsDao.getSettingsSnapshot() ?: KioskSettingsEntity()
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    settingsDao.saveSettings(
                        curr.copy(
                            dailyRunKm = daily,
                            totalOdometerKm = total,
                            serviceRemainingKm = serviceRem,
                            lastDailyDate = today
                        )
                    )
                }
            }

            applySettingsToServices(currentSettings)

            // Sync installed apps to database if empty
            installedAppsManager.syncDefaultAllowedAppsIfNeeded()
            loadInstalledAppsList()

            // Start sensor tracking
            sensorTracker.startListening()
            locationTracker.startRealLocationUpdates()

            // Fetch initial obstacles around location (strictly relevant API query)
            delay(1200)
            val initialLat = locationTracker.currentLat
            val initialLon = locationTracker.currentLon
            if (initialLat != 0.0 && initialLon != 0.0) {
                fetchRoadObstacles(initialLat, initialLon)
            }
        }
    }

    private fun applySettingsToServices(s: KioskSettingsEntity) {
        sensorTracker.bumpSensitivityThreshold = s.bumpSensitivity
        sensorTracker.soundEnabled = s.soundEnabled
        sensorTracker.fakeBumpStrikeLimit = s.fakeBumpThreshold
        powerManager.batteryModeEnabled = s.batteryModeEnabled
        powerManager.disconnectDelaySec = s.disconnectDelaySec
        powerManager.denyPowerButtonWakeup = s.denyPowerButtonWakeup
        powerManager.power3TimesWakeupEnabled = s.power3TimesWakeupEnabled
        CashNotificationListenerService.cashAnnouncementEnabled = s.cashAnnouncementEnabled
        CashNotificationListenerService.cashTtsEnabled = s.cashTtsEnabled
    }

    fun wakeFromStandby() {
        powerManager.onDoubleTapWakeup()
        _showWakeDeniedHint.value = false
    }

    fun enterStandby() {
        powerManager.enterStandby()
    }

    fun cancelDisconnectCountdown() {
        powerManager.cancelDisconnectCountdown()
    }

    /**
     * Requirement: 5 top right corner tap open setting
     */
    fun onTopRightCornerTapped() {
        val nextCount = _cornerTapCount.value + 1
        _cornerTapCount.value = nextCount

        tapResetJob?.cancel()

        if (nextCount >= 5) {
            _cornerTapCount.value = 0
            _showPinDialog.value = true
        } else {
            // Auto reset tap count if inactive for 3.5 seconds
            tapResetJob = viewModelScope.launch {
                delay(3500)
                _cornerTapCount.value = 0
            }
        }
    }

    fun dismissPinDialog() {
        _showPinDialog.value = false
        _cornerTapCount.value = 0
    }

    fun loadInstalledAppsList() {
        viewModelScope.launch {
            val entities = allowedApps.value
            _installedAppsList.value = installedAppsManager.getInstalledAppsList(entities)
        }
    }

    fun toggleAppAllowed(packageName: String, appName: String, isAllowed: Boolean) {
        viewModelScope.launch {
            allowedAppDao.insertApp(
                AllowedAppEntity(
                    packageName = packageName,
                    appName = appName,
                    isAllowed = isAllowed
                )
            )
            loadInstalledAppsList()
        }
    }

    fun launchApp(packageName: String): Boolean {
        return installedAppsManager.launchApp(packageName)
    }

    /**
     * Requirement: "Alow, i stored two app screen share in one icon, i touch it it open both, can alow this type shortcut icon to my app"
     * Launches the companion app in Multi-Window / Split-Screen Adjacent mode alongside Drive Safe!
     */
    fun launchSplitScreenAppPair(targetPackage: String) {
        val context = getApplication<Application>()
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(targetPackage)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            if (intent != null) {
                context.startActivity(intent)
                Toast.makeText(context, "Opening split-screen with $targetPackage", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "App not found: $targetPackage", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("KioskViewModel", "Failed to launch split-screen app pair: ${e.message}")
            Toast.makeText(context, "Unable to start split screen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Import custom icon for the two-app split-screen shortcut
     */
    fun importCustomAppPairIcon(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    // Clean up any old imported icons
                    context.filesDir.listFiles { _, name -> name.startsWith("custom_app_pair_icon") }?.forEach { it.delete() }

                    val iconFile = File(context.filesDir, "custom_app_pair_icon_${System.currentTimeMillis()}.png")
                    val fos = FileOutputStream(iconFile)
                    // Scale to a crisp 256x256 square icon
                    val scaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush()
                    fos.close()

                    val curr = settings.value
                    val updated = curr.copy(appPairIconPath = iconFile.absolutePath)
                    settingsDao.saveSettings(updated)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "App Pair icon imported successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not decode chosen image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("KioskViewModel", "Error importing icon: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to import icon: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun resetCustomAppPairIcon() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "custom_app_pair_icon.png")
                if (file.exists()) {
                    file.delete()
                }
                val curr = settings.value
                settingsDao.saveSettings(curr.copy(appPairIconPath = ""))
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Reset to default dual-app icon", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Pin App-Pair shortcut to Android Home Screen / Launcher
     */
    fun pinAppPairShortcut(targetPackage: String, appName: String) {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("LAUNCH_APP_PAIR", targetPackage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                // Determine icon: Use imported custom icon if available
                val customIconPath = settings.value.appPairIconPath
                val pinIcon: Icon = if (customIconPath.isNotEmpty()) {
                    try {
                        val file = File(customIconPath)
                        if (file.exists()) {
                            val bmp = BitmapFactory.decodeFile(file.absolutePath)
                            Icon.createWithBitmap(bmp)
                        } else {
                            Icon.createWithResource(context, R.drawable.img_split_app_pair)
                        }
                    } catch (_: Exception) {
                        Icon.createWithResource(context, R.drawable.img_split_app_pair)
                    }
                } else {
                    Icon.createWithResource(context, R.drawable.img_split_app_pair)
                }

                val pinShortcutInfo = ShortcutInfo.Builder(context, "app_pair_${targetPackage}")
                    .setIcon(pinIcon)
                    .setShortLabel("DriveSafe + $appName")
                    .setLongLabel("Drive Safe & $appName (Dual Screen)")
                    .setIntent(launchIntent)
                    .build()

                val pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(pinShortcutInfo)
                val successCallback = PendingIntent.getBroadcast(
                    context,
                    0,
                    pinnedShortcutCallbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )

                shortcutManager.requestPinShortcut(pinShortcutInfo, successCallback.intentSender)
                Toast.makeText(context, "Shortcut requested for Home Screen!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Pinning shortcuts not supported by current launcher", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun changePin(newPin: String) {
        viewModelScope.launch {
            val current = settings.value
            val updated = current.copy(pinCode = newPin)
            settingsDao.saveSettings(updated)
        }
    }

    fun updateWallpaper(themeId: String, customColor: Long = 0xFF0B132BL) {
        viewModelScope.launch {
            val current = settings.value
            val updated = current.copy(
                wallpaperTheme = themeId,
                customWallpaperColor = customColor
            )
            settingsDao.saveSettings(updated)
        }
    }

    fun updateSettings(updated: KioskSettingsEntity) {
        viewModelScope.launch {
            settingsDao.saveSettings(updated)
            applySettingsToServices(updated)
        }
    }

    fun fetchRoadObstacles(lat: Double, lon: Double) {
        viewModelScope.launch {
            _isFetchingObstacles.value = true
            try {
                val fetched = OverpassService.fetchNearbyObstacles(lat, lon, 2000)
                if (fetched.isNotEmpty()) {
                    obstacleDao.insertOrUpdateObstacles(fetched)
                }
            } catch (_: Exception) {} finally {
                _isFetchingObstacles.value = false
            }
        }
    }

    fun addManualObstacle(lat: Double, lon: Double, type: String, title: String) {
        viewModelScope.launch {
            val obs = RoadObstacleEntity(
                id = "manual_${System.currentTimeMillis()}",
                type = type,
                latitude = lat,
                longitude = lon,
                heading = telemetry.value.compassDegrees,
                title = title.ifEmpty { if (type == "SPEED_BUMP") "Speed Bump" else "Traffic Signal" },
                isAutoDetected = false
            )
            obstacleDao.insertObstacle(obs)
        }
    }

    fun deleteObstacle(id: String) {
        viewModelScope.launch {
            obstacleDao.deleteObstacle(id)
        }
    }

    fun resetFakeBumpStrikes(id: String) {
        viewModelScope.launch {
            obstacleDao.updateStrike(id, strikes = 0, suppressed = false)
        }
    }

    // Daily Run Kilometer & Periodical Service Countdown methods
    fun toggleServiceCountdownRevealed() {
        _isServiceCountdownRevealed.update { !it }
    }

    fun setServiceCountdownRevealed(revealed: Boolean) {
        _isServiceCountdownRevealed.value = revealed
    }

    fun resetDailyKm() {
        sensorTracker.resetDailyKm()
        viewModelScope.launch {
            val curr = settingsDao.getSettingsSnapshot() ?: KioskSettingsEntity()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            settingsDao.saveSettings(curr.copy(dailyRunKm = 0.0, lastDailyDate = today))
        }
    }

    fun resetServiceCountdown() {
        sensorTracker.resetServiceCountdown()
        audioAlertManager.playNewBumpDetectedChime()
        viewModelScope.launch {
            val curr = settingsDao.getSettingsSnapshot() ?: KioskSettingsEntity()
            settingsDao.saveSettings(curr.copy(serviceRemainingKm = curr.serviceIntervalKm))
        }
    }

    fun updateServiceInterval(intervalKm: Double) {
        sensorTracker.updateServiceInterval(intervalKm)
        viewModelScope.launch {
            val curr = settingsDao.getSettingsSnapshot() ?: KioskSettingsEntity()
            val remaining = if (curr.serviceRemainingKm > intervalKm) intervalKm else curr.serviceRemainingKm
            settingsDao.saveSettings(curr.copy(serviceIntervalKm = intervalKm, serviceRemainingKm = remaining))
        }
    }

    fun addManualTestKm(km: Double) {
        sensorTracker.accumulateTravelDistance(km)
    }

    fun updateCustomOdometerValues(dailyKm: Double, totalKm: Double, remainingServiceKm: Double) {
        sensorTracker.setOdometerState(
            daily = dailyKm,
            total = totalKm,
            remaining = remainingServiceKm,
            interval = settings.value.serviceIntervalKm
        )
        viewModelScope.launch {
            val curr = settingsDao.getSettingsSnapshot() ?: KioskSettingsEntity()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            settingsDao.saveSettings(
                curr.copy(
                    dailyRunKm = dailyKm,
                    totalOdometerKm = totalKm,
                    serviceRemainingKm = remainingServiceKm,
                    lastDailyDate = today
                )
            )
        }
    }

    // Cash announcement simulation and dismiss
    fun simulateCashNotification(amount: String = "500", sender: String = "Rahul") {
        audioAlertManager.playCashAlertChime()
        CashNotificationListenerService.triggerManualSimulation(amount, sender)
    }

    fun dismissCashAlert() {
        CashNotificationListenerService.clearCurrentAlert()
    }

    // Audio test triggers
    fun testBumpBeep() {
        audioAlertManager.playSpeedBumpAlert("test_bump_${System.currentTimeMillis()}", true)
    }

    fun testSignalBeep() {
        audioAlertManager.playTrafficSignalAlert("test_signal_${System.currentTimeMillis()}", true)
    }

    fun testGutterBeep() {
        audioAlertManager.playGutterAlert("test_gutter_${System.currentTimeMillis()}", true)
    }

    // Sensor test trigger
    fun testSimulateVerticalBumpSpike() {
        sensorTracker.onVerticalBumpSpikeDetected(18.0f)
    }

    // Driving simulator toggle
    fun toggleSimulation() {
        if (locationTracker.isSimulating()) {
            locationTracker.stopSimulation()
        } else {
            locationTracker.startSimulation(45f)
        }
    }

    override fun onCleared() {
        super.onCleared()
        powerManager.stopListening()
        sensorTracker.stopListening()
        locationTracker.stopLocationUpdates()
        audioAlertManager.release()
    }
}
