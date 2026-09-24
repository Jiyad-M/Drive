package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.KioskHomeScreen
import com.example.ui.screens.KioskSettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.KioskViewModel

enum class KioskScreen {
    HOME,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                val kioskViewModel: KioskViewModel = viewModel()
                var currentScreen by remember { mutableStateOf(KioskScreen.HOME) }

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

                // Kiosk Back-button safety handling
                BackHandler {
                    if (currentScreen == KioskScreen.SETTINGS) {
                        currentScreen = KioskScreen.HOME
                    }
                    // If on HOME, kiosk stays on HOME (prevents accidental exit without PIN)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0F19)
                ) {
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
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// For test compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
