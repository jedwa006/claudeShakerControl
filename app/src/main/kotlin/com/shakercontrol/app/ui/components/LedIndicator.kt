package com.shakercontrol.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shakercontrol.app.ui.theme.SemanticColors
import com.shakercontrol.app.ui.theme.ShakerControlTheme

/**
 * LED indicator component for status display.
 * Supports on/off/stale states and optional pulsing for active outputs.
 *
 * Spec: docs/dashboard-sec-v1.md section 1.4 (Motion - subtle pulsing for "output active")
 */
@Composable
fun LedIndicator(
    isOn: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 12.dp,
    onColor: Color = SemanticColors.OutputActive,
    offColor: Color = SemanticColors.OutputInactive,
    isPulsing: Boolean = false,
    isStale: Boolean = false
) {
    val displayColor = when {
        isStale -> SemanticColors.Stale
        isOn -> onColor
        else -> offColor
    }

    val alpha by if (isPulsing && isOn && !isStale) {
        rememberInfiniteTransition(label = "led_pulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "led_alpha"
        )
    } else {
        rememberInfiniteTransition(label = "led_static").animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Restart
            ),
            label = "led_alpha_static"
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(displayColor.copy(alpha = alpha))
                .border(1.dp, displayColor.copy(alpha = 0.5f), CircleShape)
        )

        if (label != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isStale) SemanticColors.Stale else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Compact LED row for PID tiles showing Enabled/Output/Fault/Alarm status.
 */
@Composable
fun PidStatusLeds(
    isEnabled: Boolean,
    isOutputActive: Boolean,
    hasFault: Boolean,
    isStale: Boolean = false,
    al1Active: Boolean = false,
    al2Active: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LedIndicator(
            isOn = isEnabled,
            label = "Enabled",
            onColor = SemanticColors.Enabled,
            isStale = isStale
        )
        LedIndicator(
            isOn = isOutputActive,
            label = "Output",
            onColor = SemanticColors.OutputActive,
            isPulsing = true,
            isStale = isStale
        )
        LedIndicator(
            isOn = hasFault,
            label = "Fault",
            onColor = SemanticColors.Fault,
            isStale = isStale
        )
        // AL1/AL2 alarm relays from PID controller
        if (al1Active || al2Active) {
            LedIndicator(
                isOn = al1Active,
                label = "AL1",
                onColor = SemanticColors.Alarm,
                isPulsing = al1Active,
                isStale = isStale
            )
            LedIndicator(
                isOn = al2Active,
                label = "AL2",
                onColor = SemanticColors.Alarm,
                isPulsing = al2Active,
                isStale = isStale
            )
        }
    }
}

@Preview
@Composable
private fun LedIndicatorPreview() {
    ShakerControlTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LedIndicator(isOn = true, label = "Active", isPulsing = true)
            LedIndicator(isOn = true, label = "Enabled", onColor = SemanticColors.Enabled)
            LedIndicator(isOn = false, label = "Off")
            LedIndicator(isOn = true, label = "Stale", isStale = true)
            LedIndicator(isOn = true, label = "Fault", onColor = SemanticColors.Fault)
        }
    }
}

@Preview
@Composable
private fun PidStatusLedsPreview() {
    ShakerControlTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PidStatusLeds(isEnabled = true, isOutputActive = true, hasFault = false)
            PidStatusLeds(isEnabled = true, isOutputActive = false, hasFault = false)
            PidStatusLeds(isEnabled = true, isOutputActive = true, hasFault = true)
            PidStatusLeds(isEnabled = false, isOutputActive = false, hasFault = false, isStale = true)
        }
    }
}
