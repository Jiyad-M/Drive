package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
    private val TAG = "PowerManager"
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isChargerConnected = MutableStateFlow(false)
    val isChargerConnected: StateFlow<Boolean> = _isChargerConnected.asStateFlow()

    private val _batteryPercent = MutableStateFlow(100)
    val batteryPercent: StateFlow<Int> = _batteryPercent.asStateFlow()

    private val _disconnectCountdown = MutableStateFlow<Int?>(null)
    val disconnectCountdown: StateFlow<Int?> = _disconnectCountdown.asStateFlow()

    private val _isStandbyActive = MutableStateFlow(false)
    val isStandbyActive: StateFlow<Boolean> = _isStandbyActive.asStateFlow()

    private var disconnectCountdownJob: Job? = null
    private var isRegistered = false

    // Rolling timestamps for 3-power-press detection to wake display
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
                    Log.d(TAG, "Charger connected -> Turning screen ON")
                    _isChargerConnected.value = true
                    cancelDisconnectCountdown()
                    exitStandby()
                    audioAlertManager.playPowerChime(connected = true)
                    onScreenWakeRequested?.invoke()
                }

                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.d(TAG, "Charger disconnected -> Starting power-down delay")
                    _isChargerConnected.value = false
                    audioAlertManager.playPowerChime(connected = false)

                    if (batteryModeEnabled) {
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
    }

    /**
     * Requirement: Press power 3 times when on battery to turn display ON
     */
    private fun recordPowerButtonPress(timestamp: Long) {
        if (!power3TimesWakeupEnabled) return

        powerPressTimestamps.add(timestamp)
        val cutoff = timestamp - 3000L
        powerPressTimestamps.removeAll { it < cutoff }

        if (powerPressTimestamps.size >= 3) {
            Log.i(TAG, "3 Power button presses detected: Turning display ON!")
            powerPressTimestamps.clear()
            exitStandby()
            audioAlertManager.playWakeChime()
            onScreenWakeRequested?.invoke()
        }
    }

    /**
     * Requirement: Deny normal single power button wake up when disconnected on battery.
     */
    private fun handleScreenOnEvent() {
        if (_isStandbyActive.value && !_isChargerConnected.value && denyPowerButtonWakeup) {
            if (powerPressTimestamps.size < 3) {
                Log.d(TAG, "Single power button press denied on battery: Re-locking display")
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
        // Turn screen completely off (hardware lock & backlight off)
        onScreenSleepRequested?.invoke()
    }

    fun onDoubleTapWakeup() {
        Log.d(TAG, "Waking display via double tap gesture")
        exitStandby()
        audioAlertManager.playWakeChime()
        onScreenWakeRequested?.invoke()
    }

    fun exitStandby() {
        _isStandbyActive.value = false
        cancelDisconnectCountdown()
    }
}
