package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import com.example.data.db.AllowedAppDao
import com.example.data.model.AllowedAppEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable?,
    val isAllowed: Boolean,
    val isAppPair: Boolean = false,
    val secondaryPackage: String = ""
)

data class PhoneShortcutItem(
    val id: String,
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
    val intentUri: String = "",
    val isAllowed: Boolean = false,
    val isMultitaskingShortcut: Boolean = false
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
        val commonKeywords = listOf("map", "nav", "uber", "driver", "phone", "dialer", "message", "music", "radio", "chrome", "browser", "clock", "weather", "camera")

        var index = 0
        var foundUberPkg: String? = null
        var foundMapsPkg: String? = null

        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue

            val label = info.loadLabel(packageManager).toString().lowercase()
            val isRecommended = commonKeywords.any { label.contains(it) || pkg.contains(it) } || index < 4

            if (pkg.contains("uber")) {
                foundUberPkg = pkg
            }
            if (pkg.contains("maps") || pkg.contains("google.android.apps.maps")) {
                foundMapsPkg = pkg
            }

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

        // Auto-create the Uber + Maps App Pair preset if both or maps is available
        val uber = foundUberPkg ?: "com.ubercab.driver"
        val maps = foundMapsPkg ?: "com.google.android.apps.maps"
        val pairKey = "pair:$uber+$maps"

        val allExisting = allowedAppDao.getAllAppsList()
        val existingPair = allExisting.firstOrNull { it.packageName == pairKey }
        if (existingPair == null) {
            entities.add(
                AllowedAppEntity(
                    packageName = pairKey,
                    activityName = "",
                    appName = "Uber + Maps (Split Screen)",
                    isAllowed = true,
                    orderIndex = 0,
                    isAppPair = true,
                    secondaryPackageName = maps,
                    secondaryAppName = "Google Maps"
                )
            )
        }

        // Requirement: "multitasking screen of my tab. It is open when i tap single icon u can see bottom 5 th one from left"
        // Preset the Multitasking Screen shortcut at 5th position (orderIndex = 4)
        val multitaskingKey = "action:multitasking"
        val existingMultitasking = allExisting.firstOrNull { it.packageName == multitaskingKey }
        if (existingMultitasking == null) {
            entities.add(
                AllowedAppEntity(
                    packageName = multitaskingKey,
                    activityName = "",
                    appName = "Multitasking Screen",
                    isAllowed = true,
                    orderIndex = 4, // 5th item! (0, 1, 2, 3, 4)
                    isAppPair = false
                )
            )
        }

        if (entities.isNotEmpty()) {
            allowedAppDao.insertOrUpdateApps(entities)
        }
    }

    /**
     * Launch standard app, phone shortcut, or multitasking App Pair
     */
    fun launchTarget(app: AllowedAppEntity): Boolean {
        return if (app.packageName == "action:multitasking" || app.packageName == "action:recents") {
            // Requirement: Single tap icon opens tablet multitasking screen
            val opened = KioskAccessibilityService.openRecentsMultitasking()
            if (!opened) {
                val openedSplit = KioskAccessibilityService.openSplitScreen()
                if (!openedSplit) {
                    Toast.makeText(
                        context,
                        "Enable 'Drive Safe Kiosk Admin' in Accessibility Settings to open Tablet Multitasking screen",
                        Toast.LENGTH_LONG
                    ).show()
                    KioskAccessibilityService.openAccessibilitySettings(context)
                }
                openedSplit
            } else {
                true
            }
        } else if (app.packageName == "action:split_screen") {
            KioskAccessibilityService.openSplitScreen()
        } else if (app.isAppPair) {
            val primaryPkg = if (app.packageName.startsWith("pair:")) {
                app.packageName.removePrefix("pair:").split("+").firstOrNull() ?: app.packageName
            } else {
                app.packageName
            }
            launchAppPair(primaryPkg, app.secondaryPackageName)
        } else if (app.shortcutIntentUri.isNotBlank()) {
            launchShortcutUri(app.shortcutIntentUri, app.packageName, app.shortcutId)
        } else {
            launchApp(app.packageName)
        }
    }

    /**
     * Launch two apps in split-screen / multi-window adjacent tiling (Samsung One UI App Pair style)
     */
    fun launchAppPair(primaryPkg: String, secondaryPkg: String): Boolean {
        return try {
            val intent1 = packageManager.getLaunchIntentForPackage(primaryPkg)
            if (intent1 != null) {
                intent1.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
                context.startActivity(intent1)
            }

            if (secondaryPkg.isNotBlank()) {
                val intent2 = packageManager.getLaunchIntentForPackage(secondaryPkg)
                if (intent2 != null) {
                    intent2.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    )
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(220)
                        try {
                            context.startActivity(intent2)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed secondary launch adjacent: ${e.message}")
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app pair: ${e.message}")
            false
        }
    }

    private fun launchShortcutUri(uriString: String, packageName: String, shortcutId: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && shortcutId.isNotBlank()) {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                if (launcherApps?.hasShortcutHostPermission() == true) {
                    launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
                    return true
                }
            }
            val intent = Intent.parseUri(uriString, 0).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch shortcut $uriString: ${e.message}")
            launchApp(packageName)
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

    /**
     * Query existing Pinned & Dynamic shortcuts from the phone
     */
    fun getPhoneShortcuts(allowedEntities: List<AllowedAppEntity> = emptyList()): List<PhoneShortcutItem> {
        val allowedMap = allowedEntities.associateBy { it.packageName }
        val list = mutableListOf<PhoneShortcutItem>()

        // 1. Always provide the Tablet Multitasking Screen Shortcut (the 5th icon from left on user's tab)
        val multitaskingAllowed = allowedMap["action:multitasking"]?.isAllowed ?: true
        list.add(
            PhoneShortcutItem(
                id = "action:multitasking",
                packageName = "action:multitasking",
                label = "Tablet Multitasking Screen (Tab 5th Icon)",
                icon = null,
                intentUri = "",
                isAllowed = multitaskingAllowed,
                isMultitaskingShortcut = true
            )
        )

        // 2. Query Pinned & Dynamic shortcuts from Android LauncherApps API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
                    val query = LauncherApps.ShortcutQuery().apply {
                        setQueryFlags(
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                        )
                    }
                    val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
                    val profiles = userManager?.userProfiles ?: listOf(Process.myUserHandle())
                    for (profile in profiles) {
                        val shortcuts = launcherApps.getShortcuts(query, profile) ?: continue
                        for (s in shortcuts) {
                            val label = s.shortLabel?.toString() ?: s.longLabel?.toString() ?: s.id
                            val icon = try {
                                launcherApps.getShortcutIconDrawable(s, context.resources.displayMetrics.densityDpi)
                            } catch (_: Exception) {
                                null
                            }
                            val intentUri = s.intent?.toUri(0) ?: ""
                            val isAllowed = allowedMap[s.`package`]?.isAllowed ?: allowedMap[s.id]?.isAllowed ?: false
                            list.add(
                                PhoneShortcutItem(
                                    id = s.id,
                                    packageName = s.`package`,
                                    label = label,
                                    icon = icon,
                                    intentUri = intentUri,
                                    isAllowed = isAllowed,
                                    isMultitaskingShortcut = false
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not query shortcuts via LauncherApps: ${e.message}")
            }
        }
        return list
    }

    fun getAppIcon(packageName: String): Drawable? {
        val cleanPkg = if (packageName.startsWith("pair:")) {
            packageName.removePrefix("pair:").split("+").firstOrNull() ?: packageName
        } else {
            packageName
        }
        return try {
            packageManager.getApplicationIcon(cleanPkg)
        } catch (_: Exception) {
            null
        }
    }
}
