package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PowerAndEmergencyManager(
    private val context: Context,
    private val audioAlertManager: AudioAlertManager
) {
    private val TAG = "PowerAndEmergencyMgr"
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isChargerConnected = MutableStateFlow(false)
    val isChargerConnected: StateFlow<Boolean> = _isChargerConnected.asStateFlow()

    private val _batteryPercent = MutableStateFlow(100)
    val batteryPercent: StateFlow<Int> = _batteryPercent.asStateFlow()

    private val _disconnectCountdown = MutableStateFlow<Int?>(null)
    val disconnectCountdown: StateFlow<Int?> = _disconnectCountdown.asStateFlow()

    private val _isStandbyActive = MutableStateFlow(false)
    val isStandbyActive: StateFlow<Boolean> = _isStandbyActive.asStateFlow()

    private val _isEmergencyActive = MutableStateFlow(false)
    val isEmergencyActive: StateFlow<Boolean> = _isEmergencyActive.asStateFlow()

    private val _isTorchActive = MutableStateFlow(false)
    val isTorchActive: StateFlow<Boolean> = _isTorchActive.asStateFlow()

    private var disconnectCountdownJob: Job? = null
    private var strobeJob: Job? = null
    private var isRegistered = false

    // Rolling timestamps for 3-power-press detection
    private val powerPressTimestamps = mutableListOf<Long>()

    // Config options
    var batteryModeEnabled: Boolean = true
    var disconnectDelaySec: Int = 5
    var denyPowerButtonWakeup: Boolean = true
    var power3TimesWakeupEnabled: Boolean = true

    // Callbacks to Activity
    var onScreenWakeRequested: (() -> Unit)? = null
    var onScreenSleepRequested: (() -> Unit)? = null
    var onPowerWakeDeniedCallback: (() -> Unit)? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val now = System.currentTimeMillis()

            when (action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.d(TAG, "Charger connected")
                    _isChargerConnected.value = true
                    cancelDisconnectCountdown()
                    exitStandby()
                    audioAlertManager.playPowerChime(connected = true)
                    onScreenWakeRequested?.invoke()
                }

                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.d(TAG, "Charger disconnected")
                    _isChargerConnected.value = false
                    audioAlertManager.playPowerChime(connected = false)

                    if (batteryModeEnabled && !_isEmergencyActive.value) {
                        startDisconnectCountdown()
                    }
                }

                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        _batteryPercent.value = (level * 100) / scale
                    }
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    _isChargerConnected.value = isCharging
                }

                Intent.ACTION_SCREEN_ON -> {
                    recordPowerButtonPress(now)
                    handleScreenOnEvent()
                }

                Intent.ACTION_SCREEN_OFF -> {
                    recordPowerButtonPress(now)
                }
            }
        }
    }

    init {
        checkInitialBatteryState()
    }

    private fun checkInitialBatteryState() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            if (batteryStatus != null) {
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                _isChargerConnected.value = isCharging

                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    _batteryPercent.value = (level * 100) / scale
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery state: ${e.message}")
        }
    }

    fun startListening() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(powerReceiver, filter)
        isRegistered = true
    }

    fun stopListening() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(powerReceiver)
        } catch (_: Exception) {}
        isRegistered = false
        cancelDisconnectCountdown()
        stopSosStrobe()
    }

    /**
     * User requirement:
     * "Remove 3 power to emergecy. I said if not connected to chargeger but we need display press 3 time"
     * Single press power on battery = denied.
     * 3 presses power on battery = wakes the display!
     */
    private fun recordPowerButtonPress(timestamp: Long) {
        if (!power3TimesWakeupEnabled) return

        powerPressTimestamps.add(timestamp)
        // Keep only events in the last 3000ms
        val cutoff = timestamp - 3000L
        powerPressTimestamps.removeAll { it < cutoff }

        if (powerPressTimestamps.size >= 3) {
            Log.i(TAG, "3 Power button presses detected while on battery: Waking display!")
            powerPressTimestamps.clear()
            exitStandby()
            audioAlertManager.playWakeChime()
            onScreenWakeRequested?.invoke()
        }
    }

    /**
     * Requirement: Deny normal single power button wake up when disconnected on battery.
     * When charger is disconnected and device was in standby, normal 1-press wake up is denied.
     * Screen stays asleep unless user presses power 3 times, or double/triple taps the screen.
     */
    private fun handleScreenOnEvent() {
        if (_isEmergencyActive.value) return

        // If standby was active and charger is disconnected and power button wake is denied
        if (_isStandbyActive.value && !_isChargerConnected.value && denyPowerButtonWakeup) {
            if (powerPressTimestamps.size < 3) {
                Log.d(TAG, "Normal 1-press power button wake denied: re-engaging standby (requires 3 presses or screen tap to wake)")
                onPowerWakeDeniedCallback?.invoke()
            }
        }
    }

    fun startDisconnectCountdown() {
        cancelDisconnectCountdown()
        disconnectCountdownJob = scope.launch {
            for (sec in disconnectDelaySec downTo 1) {
                _disconnectCountdown.value = sec
                delay(1000)
            }
            _disconnectCountdown.value = 0
            delay(200)
            _disconnectCountdown.value = null
            enterStandby()
        }
    }

    fun cancelDisconnectCountdown() {
        disconnectCountdownJob?.cancel()
        disconnectCountdownJob = null
        _disconnectCountdown.value = null
    }

    fun enterStandby() {
        _isStandbyActive.value = true
        onScreenSleepRequested?.invoke()
    }

    /**
     * Requirement: Two-time screen tap wakeup (Double Tap)
     */
    fun onDoubleTapWakeup() {
        Log.d(TAG, "Waking up via double tap gesture")
        exitStandby()
        audioAlertManager.playWakeChime()
        onScreenWakeRequested?.invoke()
    }

    fun exitStandby() {
        _isStandbyActive.value = false
        cancelDisconnectCountdown()
    }

    /**
     * Requirement: Emergency SOS mode
     */
    fun triggerEmergencyMode() {
        _isEmergencyActive.value = true
        exitStandby()
        cancelDisconnectCountdown()
        audioAlertManager.startEmergencySiren()
        startSosStrobe()
        onScreenWakeRequested?.invoke()
    }

    fun dismissEmergencyMode() {
        _isEmergencyActive.value = false
        audioAlertManager.stopEmergencySiren()
        stopSosStrobe()
    }

    fun toggleTorchManual(enabled: Boolean) {
        stopSosStrobe()
        setTorchMode(enabled)
        _isTorchActive.value = enabled
    }

    fun startSosStrobe() {
        stopSosStrobe()
        strobeJob = scope.launch(Dispatchers.Default) {
            var state = false
            try {
                while (_isEmergencyActive.value) {
                    state = !state
                    setTorchMode(state)
                    _isTorchActive.value = state
                    delay(if (state) 200 else 200)
                }
            } catch (_: Exception) {
            } finally {
                setTorchMode(false)
                _isTorchActive.value = false
            }
        }
    }

    fun stopSosStrobe() {
        strobeJob?.cancel()
        strobeJob = null
        setTorchMode(false)
        _isTorchActive.value = false
    }

    private fun setTorchMode(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    flashAvailable && facing == CameraCharacteristics.LENS_FACING_BACK
                } ?: cameraManager.cameraIdList.firstOrNull() ?: return

                cameraManager.setTorchMode(cameraId, enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Camera torch error: ${e.message}")
            }
        }
    }
}
