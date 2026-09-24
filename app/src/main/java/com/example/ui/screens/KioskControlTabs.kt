package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KioskSettingsEntity
import com.example.data.model.TelemetryData
import com.example.service.KioskAccessibilityService
import com.example.service.KioskDeviceAdminReceiver
import com.example.ui.components.PinEntryDialog

@Composable
fun LauncherAndKioskTab(
    settings: KioskSettingsEntity,
    onUpdateSettings: (KioskSettingsEntity) -> Unit,
    onRequestLockTask: () -> Unit,
    onReleaseLockTask: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var showExitPinDialog by remember { mutableStateOf(false) }
    var actionStatusMessage by remember { mutableStateOf("") }

    val isAdminActive = remember(settings) {
        KioskDeviceAdminReceiver.isDeviceAdminActive(context)
    }

    val isAccessibilityActive = remember(settings) {
        KioskAccessibilityService.isAccessibilityServiceEnabled(context)
    }

    if (showExitPinDialog) {
        PinEntryDialog(
            correctPin = settings.pinCode,
            onPinSuccess = {
                showExitPinDialog = false
                onReleaseLockTask()
                actionStatusMessage = "Kiosk unlocked. You can now minimize or switch apps."
            },
            onDismiss = { showExitPinDialog = false }
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("launcher_kiosk_tab")
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Launcher & Kiosk Lockdown",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure Drive Safe as inescapable launcher, lock screen navigation, and restrict unwanted apps.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Section 1: Default Launcher Configuration
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0284C7).copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Default Home Launcher", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Make Drive Safe the system home screen", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            openHomeLauncherSettings(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("set_default_launcher_button")
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Set Drive Safe as Default Launcher", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• When set as default launcher, pressing the Home button always returns directly to Drive Safe.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Section 2: Strict Kiosk Lock (Can't minimize, Can't exit)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Strict Kiosk Mode (SureLock)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Can't minimize, Can't exit without PIN", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }

                        Switch(
                            checked = settings.kioskLockEnabled,
                            onCheckedChange = { isChecked ->
                                onUpdateSettings(settings.copy(kioskLockEnabled = isChecked))
                                if (isChecked) {
                                    onRequestLockTask()
                                } else {
                                    showExitPinDialog = true
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFEF4444))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onRequestLockTask() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("arm_lock_task_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pin Screen", fontSize = 12.sp, color = Color(0xFF38BDF8))
                        }

                        Button(
                            onClick = { showExitPinDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("exit_kiosk_button")
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exit Kiosk (PIN)", fontSize = 12.sp, color = Color.White)
                        }
                    }

                    if (actionStatusMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(actionStatusMessage, color = Color(0xFF10B981), fontSize = 12.sp)
                    }
                }
            }
        }

        // Section 3: Advanced Lockdown Services (Device Admin & Accessibility Watchdog)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Deep Kiosk Security Privileges", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Grant system permissions for hardware locking and unwanted app interception.", color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))

                    // Device Administrator Item
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Device Administrator", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isAdminActive) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isAdminActive) "ACTIVE" else "NOT ENABLED",
                                        color = if (isAdminActive) Color(0xFF10B981) else Color(0xFFEF4444),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text("Allows instant screen lock when charger disconnects.", color = Color(0xFF64748B), fontSize = 11.sp)
                        }

                        if (!isAdminActive && activity != null) {
                            Button(
                                onClick = { KioskDeviceAdminReceiver.requestDeviceAdmin(activity) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Activate", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Accessibility App Blocker Item
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("App Blocker Service", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isAccessibilityActive) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isAccessibilityActive) "ACTIVE" else "NOT ENABLED",
                                        color = if (isAccessibilityActive) Color(0xFF10B981) else Color(0xFFEF4444),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text("Intersects and blocks unauthorized apps immediately.", color = Color(0xFF64748B), fontSize = 11.sp)
                        }

                        if (!isAccessibilityActive) {
                            Button(
                                onClick = { KioskAccessibilityService.openAccessibilitySettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Data Saver & No Background Data for Unwanted Apps
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DataSaverOn, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("No Background Data for Unwanted Apps", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Restrict background cellular/Wi-Fi data usage so unallowed apps cannot consume bandwidth while driving.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { openDataSaverSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("open_data_saver_button")
                        ) {
                            Text("Data Saver Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { openBackgroundDataRestrictions(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("App Data Usage", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryAndPowerTab(
    settings: KioskSettingsEntity,
    telemetry: TelemetryData,
    onUpdateSettings: (KioskSettingsEntity) -> Unit,
    onTestStandby: () -> Unit,
    onTestEmergency: () -> Unit
) {
    var emergencyNumberInput by remember { mutableStateOf(settings.emergencyNumber) }
    var emergencyContactInput by remember { mutableStateOf(settings.emergencySmsContact) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("battery_power_tab")
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Battery & Power Automation",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage charger-based screen state, power button denials, double-tap wake, and 3-press emergency SOS.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Section 1: Real-time Power Status Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (telemetry.isChargerConnected) Color(0xFF10B981).copy(alpha = 0.25f)
                                    else Color(0xFFF59E0B).copy(alpha = 0.25f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (telemetry.isChargerConnected) Icons.Default.BatteryChargingFull else Icons.Default.PowerOff,
                                contentDescription = null,
                                tint = if (telemetry.isChargerConnected) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (telemetry.isChargerConnected) "Charger Connected" else "Running on Battery",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (telemetry.isChargerConnected) "Screen kept ON automatically" else "Auto-off active on disconnect",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = "${telemetry.batteryPercent}%",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Section 2: Battery Mode Automation (Charger Connected -> ON, Disconnect -> OFF in 5s)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Screen Power", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Charger connected: Screen ON\nDisconnect: OFF with delay", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }

                        Switch(
                            checked = settings.batteryModeEnabled,
                            onCheckedChange = { isChecked ->
                                onUpdateSettings(settings.copy(batteryModeEnabled = isChecked))
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF0284C7))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Disconnect Turn-Off Delay:", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 10, 15).forEach { sec ->
                            val isSelected = settings.disconnectDelaySec == sec
                            FilterChip(
                                selected = isSelected,
                                onClick = { onUpdateSettings(settings.copy(disconnectDelaySec = sec)) },
                                label = { Text("${sec}s delay", fontSize = 12.sp) },
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

        // Section 3: Deny Power Button Wakeup & Double-Tap Wakeup
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Wakeup Gesture Rules", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Deny Normal Power Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Deny Power Button Wakeup", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Denies power button wake while disconnected on battery", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }

                        Switch(
                            checked = settings.denyPowerButtonWakeup,
                            onCheckedChange = { onUpdateSettings(settings.copy(denyPowerButtonWakeup = it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF0284C7))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Double Tap Wakeup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Double-Tap Screen Wakeup", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Tap the screen twice to wake up the dashboard", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }

                        Switch(
                            checked = settings.doubleTapWakeupEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(doubleTapWakeupEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF0284C7))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = onTestStandby,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth().testTag("test_standby_button")
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Screen Sleep (Try Double-Tap to Wake)", fontSize = 12.sp)
                    }
                }
            }
        }

        // Section 4: Press Power 3 Times to Wake Display on Battery
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("3-Press Power Display Wakeup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Press power 3 times to turn display ON on battery", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }

                        Switch(
                            checked = settings.power3TimesWakeupEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(power3TimesWakeupEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF0284C7))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "• Normal single power button press is denied on battery to prevent accidental wakeup.\n• Pressing physical power button 3 times in quick succession immediately turns the display ON.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Manual Emergency SOS Contact Settings:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = emergencyNumberInput,
                        onValueChange = {
                            emergencyNumberInput = it
                            onUpdateSettings(settings.copy(emergencyNumber = it))
                        },
                        label = { Text("Emergency Phone Number (911 / 112)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("emergency_number_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = emergencyContactInput,
                        onValueChange = {
                            emergencyContactInput = it
                            onUpdateSettings(settings.copy(emergencySmsContact = it))
                        },
                        label = { Text("Emergency SOS SMS Contact (Phone Number)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("emergency_contact_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onTestEmergency,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("test_emergency_button")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Emergency SOS Screen Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun openHomeLauncherSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallbackIntent)
    }
}

private fun openDataSaverSettings(context: Context) {
    try {
        val intent = Intent("android.settings.DATA_SAVER_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }
}

private fun openBackgroundDataRestrictions(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_DATA_USAGE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }
}
