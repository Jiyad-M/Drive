package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.KioskSettingsEntity
import com.example.data.model.RoadObstacleEntity
import com.example.data.model.TelemetryData
import com.example.service.InstalledAppItem
import com.example.ui.components.RealOsmMapView
import com.example.ui.theme.WallpaperOption
import com.example.ui.theme.WallpaperProvider
import com.example.viewmodel.KioskViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskSettingsScreen(
    viewModel: KioskViewModel,
    onBackToKiosk: () -> Unit,
    onRequestLockTask: () -> Unit = {},
    onReleaseLockTask: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val allObstacles by viewModel.allObstacles.collectAsState()
    val installedApps by viewModel.installedAppsList.collectAsState()
    val isFetchingObstacles by viewModel.isFetchingObstacles.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Launcher & Kiosk",
        "Battery & Power",
        "Allowed Apps",
        "Password",
        "Sensors & Audio",
        "Service & Run",
        "Real Map",
        "Wallpaper"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Kiosk Configuration",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToKiosk,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Kiosk",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0F172A),
                contentColor = Color(0xFF38BDF8),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF38BDF8)
                    )
                },
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == index) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.Security
                                    1 -> Icons.Default.BatteryChargingFull
                                    2 -> Icons.Default.Apps
                                    3 -> Icons.Default.Lock
                                    4 -> Icons.Default.Sensors
                                    5 -> Icons.Default.CarRepair
                                    6 -> Icons.Default.Map
                                    else -> Icons.Default.Wallpaper
                                },
                                contentDescription = title,
                                tint = if (selectedTab == index) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> LauncherAndKioskTab(
                        settings = settings,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onRequestLockTask = onRequestLockTask,
                        onReleaseLockTask = onReleaseLockTask
                    )
                    1 -> BatteryAndPowerTab(
                        settings = settings,
                        telemetry = telemetry,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onTestStandby = { viewModel.enterStandby() },
                        onTestEmergency = { viewModel.triggerEmergency() }
                    )
                    2 -> AllowedAppsTab(
                        apps = installedApps,
                        onToggleApp = { pkg, name, allowed ->
                            viewModel.toggleAppAllowed(pkg, name, allowed)
                        }
                    )
                    3 -> PasswordTab(
                        currentPin = settings.pinCode,
                        onChangePin = { newPin ->
                            viewModel.changePin(newPin)
                        }
                    )
                    4 -> SensorsAndAudioTab(
                        settings = settings,
                        telemetry = telemetry,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onTestBumpBeep = { viewModel.testBumpBeep() },
                        onTestSignalBeep = { viewModel.testSignalBeep() },
                        onTestVerticalBumpSpike = { viewModel.testSimulateVerticalBumpSpike() }
                    )
                    5 -> ServiceAndOdometerTab(
                        settings = settings,
                        telemetry = telemetry,
                        onResetDailyKm = { viewModel.resetDailyKm() },
                        onResetServiceCountdown = { viewModel.resetServiceCountdown() },
                        onUpdateServiceInterval = { viewModel.updateServiceInterval(it) }
                    )
                    6 -> RealMapTab(
                        telemetry = telemetry,
                        obstacles = allObstacles,
                        isFetching = isFetchingObstacles,
                        onFetchData = {
                            viewModel.fetchRoadObstacles(telemetry.latitude, telemetry.longitude)
                        },
                        onAddObstacle = { lat, lon, type ->
                            viewModel.addManualObstacle(lat, lon, type, "")
                        },
                        onDeleteObstacle = { viewModel.deleteObstacle(it) },
                        onResetStrikes = { viewModel.resetFakeBumpStrikes(it) }
                    )
                    7 -> WallpaperTab(
                        currentTheme = settings.wallpaperTheme,
                        onSelectTheme = { themeId ->
                            viewModel.updateWallpaper(themeId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RealMapTab(
    telemetry: com.example.data.model.TelemetryData,
    obstacles: List<RoadObstacleEntity>,
    isFetching: Boolean,
    onFetchData: () -> Unit,
    onAddObstacle: (Double, Double, String) -> Unit,
    onDeleteObstacle: (String) -> Unit,
    onResetStrikes: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onFetchData,
                enabled = !isFetching,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("fetch_osm_data_button")
            ) {
                if (isFetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fetching OSM Data...", fontSize = 12.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Fetch Data",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fetch Bumps & Signals", fontSize = 12.sp, color = Color.White)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val lat = if (telemetry.latitude != 0.0) telemetry.latitude else 37.7749
                        val lon = if (telemetry.longitude != 0.0) telemetry.longitude else -122.4194
                        onAddObstacle(lat + 0.0005, lon, "SPEED_BUMP")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("add_bump_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Bump", fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        val lat = if (telemetry.latitude != 0.0) telemetry.latitude else 37.7749
                        val lon = if (telemetry.longitude != 0.0) telemetry.longitude else -122.4194
                        onAddObstacle(lat + 0.0012, lon, "TRAFFIC_SIGNAL")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("add_signal_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Signal", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Real Interactive Map Container
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
        ) {
            RealOsmMapView(
                latitude = if (telemetry.latitude != 0.0) telemetry.latitude else 37.7749,
                longitude = if (telemetry.longitude != 0.0) telemetry.longitude else -122.4194,
                heading = telemetry.compassDegrees,
                speedKmH = telemetry.currentSpeedKmH,
                dynamicAlertDistance = telemetry.dynamicAlertDistanceMeters,
                obstacles = obstacles,
                onAddObstacleAtLocation = { lat, lon, type ->
                    onAddObstacle(lat, lon, type)
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Obstacles List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tracked Obstacles (${obstacles.size})",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Dynamic Alert: ${telemetry.dynamicAlertDistanceMeters.toInt()}m",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(obstacles, key = { it.id }) { obs ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (obs.isSuppressedFake) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFF1E293B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (obs.type == "SPEED_BUMP") Color(0xFFF59E0B) else Color(0xFFEF4444)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (obs.type == "SPEED_BUMP") Icons.Default.Warning else Icons.Default.Traffic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = obs.title.ifEmpty { obs.type },
                                    color = if (obs.isSuppressedFake) Color(0xFF94A3B8) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (obs.isAutoDetected) {
                                        Text("Auto-Detected", color = Color(0xFF38BDF8), fontSize = 10.sp)
                                    }
                                    if (obs.strikeCount > 0) {
                                        Text(
                                            "Unfelt Strikes: ${obs.strikeCount}",
                                            color = if (obs.isSuppressedFake) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (obs.isSuppressedFake) {
                                        Text("Suppressed (Fake)", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (obs.strikeCount > 0) {
                                IconButton(
                                    onClick = { onResetStrikes(obs.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset Strikes", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(
                                onClick = { onDeleteObstacle(obs.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WallpaperTab(
    currentTheme: String,
    onSelectTheme: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Select Kiosk Wallpaper",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Choose an automotive gradient theme for your kiosk front dashboard.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(WallpaperProvider.options, key = { it.id }) { option ->
                val isSelected = option.id == currentTheme
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectTheme(option.id) }
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .testTag("wallpaper_${option.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(option.brush)
                                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = option.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isSelected) "Active Wallpaper" else "Tap to apply",
                                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AllowedAppsTab(
    apps: List<InstalledAppItem>,
    onToggleApp: (String, String, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isEmpty()) apps
        else apps.filter { it.label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Manage Allowed Apps",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Toggle applications permitted on the Kiosk front screen.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search installed applications...", color = Color(0xFF64748B), fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            val icon = app.icon
                            if (icon != null) {
                                Image(
                                    bitmap = icon.toBitmap(80, 80).asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0284C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = app.label.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = app.label,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Switch(
                            checked = app.isAllowed,
                            onCheckedChange = { isChecked ->
                                onToggleApp(app.packageName, app.label, isChecked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0284C7),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.testTag("app_switch_${app.packageName}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordTab(
    currentPin: String,
    onChangePin: (String) -> Unit
) {
    var oldPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Kiosk Password Settings",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "This PIN protects the Kiosk from unauthorized settings changes and exiting.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = oldPinInput,
                    onValueChange = { oldPinInput = it },
                    label = { Text("Current PIN (Default: 1234)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("old_pin_input")
                )

                OutlinedTextField(
                    value = newPinInput,
                    onValueChange = { newPinInput = it },
                    label = { Text("New PIN (4-6 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("new_pin_input")
                )

                OutlinedTextField(
                    value = confirmPinInput,
                    onValueChange = { confirmPinInput = it },
                    label = { Text("Confirm New PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("confirm_pin_input")
                )

                if (message.isNotEmpty()) {
                    Text(
                        text = message,
                        color = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = {
                        if (oldPinInput != currentPin) {
                            message = "Current PIN is incorrect."
                            isSuccess = false
                        } else if (newPinInput.length < 4 || newPinInput.length > 6) {
                            message = "PIN must be between 4 and 6 digits."
                            isSuccess = false
                        } else if (newPinInput != confirmPinInput) {
                            message = "New PIN and confirmation do not match."
                            isSuccess = false
                        } else {
                            onChangePin(newPinInput)
                            message = "PIN successfully updated!"
                            isSuccess = true
                            oldPinInput = ""
                            newPinInput = ""
                            confirmPinInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_pin_button")
                ) {
                    Text("Save New PIN", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SensorsAndAudioTab(
    settings: com.example.data.model.KioskSettingsEntity,
    telemetry: com.example.data.model.TelemetryData,
    onUpdateSettings: (com.example.data.model.KioskSettingsEntity) -> Unit,
    onTestBumpBeep: () -> Unit,
    onTestSignalBeep: () -> Unit,
    onTestVerticalBumpSpike: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Road Sensors & Audio Calibration",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Fine-tune 3-beep speed bump alerts, long-beep traffic signal alerts, and upward accelerometer bump detection.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Audio Alert Tests",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Audition the speed bump and traffic light tones.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onTestBumpBeep,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("test_bump_beep_button")
                            ) {
                                Text("Test 3-Beep Bump", fontSize = 12.sp)
                            }

                            Button(
                                onClick = onTestSignalBeep,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("test_signal_beep_button")
                            ) {
                                Text("Test Long Beep Signal", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Sound Alerts Enabled",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Mute or enable audible tones",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = settings.soundEnabled,
                                onCheckedChange = {
                                    onUpdateSettings(settings.copy(soundEnabled = it))
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF0284C7))
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Upward Car Bump Detection (Accelerometer)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "When the car experiences vertical upward acceleration while driving, AutoKiosk registers a new speed bump using GPS & compass.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = "Sensitivity Threshold: ${String.format("%.1f", settings.bumpSensitivity)} m/s² (Earth gravity = 9.8)",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Slider(
                            value = settings.bumpSensitivity,
                            onValueChange = {
                                onUpdateSettings(settings.copy(bumpSensitivity = it))
                            },
                            valueRange = 11.0f..22.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF38BDF8),
                                activeTrackColor = Color(0xFF0284C7)
                            )
                        )

                        Button(
                            onClick = onTestVerticalBumpSpike,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("simulate_bump_spike_button")
                        ) {
                            Text("Simulate Vertical Upward Bump Movement", fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Fake Bump Auto-Suppression",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "If a mapped bump is passed ${settings.fakeBumpThreshold} times with NO vertical accelerometer spike detected, it is marked as fake and suppressed.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 2, 3, 4).forEach { threshold ->
                                FilterChip(
                                    selected = settings.fakeBumpThreshold == threshold,
                                    onClick = {
                                        onUpdateSettings(settings.copy(fakeBumpThreshold = threshold))
                                    },
                                    label = { Text("$threshold Strike${if (threshold > 1) "s" else ""}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF0284C7),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Requirement:
 * "Add daily run kilometer
 * Also an option periodical survice kilometer in condown motion. This hide inside daily kiloeter."
 * "Also works this app rotate screen"
 */
@Composable
fun ServiceAndOdometerTab(
    settings: KioskSettingsEntity,
    telemetry: TelemetryData,
    onResetDailyKm: () -> Unit,
    onResetServiceCountdown: () -> Unit,
    onUpdateServiceInterval: (Double) -> Unit
) {
    val serviceRemaining = telemetry.serviceRemainingKm
    val serviceInterval = if (telemetry.serviceIntervalKm <= 0.0) 5000.0 else telemetry.serviceIntervalKm

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Daily Run & Total Odometer Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Daily Run Kilometer",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Button(
                            onClick = onResetDailyKm,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Daily", fontSize = 12.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(Locale.US, "%.1f", telemetry.dailyRunKm),
                                color = Color(0xFF38BDF8),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Today's Run (km)",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(Locale.US, "%,.0f", telemetry.totalOdometerKm),
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Total Vehicle Odo (km)",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• Daily run resets automatically at midnight (or manually with Reset button).\n• Distance is calculated from live GPS location tracking in real-time.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Periodical Service Kilometer Countdown Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CarRepair,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Periodical Service Countdown",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Button(
                            onClick = onResetServiceCountdown,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Service Done (Reset)", fontSize = 12.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Remaining Countdown display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "COUNTDOWN REMAINING",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", serviceRemaining),
                                    color = when {
                                        serviceRemaining <= 0.0 -> Color(0xFFEF4444)
                                        serviceRemaining <= 500.0 -> Color(0xFFF59E0B)
                                        else -> Color(0xFF10B981)
                                    },
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "KM LEFT",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 5.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        serviceRemaining <= 0.0 -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                        serviceRemaining <= 500.0 -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                        else -> Color(0xFF10B981).copy(alpha = 0.2f)
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when {
                                    serviceRemaining <= 0.0 -> "OVERDUE"
                                    serviceRemaining <= 500.0 -> "DUE SOON"
                                    else -> "HEALTHY"
                                },
                                color = when {
                                    serviceRemaining <= 0.0 -> Color(0xFFEF4444)
                                    serviceRemaining <= 500.0 -> Color(0xFFF59E0B)
                                    else -> Color(0xFF10B981)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Select Service Cycle Interval:",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3000.0, 5000.0, 7500.0, 10000.0, 15000.0).forEach { interval ->
                            val isSelected = serviceInterval == interval
                            FilterChip(
                                selected = isSelected,
                                onClick = { onUpdateServiceInterval(interval) },
                                label = {
                                    Text("${interval.toInt()} km", fontSize = 12.sp)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "• On the home screen, this countdown is hidden inside the daily kilometer card.\n• Tap the daily run card on the dashboard to toggle reveal and hide.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Screen Rotation Info Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Screen Rotation Support",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Supports both Portrait and Automotive Landscape orientations automatically with responsive layouts.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
