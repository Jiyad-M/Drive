package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun SmoothToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF0284C7),
    inactiveColor: Color = Color(0xFF334155),
    thumbColor: Color = Color.White,
    testTag: String = "smooth_toggle_switch"
) {
    val view = LocalView.current

    val trackColor by animateColorAsState(
        targetValue = if (checked) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 240),
        label = "track_color"
    )

    val borderColor by animateColorAsState(
        targetValue = if (checked) activeColor.copy(alpha = 0.8f) else Color(0xFF475569),
        animationSpec = tween(durationMillis = 240),
        label = "border_color"
    )

    // Smooth spring movement for the thumb
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumb_offset"
    )

    Box(
        modifier = modifier
            .testTag(testTag)
            .size(width = 56.dp, height = 48.dp) // Touch target adheres to 48dp guideline
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track Pill
        Box(
            modifier = Modifier
                .size(width = 50.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(trackColor)
                .border(1.2.dp, borderColor, RoundedCornerShape(14.dp))
        ) {
            // Inner track glow when checked
            if (checked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    activeColor.copy(alpha = 0.4f),
                                    Color.White.copy(alpha = 0.15f)
                                )
                            )
                        )
                )
            }
        }

        // Animated Round Thumb
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .shadow(elevation = if (checked) 4.dp else 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
                .border(0.5.dp, Color(0xFFE2E8F0), CircleShape)
        )
    }
}
