package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class KioskAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Kiosk Accessibility Service Connected")
        instance = this
        _isServiceRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!isKioskLockActive) return

        val targetPackage = event.packageName?.toString() ?: return

        // Exclude ourselves and system essential packages
        if (targetPackage == packageName) return
        if (isSystemEssential(targetPackage)) return

        // Check if package is allowed
        if (!allowedPackagesSet.contains(targetPackage)) {
            Log.w(TAG, "Blocked unwanted application window: $targetPackage")
            _lastBlockedPackage.value = targetPackage

            // Intercept and bring user back to Drive Safe Kiosk Launcher
            performGlobalAction(GLOBAL_ACTION_HOME)

            val bringHomeIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("BLOCKED_PACKAGE", targetPackage)
            }
            startActivity(bringHomeIntent)

            Toast.makeText(
                this,
                "Access restricted: $targetPackage is not an allowed app in Kiosk mode",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Kiosk Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceRunning.value = false
    }

    private fun isSystemEssential(pkg: String): Boolean {
        return pkg.startsWith("com.android.systemui") ||
                pkg.contains("dialer") ||
                pkg.contains("telecom") ||
                pkg.contains("incallui") ||
                pkg == "android"
    }

    companion object {
        private const val TAG = "KioskAccessibility"
        private var instance: KioskAccessibilityService? = null

        var isKioskLockActive: Boolean = true
        var allowedPackagesSet: Set<String> = emptySet()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _lastBlockedPackage = MutableStateFlow<String?>(null)
        val lastBlockedPackage = _lastBlockedPackage.asStateFlow()

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${KioskAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices.contains(expectedServiceName) || enabledServices.contains(KioskAccessibilityService::class.java.simpleName)
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun updateAllowedPackages(packages: Set<String>, lockActive: Boolean) {
            allowedPackagesSet = packages
            isKioskLockActive = lockActive
        }

        fun lockDeviceScreen(): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
            }
            return false
        }
    }
}
