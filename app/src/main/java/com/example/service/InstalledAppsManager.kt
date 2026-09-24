package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import com.example.data.db.AllowedAppDao
import com.example.data.model.AllowedAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable?,
    val isAllowed: Boolean
)

class InstalledAppsManager(
    private val context: Context,
    private val allowedAppDao: AllowedAppDao
) {
    private val TAG = "InstalledAppsManager"
    private val packageManager: PackageManager = context.packageManager

    suspend fun getInstalledAppsList(allowedEntities: List<AllowedAppEntity>): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val allowedMap = allowedEntities.associateBy { it.packageName }
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
        val list = mutableListOf<InstalledAppItem>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            // Don't include ourselves as a launchable app inside our own kiosk front
            if (pkg == context.packageName) continue

            val label = resolveInfo.loadLabel(packageManager).toString()
            val icon = try {
                resolveInfo.loadIcon(packageManager)
            } catch (e: Exception) {
                null
            }
            val activityName = resolveInfo.activityInfo.name
            val isAllowed = allowedMap[pkg]?.isAllowed ?: false

            list.add(
                InstalledAppItem(
                    packageName = pkg,
                    activityName = activityName,
                    label = label,
                    icon = icon,
                    isAllowed = isAllowed
                )
            )
        }

        list.sortedBy { it.label.lowercase() }
    }

    suspend fun syncDefaultAllowedAppsIfNeeded() = withContext(Dispatchers.IO) {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
        val entities = mutableListOf<AllowedAppEntity>()

        // Default set of packages to allow if found
        val commonKeywords = listOf("map", "nav", "phone", "dialer", "message", "music", "radio", "chrome", "browser", "clock", "weather", "camera")

        var index = 0
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue

            val label = info.loadLabel(packageManager).toString().lowercase()
            val isRecommended = commonKeywords.any { label.contains(it) || pkg.contains(it) } || index < 4

            entities.add(
                AllowedAppEntity(
                    packageName = pkg,
                    activityName = info.activityInfo.name,
                    appName = info.loadLabel(packageManager).toString(),
                    isAllowed = isRecommended,
                    orderIndex = index++
                )
            )
        }

        if (entities.isNotEmpty()) {
            allowedAppDao.insertOrUpdateApps(entities)
        }
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                Log.w(TAG, "No launch intent found for $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app $packageName: ${e.message}")
            false
        }
    }

    fun getAppIcon(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }
}
