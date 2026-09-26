package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.KioskSettingsEntity
import com.example.data.model.TelemetryData
import com.example.service.CashNotificationListenerService
import com.example.service.KioskAccessibilityService
import com.example.service.KioskDeviceAdminReceiver
import com.example.ui.components.PinEntryDialog
import com.example.ui.components.SmoothToggleSwitch
import java.io.File

@Composable
fun LauncherAndKioskTab(
    settings: KioskSettingsEntity,
    onUpdateSettings: (KioskSettingsEntity) -> Unit,
    onRequestLockTask: () -> Unit,
    onReleaseLockTask: () -> Unit,
    onLaunchSplitScreen: ((String) -> Unit)? = null,
    onPinAppPairShortcut: ((String, String) -> Unit)? = null,
    onImportIcon: ((Uri) -> Unit)? = null,
    onResetIcon: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var showExitPinDialog by remember { mutableStateOf(false) }
    var actionStatusMessage by remember { mutableStateOf("") }

    var companionPackageInput by remember { mutableStateOf(settings.appPairPackage) }
    var companionNameInput by remember { mutableStateOf(settings.appPairName) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportIcon?.invoke(uri)
        }
    }

    val customIconFile = remember(settings.appPairIconPath) {
        if (settings.appPairIconPath.isNotEmpty()) File(settings.appPairIconPath) else null
    }
    val customBitmap = remember(customIconFile) {
        if (customIconFile != null && customIconFile.exists()) {
            BitmapFactory.decodeFile(customIconFile.absolutePath)
        } else null
    }

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
            onDismiss = {
                showExitPinDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("launcher_kiosk_tab"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // App Pair / Dual Screen Shortcut Card (Requirement: Stored two app screen share in one icon)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF0284C7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Splitscreen, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Dual Screen / App Pair Shortcut", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Touch one icon to open Drive Safe & companion app simultaneously", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = companionNameInput,
                        onValueChange = {
                            companionNameInput = it
                            onUpdateSettings(settings.copy(appPairName = it))
                        },
                        label = { Text("Companion App Name (e.g., Google Maps, Uber)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = companionPackageInput,
                        onValueChange = {
                            companionPackageInput = it
                            onUpdateSettings(settings.copy(appPairPackage = it))
                        },
                        label = { Text("Package Name (e.g., com.google.android.apps.maps)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Import Custom Icon Section (Requirement: I already creTe a icon of two app screen share. I wanna import that icon)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
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
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (customBitmap != null) {
                                    Image(
                                        bitmap = customBitmap.asImageBitmap(),
                                        contentDescription = "Imported App Pair Icon",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(R.drawable.img_split_app_pair),
                                        contentDescription = "Default App Pair Icon",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = if (customBitmap != null) "Custom Icon Imported" else "Standard Icon",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (customBitmap != null) "✓ Your icon is active" else "Import your created icon",
                                    color = if (customBitmap != null) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import Icon", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (customBitmap != null) {
                                OutlinedButton(
                                    onClick = { onResetIcon?.invoke() },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF64748B)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("Reset", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Toggle: Show App Pair card on dashboard grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show On Front Dashboard Grid", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Places your dual-app shortcut directly inside the home app grid", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }

                        SmoothToggleSwitch(
                            checked = settings.showAppPairOnDashboard,
                            onCheckedChange = { onUpdateSettings(settings.copy(showAppPairOnDashboard = it)) },
                            testTag = "show_app_pair_dashboard_toggle"
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onLaunchSplitScreen?.invoke(companionPackageInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Splitscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Split Screen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onPinAppPairShortcut?.invoke(companionPackageInput, companionNameInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pin to Home", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 1: Default Home Launcher
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
                                Text("Default Home App", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Set Drive Safe as primary home launcher", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = { openHomeLauncherSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Configure", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 2: Strict Kiosk Mode with Smooth Toggle Switch
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

                        // Smooth Toggle Switch
                        SmoothToggleSwitch(
                            checked = settings.kioskLockEnabled,
                            onCheckedChange = { isChecked ->
                                onUpdateSettings(settings.copy(kioskLockEnabled = isChecked))
                                if (isChecked) {
                                    onRequestLockTask()
                                } else {
                                    showExitPinDialog = true
                                }
                            },
                            activeColor = Color(0xFFEF4444),
                            testTag = "kiosk_lock_toggle"
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
                            modifier = Modifier.weight(1f).testTag("release_lock_task_button")
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exit (PIN)", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // Section 3: Device Admin & Accessibility
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System Power & Display Off Authorization", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Required for physical display off lights & screen lock", color = Color(0xFF94A3B8), fontSize = 11.sp)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Device Admin (Complete Screen Off)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (isAdminActive) "✓ Granted (Turns off backlights completely)" else "Not enabled", color = if (isAdminActive) Color(0xFF10B981) else Color(0xFFEF4444), fontSize = 11.sp)
                        }
                        if (!isAdminActive && activity != null) {
                            Button(
                                onClick = { KioskDeviceAdminReceiver.requestDeviceAdmin(activity) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Grant", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Accessibility Service (App Guard & Lock)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (isAccessibilityActive) "✓ Active" else "Not enabled", color = if (isAccessibilityActive) Color(0xFF10B981) else Color(0xFFEF4444), fontSize = 11.sp)
                        }
                        if (!isAccessibilityActive) {
                            Button(
                                onClick = { KioskAccessibilityService.openAccessibilitySettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp)
                            }
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
    onSimulateCashNotification: () -> Unit
) {
    val context = LocalContext.current
    val isNotificationAccessGranted = remember {
        CashNotificationListenerService.isNotificationServiceEnabled(context)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("battery_power_tab"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Section 1: Real-Time Power Status
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
                                text = if (telemetry.isChargerConnected) "Screen kept ON automatically" else "Complete screen-off active on disconnect",
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

        // Section 2: Cash Payment Voice Announcement (Requirement: Read notifaction for cash recieve)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF10B981)),
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
                                    .background(Color(0xFF10B981).copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Cash Received Voice Reader", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Speaks: 'Cash received ₹500 from Rahul'", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }

                        SmoothToggleSwitch(
                            checked = settings.cashAnnouncementEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(cashAnnouncementEnabled = it)) },
                            activeColor = Color(0xFF10B981),
                            testTag = "cash_announce_toggle"
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Text-To-Speech (TTS) Voice", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Reads aloud when payment notification arrives", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }

                        SmoothToggleSwitch(
                            checked = settings.cashTtsEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(cashTtsEnabled = it)) },
                            activeColor = Color(0xFF10B981),
                            testTag = "cash_tts_toggle"
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSimulateCashNotification,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Voice Alert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (!isNotificationAccessGranted) {
                            Button(
                                onClick = { CashNotificationListenerService.openNotificationAccessSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                border = BorderStroke(1.dp, Color(0xFF34D399)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Allow Access", fontSize = 12.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Complete Screen Off When Unplugged (Requirement: Display off completely when unplugged, not black shade, complete off lights)
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
                            Text("Complete Display Off On Unplug", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Turns off display lights completely (100% hardware backlight off, not a black shade)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }

                        SmoothToggleSwitch(
                            checked = settings.batteryModeEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(batteryModeEnabled = it)) },
                            testTag = "battery_mode_toggle"
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Disconnect countdown delay before complete screen off:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 10, 15).forEach { sec ->
                            FilterChip(
                                selected = settings.disconnectDelaySec == sec,
                                onClick = { onUpdateSettings(settings.copy(disconnectDelaySec = sec)) },
                                label = { Text("${sec}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onTestStandby,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Complete Display Off Now", color = Color(0xFF38BDF8), fontSize = 12.sp)
                    }
                }
            }
        }

        // Section 4: 3-Press Power Wakeup on Battery
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

                        SmoothToggleSwitch(
                            checked = settings.power3TimesWakeupEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(power3TimesWakeupEnabled = it)) },
                            testTag = "power_3times_toggle"
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "• Normal single power button press is denied on battery to prevent accidental battery drain.\n• Pressing physical power button 3 times in quick succession immediately turns the display ON.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
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
