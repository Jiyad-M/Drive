package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OdometerPersistenceManager(context: Context) {

    private val TAG = "OdometerPersistenceMgr"
    private val prefs: SharedPreferences = context.getSharedPreferences("kiosk_odometer_prefs", Context.MODE_PRIVATE)

    var dailyRunKm: Double
        private set

    var totalOdometerKm: Double
        private set

    var serviceRemainingKm: Double
        private set

    var serviceIntervalKm: Double
        private set

    init {
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_LAST_DATE, "") ?: ""
        val savedDaily = prefs.getFloat(KEY_DAILY_KM, 0.0f).toDouble()
        val savedTotal = prefs.getFloat(KEY_TOTAL_KM, 12450.0f).toDouble()
        val savedInterval = prefs.getFloat(KEY_SERVICE_INTERVAL, 5000.0f).toDouble()
        val savedRemaining = prefs.getFloat(KEY_SERVICE_REMAINING, 4850.0f).toDouble()

        totalOdometerKm = savedTotal
        serviceIntervalKm = savedInterval
        serviceRemainingKm = savedRemaining

        // Check if calendar date really changed (e.g. yesterday vs today)
        if (savedDate.isNotEmpty() && savedDate != today) {
            Log.i(TAG, "New day detected ($savedDate -> $today). Rolled over daily km.")
            dailyRunKm = 0.0
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putFloat(KEY_DAILY_KM, 0.0f)
                .apply()
        } else {
            // Same day OR fresh install: Preserve accumulated km!
            dailyRunKm = savedDaily
            if (savedDate.isEmpty()) {
                prefs.edit().putString(KEY_LAST_DATE, today).apply()
            }
            Log.i(TAG, "Restored odometer state: daily=$dailyRunKm, total=$totalOdometerKm, serviceRem=$serviceRemainingKm")
        }
    }

    @Synchronized
    fun addDistance(deltaKm: Double) {
        if (deltaKm <= 0.0) return

        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_LAST_DATE, "") ?: ""
        if (savedDate.isNotEmpty() && savedDate != today) {
            dailyRunKm = 0.0
        }

        dailyRunKm += deltaKm
        totalOdometerKm += deltaKm
        serviceRemainingKm = (serviceRemainingKm - deltaKm).coerceAtLeast(0.0)

        // Immediately commit to SharedPreferences so leaving the app never loses progress
        prefs.edit()
            .putFloat(KEY_DAILY_KM, dailyRunKm.toFloat())
            .putFloat(KEY_TOTAL_KM, totalOdometerKm.toFloat())
            .putFloat(KEY_SERVICE_REMAINING, serviceRemainingKm.toFloat())
            .putString(KEY_LAST_DATE, today)
            .apply()
    }

    @Synchronized
    fun resetDaily() {
        dailyRunKm = 0.0
        prefs.edit()
            .putFloat(KEY_DAILY_KM, 0.0f)
            .putString(KEY_LAST_DATE, getTodayDateString())
            .apply()
    }

    @Synchronized
    fun resetServiceCountdown() {
        serviceRemainingKm = serviceIntervalKm
        prefs.edit()
            .putFloat(KEY_SERVICE_REMAINING, serviceRemainingKm.toFloat())
            .apply()
    }

    @Synchronized
    fun setServiceInterval(interval: Double) {
        serviceIntervalKm = interval
        if (serviceRemainingKm > interval) {
            serviceRemainingKm = interval
        }
        prefs.edit()
            .putFloat(KEY_SERVICE_INTERVAL, serviceIntervalKm.toFloat())
            .putFloat(KEY_SERVICE_REMAINING, serviceRemainingKm.toFloat())
            .apply()
    }

    @Synchronized
    fun syncFromDatabase(daily: Double, total: Double, remaining: Double, interval: Double, dateStr: String) {
        val today = getTodayDateString()
        if (total > totalOdometerKm) {
            totalOdometerKm = total
        }
        if (interval > 0) {
            serviceIntervalKm = interval
        }
        if (remaining > 0 && remaining <= serviceIntervalKm) {
            serviceRemainingKm = remaining
        }
        if (dateStr == today && daily > dailyRunKm) {
            dailyRunKm = daily
        }

        prefs.edit()
            .putFloat(KEY_DAILY_KM, dailyRunKm.toFloat())
            .putFloat(KEY_TOTAL_KM, totalOdometerKm.toFloat())
            .putFloat(KEY_SERVICE_REMAINING, serviceRemainingKm.toFloat())
            .putFloat(KEY_SERVICE_INTERVAL, serviceIntervalKm.toFloat())
            .putString(KEY_LAST_DATE, today)
            .apply()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    companion object {
        private const val KEY_DAILY_KM = "daily_km"
        private const val KEY_TOTAL_KM = "total_km"
        private const val KEY_SERVICE_REMAINING = "service_remaining"
        private const val KEY_SERVICE_INTERVAL = "service_interval"
        private const val KEY_LAST_DATE = "last_date"
    }
}
