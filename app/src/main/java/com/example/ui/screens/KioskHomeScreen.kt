package com.example.ui.screens

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.model.AllowedAppEntity
import com.example.data.model.CashAlertItem
import com.example.data.model.TelemetryData
import com.example.ui.components.PinEntryDialog
import com.example.ui.components.RealOsmMapView
import com.example.ui.theme.WallpaperProvider
import com.example.viewmodel.KioskViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KioskHomeScreen(
    viewModel: KioskViewModel,
    onOpenSettings: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val allowedApps by viewModel.allowedApps.collectAsState()
    val allObstacles by viewModel.allObstacles.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val cornerTapCount by viewModel.cornerTapCount.collectAsState()
    val showPinDialog by viewModel.showPinDialog.collectAsState()
    val isServiceCountdownRevealed by viewModel.isServiceCountdownRevealed.collectAsState()

    // Requirement: Allow user to import their created icon for two app screen share
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomAppPairIcon(uri)
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        // LAYER 1: WALLPAPER POSITION MAP (Requirement: Integrate map in wallpaper position)
        if (settings.wallpaperTheme == "map") {
            RealOsmMapView(
                latitude = telemetry.latitude,
                longitude = telemetry.longitude,
                heading = telemetry.compassDegrees,
                speedKmH = telemetry.currentSpeedKmH,
                dynamicAlertDistance = telemetry.dynamicAlertDistanceMeters,
                obstacles = allObstacles,
                modifier = Modifier.fillMaxSize()
            )

            // Sleek dark vignette veil so UI controls stand out
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xCC070A13),
                                Color(0x990B0F19),
                                Color(0xDD070A13)
                            )
                        )
                    )
            )
        } else {
            // Classic gradient wallpaper
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(wallpaperBrush)
            )
        }

        // LAYER 2: CLEAN DASHBOARD & ALLOWED APPS
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
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
                            horizontal = if (isLandscape) 28.dp else 18.dp,
                            vertical = if (isLandscape) 10.dp else 16.dp
                        )
                ) {
                    // Top Bar: Clean Time & Date with 5-Tap Settings Hotspot
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
                                fontSize = if (isLandscape) 46.sp else 54.sp,
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
                                    else Color(0xFF0F172A).copy(alpha = 0.5f)
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
                                        tint = Color(0xFF94A3B8).copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 16.dp))

                    // Allowed Apps Grid (Center area of clean kiosk)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (allowedApps.isEmpty() && !settings.showAppPairOnDashboard) {
                            CleanEmptyAppsPlaceholder()
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = if (isLandscape) 96.dp else 88.dp),
                                contentPadding = PaddingValues(
                                    start = 4.dp,
                                    end = 4.dp,
                                    top = 4.dp,
                                    bottom = 84.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Requirement: Show imported App Pair icon card on dashboard
                                if (settings.showAppPairOnDashboard) {
                                    item {
                                        AppPairItemCard(
                                            name = settings.appPairName,
                                            iconPath = settings.appPairIconPath,
                                            onClick = { viewModel.launchSplitScreenAppPair(settings.appPairPackage) },
                                            onImportIcon = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                        )
                                    }
                                }

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

                // LAYER 3: CASH RECEIVED POPUP / VOICE ANNOUNCEMENT BANNER (Requirement: Read notifaction for cash recieve)
                AnimatedVisibility(
                    visible = telemetry.lastCashAlert != null,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                ) {
                    telemetry.lastCashAlert?.let { alert ->
                        CashReceivedBanner(
                            alert = alert,
                            onDismiss = { viewModel.dismissCashAlert() }
                        )
                    }
                }

                // Real-Time Road Hazard HUD (Only appears when approaching bump / gutter / traffic signal)
                AnimatedVisibility(
                    visible = (telemetry.isApproachingBump || telemetry.isApproachingSignal || telemetry.isApproachingGutter) && telemetry.lastCashAlert == null,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp, start = 20.dp, end = 20.dp)
                ) {
                    FloatingHazardAlertHUD(telemetry = telemetry)
                }

                // Disconnect countdown alert (Tap to cancel screen-off)
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
                                text = "Charger disconnected. Display turning completely off in ${telemetry.disconnectCountdown}s (Tap to Stay ON)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Bottom-Left Controls: Battery Indicator & Dual Screen App Pair Button
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
                            .background(Color(0xFF0F172A).copy(alpha = 0.92f))
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

                    // Requirement: App Pair / Dual Screen Split Share icon
                    val bottomCustomIconFile = remember(settings.appPairIconPath) {
                        if (settings.appPairIconPath.isNotEmpty()) File(settings.appPairIconPath) else null
                    }
                    val bottomCustomBitmap = remember(bottomCustomIconFile) {
                        if (bottomCustomIconFile != null && bottomCustomIconFile.exists()) {
                            BitmapFactory.decodeFile(bottomCustomIconFile.absolutePath)
                        } else null
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.88f))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                            .clickable {
                                viewModel.launchSplitScreenAppPair(settings.appPairPackage)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("app_pair_split_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (bottomCustomBitmap != null) {
                                Image(
                                    bitmap = bottomCustomBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Splitscreen,
                                    contentDescription = "Dual App Split Screen",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = "Split: ${settings.appPairName}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Import Created Icon",
                                tint = Color(0xFFBAE6FD),
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                            )
                        }
                    }
                }

                // Bottom-Right: Persistent Kilometer & Hidden Service Countdown
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
}

/**
 * Requirement: Dual-app split screen card on dashboard with custom imported icon
 */
@Composable
fun AppPairItemCard(
    name: String,
    iconPath: String,
    onClick: () -> Unit,
    onImportIcon: () -> Unit
) {
    val customIconFile = remember(iconPath) {
        if (iconPath.isNotEmpty()) File(iconPath) else null
    }
    val customIconBitmap = remember(customIconFile) {
        if (customIconFile != null && customIconFile.exists()) {
            BitmapFactory.decodeFile(customIconFile.absolutePath)
        } else null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("app_item_pair")
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.9f))
                .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (customIconBitmap != null) {
                Image(
                    bitmap = customIconBitmap.asImageBitmap(),
                    contentDescription = "$name Dual Split Screen",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.img_split_app_pair),
                    contentDescription = "$name Dual Split Screen",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Quick import icon badge for user to directly select their created icon
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0284C7))
                    .border(1.dp, Color.White, CircleShape)
                    .clickable { onImportIcon() }
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Import Created Icon",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "$name + Safe",
            color = Color(0xFF38BDF8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(76.dp)
        )
    }
}

/**
 * Requirement: Read notification for cash received banner
 */
@Composable
fun CashReceivedBanner(
    alert: CashAlertItem,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF065F46)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF34D399)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cash_received_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF059669)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "💰 CASH RECEIVED: ",
                            color = Color(0xFF6EE7B7),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "₹${alert.amountText}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                    }
                    Text(
                        text = "From: ${alert.sender}",
                        color = Color(0xFFD1FAE5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
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
            .background(Color(0xFF0F172A).copy(alpha = 0.94f))
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
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "TODAY",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.1f", telemetry.dailyRunKm),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "km",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                // Periodical Service Countdown View (Hidden inside, revealed on tap)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(serviceStatusColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CarRepair,
                            contentDescription = null,
                            tint = serviceStatusColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "SERVICE DUE IN",
                            color = serviceStatusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.0f", serviceRemaining),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "km",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AllowedAppItemCard(
    app: AllowedAppEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var appIconDrawable by remember(app.packageName) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(app.packageName) {
        try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(app.packageName, 0)
            appIconDrawable = pm.getApplicationIcon(info)
        } catch (_: PackageManager.NameNotFoundException) {
            appIconDrawable = null
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("app_item_${app.packageName}")
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.9f))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (appIconDrawable != null) {
                Image(
                    bitmap = appIconDrawable!!.toBitmap(128, 128).asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier.size(46.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = app.appName,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = app.appName,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(76.dp)
        )
    }
}

@Composable
fun CleanEmptyAppsPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No apps on dashboard",
                color = Color(0xFF94A3B8),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap top-right corner 5 times to select allowed apps in settings",
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
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
        isApproachingSignal -> "TRAFFIC LIGHT AHEAD"
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
            .testTag("floating_hazard_hud")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = titleText,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                    Text(
                        text = subText,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "${telemetry.nearestDistanceMeters.toInt()}m",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
