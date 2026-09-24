package com.example.service

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class KioskDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled for Drive Safe Kiosk")
        Toast.makeText(context, "Drive Safe Kiosk Admin Activated", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled for Drive Safe Kiosk")
        Toast.makeText(context, "Drive Safe Kiosk Admin Deactivated", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "KioskDeviceAdmin"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, KioskDeviceAdminReceiver::class.java)
        }

        fun isDeviceAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val component = getComponentName(context)
            return dpm?.isAdminActive(component) == true
        }

        fun requestDeviceAdmin(activity: Activity, requestCode: Int = 101) {
            val component = getComponentName(activity)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Drive Safe requires Device Administrator to lock screen instantly on charger disconnect and enforce kiosk mode."
                )
            }
            activity.startActivityForResult(intent, requestCode)
        }

        fun lockDevice(context: Context): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                val component = getComponentName(context)
                if (dpm != null && dpm.isAdminActive(component)) {
                    dpm.lockNow()
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to lock device: ${e.message}")
                false
            }
        }
    }
}
