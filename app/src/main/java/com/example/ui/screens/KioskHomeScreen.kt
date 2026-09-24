package com.example.ui.screens

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.AllowedAppEntity
import com.example.data.model.TelemetryData
import com.example.ui.components.PinEntryDialog
import com.example.ui.theme.WallpaperProvider
import com.example.viewmodel.KioskViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drive Safe - Clean Kiosk Home View
 *
 * User requirements:
 * 1. Main view clean. Remove complicated ui.
 * 2. Stay time, allowed apps, km on right bottom.
 * 3. Daily run kilometer + periodical service countdown hidden inside, revealed on tap.
 * 4. 5-tap top-right corner opens settings.
 * 5. Screen rotation support.
 */
@Composable
fun KioskHomeScreen(
    viewModel: KioskViewModel,
    onOpenSettings: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val allowedApps by viewModel.allowedApps.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val cornerTapCount by viewModel.cornerTapCount.collectAsState()
    val showPinDialog by viewModel.showPinDialog.collectAsState()
    val isServiceCountdownRevealed by viewModel.isServiceCountdownRevealed.collectAsState()

    // Digital Clock State
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now)
            delay(1000)
        }
    }

    val wallpaperBrush = WallpaperProvider.getBrushForTheme(
        settings.wallpaperTheme,
        settings.customWallpaperColor
    )

    if (showPinDialog) {
        PinEntryDialog(
            correctPin = settings.pinCode,
            onPinSuccess = {
                viewModel.dismissPinDialog()
                onOpenSettings()
            },
            onDismiss = {
                viewModel.dismissPinDialog()
            }
        )
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .background(wallpaperBrush)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Dashboard Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (isLandscape) 32.dp else 20.dp,
                        vertical = if (isLandscape) 12.dp else 18.dp
                    )
            ) {
                // Top Bar: Clean Time & Date with subtle Top-Right 5-Tap Settings Hotspot
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Time and Date Centered
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentTime.ifEmpty { "12:00" },
                            fontSize = if (isLandscape) 48.sp else 58.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.testTag("kiosk_clock")
                        )
                        Text(
                            text = currentDate.ifEmpty { "Loading Date..." },
                            fontSize = if (isLandscape) 13.sp else 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    // Requirement: 5-tap top-right corner to open Settings
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .testTag("kiosk_settings_hotspot")
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.onTopRightCornerTapped()
                            }
                            .background(
                                if (cornerTapCount > 0) Color(0xFF0284C7).copy(alpha = 0.25f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (cornerTapCount > 0) {
                                repeat(5) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (index < cornerTapCount) Color(0xFF38BDF8)
                                                else Color(0xFF475569)
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$cornerTapCount/5",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Settings Hotspot (Tap 5x)",
                                    tint = Color(0xFF94A3B8).copy(alpha = 0.35f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 20.dp))

                // Allowed Apps Grid (The center area of the clean kiosk)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (allowedApps.isEmpty()) {
                        CleanEmptyAppsPlaceholder()
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = if (isLandscape) 96.dp else 88.dp),
                            contentPadding = PaddingValues(
                                start = 4.dp,
                                end = 4.dp,
                                top = 4.dp,
                                bottom = 80.dp // Leave breathing room for the bottom-right KM widget
                            ),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(allowedApps, key = { it.packageName }) { app ->
                                AllowedAppItemCard(
                                    app = app,
                                    onClick = { viewModel.launchApp(app.packageName) }
                                )
                            }
                        }
                    }
                }
            }

            // Real-Time Road Hazard HUD (Only appears when approaching bump / gutter / traffic signal)
            AnimatedVisibility(
                visible = telemetry.isApproachingBump || telemetry.isApproachingSignal || telemetry.isApproachingGutter,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp, start = 20.dp, end = 20.dp)
            ) {
                FloatingHazardAlertHUD(telemetry = telemetry)
            }

            // Requirement: Disconnect off within small delay eg 5sec
            // Floating countdown alert that user can tap to stay on
            AnimatedVisibility(
                visible = telemetry.disconnectCountdown != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .clickable { viewModel.cancelDisconnectCountdown() }
                        .testTag("disconnect_countdown_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Charger disconnected. Screen turning off in ${telemetry.disconnectCountdown}s (Tap to Cancel)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Bottom-Left: Subtle Power & Emergency SOS hotspot
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Battery & Charger indicator pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.88f))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (telemetry.isChargerConnected) Icons.Default.BatteryChargingFull else Icons.Default.PowerOff,
                            contentDescription = null,
                            tint = if (telemetry.isChargerConnected) Color(0xFF10B981) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (telemetry.isChargerConnected) "⚡ ${telemetry.batteryPercent}%" else "${telemetry.batteryPercent}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // SOS Trigger button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF7F1D1D).copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable { viewModel.triggerEmergency() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("manual_sos_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Emergency,
                            contentDescription = "Manual SOS",
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "SOS",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Requirement: "km on right bottom"
            // Shows Daily Run KM, and tapping reveals the periodical service countdown inside!
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 16.dp)
            ) {
                CleanBottomRightKmWidget(
                    telemetry = telemetry,
                    isRevealed = isServiceCountdownRevealed,
                    onToggleReveal = { viewModel.toggleServiceCountdownRevealed() }
                )
            }
        }
    }
}

/**
 * Requirement: "km on right bottom"
 * "Add daily run kilometer. Also an option periodical survice kilometer in condown motion. This hide inside daily kiloeter."
 */
@Composable
fun CleanBottomRightKmWidget(
    telemetry: TelemetryData,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit
) {
    val serviceRemaining = telemetry.serviceRemainingKm
    val serviceStatusColor = when {
        serviceRemaining <= 0.0 -> Color(0xFFEF4444)
        serviceRemaining <= 500.0 -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    Box(
        modifier = Modifier
            .testTag("km_right_bottom_widget")
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.92f))
            .border(
                1.dp,
                if (isRevealed) serviceStatusColor.copy(alpha = 0.7f) else Color(0xFF334155),
                RoundedCornerShape(20.dp)
            )
            .clickable { onToggleReveal() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        AnimatedContent(
            targetState = isRevealed,
            transitionSpec = {
                (fadeIn(animationSpec = tween(180)) + slideInVertically { it / 2 })
                    .togetherWith(fadeOut(animationSpec = tween(150)) + slideOutVertically { -it / 2 })
            },
            label = "km_pill_flip"
        ) { revealed ->
            if (!revealed) {
                // Daily Run KM View
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7).copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = "Daily KM",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.1f", telemetry.dailyRunKm),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "km",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                        Text(
                            text = "Daily Run",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Periodical Service Countdown in Motion (Hidden inside, revealed on tap!)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(serviceStatusColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CarRepair,
                            contentDescription = "Service Countdown",
                            tint = serviceStatusColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.0f", serviceRemaining),
                                color = serviceStatusColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "km left",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                        Text(
                            text = "Next Service",
                            color = serviceStatusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingHazardAlertHUD(telemetry: TelemetryData) {
    val isApproachingSignal = telemetry.isApproachingSignal
    val isApproachingGutter = telemetry.isApproachingGutter

    val bannerColor = when {
        isApproachingGutter -> Color(0xFF0284C7)
        isApproachingSignal -> Color(0xFFDC2626)
        else -> Color(0xFFD97706)
    }

    val titleText = when {
        isApproachingGutter -> "BIG GUTTER / POTHOLE AHEAD"
        isApproachingSignal -> "TRAFFIC SIGNAL AHEAD"
        else -> "SPEED BUMP AHEAD"
    }

    val subText = when {
        isApproachingGutter -> "Single beep alert"
        isApproachingSignal -> "Long beep alert"
        else -> "3-beep alert"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bannerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hazard_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        isApproachingGutter -> Icons.Default.Warning
                        isApproachingSignal -> Icons.Default.Traffic
                        else -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = titleText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${telemetry.currentSpeedKmH.toInt()} km/h",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun CleanEmptyAppsPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No apps allowed yet",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap top-right corner 5 times to open Settings.",
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AllowedAppItemCard(
    app: AllowedAppEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var appIconDrawable by remember { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(app.packageName) {
        try {
            appIconDrawable = context.packageManager.getApplicationIcon(app.packageName)
        } catch (_: Exception) {}
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .testTag("allowed_app_${app.packageName}")
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val icon = appIconDrawable
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap(96, 96).asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0284C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.appName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = app.appName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
