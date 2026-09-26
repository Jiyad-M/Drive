package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.KioskAccessibilityService
import com.example.service.KioskDeviceAdminReceiver
import com.example.ui.screens.KioskHomeScreen
import com.example.ui.screens.KioskSettingsScreen
import com.example.ui.screens.StandbyScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.KioskViewModel

enum class KioskScreen {
    HOME,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private var isKioskLockArmed = false
    private var viewModelInstance: KioskViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                val kioskViewModel: KioskViewModel = viewModel()
                viewModelInstance = kioskViewModel

                var currentScreen by remember { mutableStateOf(KioskScreen.HOME) }
                val settings by kioskViewModel.settings.collectAsState()
                val telemetry by kioskViewModel.telemetry.collectAsState()
                val showWakeDeniedHint by kioskViewModel.showWakeDeniedHint.collectAsState()

                // Check intent for App Pair dual launch
                LaunchedEffect(intent) {
                    val pairPkg = intent?.getStringExtra("LAUNCH_APP_PAIR")
                    if (!pairPkg.isNullOrEmpty()) {
                        kioskViewModel.launchSplitScreenAppPair(pairPkg)
                    }
                }

                // Setup screen wake/sleep callbacks
                LaunchedEffect(Unit) {
                    kioskViewModel.powerManager.onScreenWakeRequested = {
                        runOnUiThread {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            val lp = window.attributes
                            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            window.attributes = lp
                        }
                    }

                    kioskViewModel.powerManager.onScreenSleepRequested = {
                        runOnUiThread {
                            // Turn physical display off completely (no backlight/shade)
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            val lp = window.attributes
                            lp.screenBrightness = 0.0f
                            window.attributes = lp

                            val adminLocked = KioskDeviceAdminReceiver.lockDevice(this@MainActivity)
                            if (!adminLocked) {
                                KioskAccessibilityService.lockDeviceScreen()
                            }
                        }
                    }
                }

                // Request location permissions gracefully
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                    val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                    if (fineGranted || coarseGranted) {
                        kioskViewModel.locationTracker.startRealLocationUpdates()
                    }
                }

                LaunchedEffect(Unit) {
                    val fineCheck = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    if (fineCheck != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }

                // Kiosk Back-button safety handling: Back never exits app
                BackHandler {
                    if (telemetry.isStandbyActive) {
                        // Double tap or 3 power presses to wake
                    } else if (currentScreen == KioskScreen.SETTINGS) {
                        currentScreen = KioskScreen.HOME
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0F19)
                ) {
                    when {
                        telemetry.isStandbyActive -> {
                            StandbyScreen(
                                isChargerConnected = telemetry.isChargerConnected,
                                batteryPercent = telemetry.batteryPercent,
                                onDoubleTapWake = { kioskViewModel.wakeFromStandby() },
                                showWakeDeniedHint = showWakeDeniedHint
                            )
                        }
                        else -> {
                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "screen_transition"
                            ) { screen ->
                                when (screen) {
                                    KioskScreen.HOME -> KioskHomeScreen(
                                        viewModel = kioskViewModel,
                                        onOpenSettings = {
                                            kioskViewModel.loadInstalledAppsList()
                                            currentScreen = KioskScreen.SETTINGS
                                        }
                                    )
                                    KioskScreen.SETTINGS -> KioskSettingsScreen(
                                        viewModel = kioskViewModel,
                                        onBackToKiosk = {
                                            currentScreen = KioskScreen.HOME
                                        },
                                        onRequestLockTask = { requestLockTaskMode() },
                                        onReleaseLockTask = { releaseLockTaskMode() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pairPkg = intent.getStringExtra("LAUNCH_APP_PAIR")
        if (!pairPkg.isNullOrEmpty()) {
            viewModelInstance?.launchSplitScreenAppPair(pairPkg)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isKioskLockArmed) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
        }
    }

    private fun requestLockTaskMode() {
        try {
            startLockTask()
            isKioskLockArmed = true
            Log.i("MainActivity", "LockTask (Screen Pinning) enabled")
        } catch (e: Exception) {
            Log.w("MainActivity", "startLockTask failed: ${e.message}")
        }
    }

    private fun releaseLockTaskMode() {
        try {
            stopLockTask()
            isKioskLockArmed = false
            Log.i("MainActivity", "LockTask (Screen Pinning) stopped")
        } catch (e: Exception) {
            Log.w("MainActivity", "stopLockTask failed: ${e.message}")
        }
    }
}

// For test compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
