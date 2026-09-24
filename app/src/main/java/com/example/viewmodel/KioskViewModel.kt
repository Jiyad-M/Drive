package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.OverpassService
import com.example.data.db.AppDatabase
import com.example.data.model.AllowedAppEntity
import com.example.data.model.KioskSettingsEntity
import com.example.data.model.RoadObstacleEntity
import com.example.data.model.TelemetryData
import com.example.service.AudioAlertManager
import com.example.service.InstalledAppItem
import com.example.service.InstalledAppsManager
import com.example.service.LocationTracker
import com.example.service.SensorAndRoadTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

    val telemetry: StateFlow<TelemetryData> = sensorTracker.telemetry

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

    private var tapResetJob: Job? = null

    init {
        viewModelScope.launch {
            // Ensure default settings exist and apply odometer state
            val currentSettings = settingsDao.getSettingsSnapshot() ?: KioskSettingsEntity().also {
                settingsDao.saveSettings(it)
            }

            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            // Check if day changed: rollover dailyRunKm
            val effectiveDailyKm = if (currentSettings.lastDailyDate != todayDate) {
                0.0
            } else {
                currentSettings.dailyRunKm
            }

            sensorTracker.setOdometerState(
                daily = effectiveDailyKm,
                total = currentSettings.totalOdometerKm,
                remaining = currentSettings.serviceRemainingKm,
                interval = currentSettings.serviceIntervalKm
            )

            // Setup callback to persist odometer updates
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
            val initialAllowed = allowedAppDao.getAllApps()
            installedAppsManager.syncDefaultAllowedAppsIfNeeded()
            loadInstalledAppsList()

            // Start sensor tracking
            sensorTracker.startListening()
            locationTracker.startRealLocationUpdates()

            // Fetch or seed initial obstacles around location
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
                val fetched = OverpassService.fetchNearbyObstacles(lat, lon, 3000)
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

    // Audio test triggers
    fun testBumpBeep() {
        audioAlertManager.playSpeedBumpAlert("test_bump_${System.currentTimeMillis()}", true)
    }

    fun testSignalBeep() {
        audioAlertManager.playTrafficSignalAlert("test_signal_${System.currentTimeMillis()}", true)
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
        sensorTracker.stopListening()
        locationTracker.stopLocationUpdates()
        audioAlertManager.release()
    }
}
