package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class KioskTrackingForegroundService : Service() {

    private val TAG = "KioskTrackingService"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        Log.i(TAG, "Foreground tracking service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val dailyKm = intent?.getDoubleExtra(EXTRA_DAILY_KM, 0.0) ?: 0.0
        updateNotification(dailyKm)

        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification(0.0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(dailyKm: Double) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(dailyKm))
    }

    private fun buildNotification(dailyKm: Double): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val kmStr = String.format("%.1f", dailyKm)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Drive Safe Active — $kmStr km Today")
            .setContentText("Continuous road hazard alerts & distance tracking")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Drive Safe Continuous Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps distance tracking and speed bump detection active while using other apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "drive_safe_tracking_channel"
        private const val NOTIFICATION_ID = 4040
        const val ACTION_START = "com.example.action.START_TRACKING"
        const val ACTION_STOP = "com.example.action.STOP_TRACKING"
        const val EXTRA_DAILY_KM = "extra_daily_km"

        fun startService(context: Context, dailyKm: Double = 0.0) {
            try {
                val intent = Intent(context, KioskTrackingForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_DAILY_KM, dailyKm)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("KioskTrackingService", "Failed to start foreground service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, KioskTrackingForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
