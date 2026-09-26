package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Requirement:
 * "Remove 3 power to emergecy. I said if not connected to chargeger but we need display press 3 time"
 * "Deney normal power butten wake up and two time screen tap wakeup."
 *
 * Screen Standby Behavior:
 * - When charger is disconnected, screen enters standby.
 * - Single power button wake is denied on battery mode.
 * - Waking requires pressing the display 3 times (Triple Tap) or double tap.
 * - Shows interactive visual feedback (3 dots) on every display press.
 */
@Composable
fun StandbyScreen(
    isChargerConnected: Boolean,
    batteryPercent: Int,
    onDoubleTapWake: () -> Unit,
    showWakeDeniedHint: Boolean = false
) {
    var currentTime by remember { mutableStateOf("") }
    var hintVisible by remember { mutableStateOf(showWakeDeniedHint) }
    var displayTapCount by remember { mutableIntStateOf(0) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    var resetJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(showWakeDeniedHint) {
        if (showWakeDeniedHint) {
            hintVisible = true
            delay(4000)
            hintVisible = false
        }
    }

    LaunchedEffect(Unit) {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTime = format.format(Date())
            delay(1000)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("standby_screen")
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                val now = System.currentTimeMillis()
                if (now - lastTapTimestamp > 1400) {
                    displayTapCount = 1
                } else {
                    displayTapCount += 1
                }
                lastTapTimestamp = now

                resetJob?.cancel()

                // Requirement: "we need display press 3 time" -> Wake up on 3 taps!
                // Also double tap wakes for convenience
                if (displayTapCount >= 3) {
                    displayTapCount = 0
                    onDoubleTapWake()
                } else {
                    hintVisible = true
                    resetJob = scope.launch {
                        delay(1400)
                        displayTapCount = 0
                    }
                }
            }
    ) {
        // OLED Minimal Standby Display
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(0.40f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime.ifEmpty { "12:00" },
                fontSize = 58.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isChargerConnected) Icons.Default.BatteryChargingFull else Icons.Default.PowerOff,
                    contentDescription = null,
                    tint = if (isChargerConnected) Color(0xFF10B981) else Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isChargerConnected) "Charging $batteryPercent%" else "Battery $batteryPercent% (Standby)",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 3 Dots Visual Indicator for Display Press 3 Times
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isLit = index < displayTapCount
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (isLit) Color(0xFF38BDF8) else Color(0xFF1E293B))
                            .border(
                                1.dp,
                                if (isLit) Color(0xFF38BDF8) else Color(0xFF475569),
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (displayTapCount > 0) "Tap display $displayTapCount/3 to wake" else "Press display 3 times to turn ON",
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Single power button wake is denied on battery",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }

        // Floating Toast when tapped or when power wake was denied
        AnimatedVisibility(
            visible = hintVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (displayTapCount > 0) "Display Tap: $displayTapCount of 3" else "Display Press 3 Times to Wake",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Power button wake is denied. Tap display 3 times or connect charger.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
