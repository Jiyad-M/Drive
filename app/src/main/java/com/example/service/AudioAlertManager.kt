package com.example.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AudioAlertManager(private val context: Context) {
    private val TAG = "AudioAlertManager"
    private var toneGenerator: ToneGenerator? = null
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val alertCooldownMap = mutableMapOf<String, Long>()

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator: ${e.message}")
        }
    }

    /**
     * Speed bump arrival: 3 short beeps
     */
    fun playSpeedBumpAlert(obstacleId: String, soundEnabled: Boolean = true) {
        val now = System.currentTimeMillis()
        val lastAlert = alertCooldownMap[obstacleId] ?: 0L
        if (now - lastAlert < 15_000L) {
            return // Prevent spamming within 15 seconds for the same obstacle
        }
        alertCooldownMap[obstacleId] = now

        triggerVibrationBump()

        if (!soundEnabled) return

        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Beep 1
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                delay(180)
                // Beep 2
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                delay(180)
                // Beep 3
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing bump 3-beep tone: ${e.message}")
            }
        }
    }

    /**
     * Traffic signal arrival: 1 long continuous beep
     */
    fun playTrafficSignalAlert(obstacleId: String, soundEnabled: Boolean = true) {
        val now = System.currentTimeMillis()
        val lastAlert = alertCooldownMap[obstacleId] ?: 0L
        if (now - lastAlert < 15_000L) {
            return // Prevent spamming
        }
        alertCooldownMap[obstacleId] = now

        triggerVibrationSignal()

        if (!soundEnabled) return

        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Long beep (700ms)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 700)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing signal long beep: ${e.message}")
            }
        }
    }

    fun playNewBumpDetectedChime() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing chime: ${e.message}")
            }
        }
    }

    private fun triggerVibrationBump() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 100, 80, 100, 80, 150)
                val amplitudes = intArrayOf(0, 200, 0, 200, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 100, 80, 100, 80, 150), -1)
            }
        } catch (_: Exception) {}
    }

    private fun triggerVibrationSignal() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}
