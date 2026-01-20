package com.shakercontrol.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shakercontrol.app.domain.model.*
import com.shakercontrol.app.ui.theme.*

/**
 * Global top status strip visible on all screens.
 * Spec: docs/dashboard-sec-v1.md section 3.
 */
@Composable
fun StatusStrip(
    screenTitle: String,
    systemStatus: SystemStatus,
    onMenuClick: () -> Unit,
    onConnectionClick: () -> Unit,
    onAlarmsClick: () -> Unit,
    modifier: Modifier = Modifier,
    canNavigateBack: Boolean = false,
    onBackClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Menu + Title (with optional back button integrated into menu area)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(0.25f)
            ) {
                // Combined menu/back button area - fixed width to prevent layout shift
                MenuBackButtonArea(
                    canNavigateBack = canNavigateBack,
                    onBackClick = onBackClick,
                    onMenuClick = onMenuClick
                )

                Text(
                    text = screenTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Center: Status chips
            Row(
                modifier = Modifier.weight(0.5f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionChip(
                    connectionState = systemStatus.connectionState,
                    rssiDbm = systemStatus.averageRssi,
                    signalQuality = systemStatus.signalQuality,
                    onClick = onConnectionClick
                )

                Spacer(modifier = Modifier.width(12.dp))

                McuHeartbeatChip(
                    heartbeatStatus = systemStatus.mcuHeartbeatStatus,
                    ageMs = systemStatus.mcuHeartbeatAgeMs
                )

                Spacer(modifier = Modifier.width(12.dp))

                MachineStateChip(
                    machineState = systemStatus.machineState
                )

                // Show session lease warning chip when not OK
                if (systemStatus.sessionLeaseStatus != SessionLeaseStatus.OK &&
                    systemStatus.sessionLeaseStatus != SessionLeaseStatus.NO_SESSION) {
                    Spacer(modifier = Modifier.width(12.dp))
                    SessionLeaseChip(
                        sessionLeaseStatus = systemStatus.sessionLeaseStatus,
                        leaseAgeMs = systemStatus.sessionLeaseAgeMs,
                        leaseMs = systemStatus.sessionLeaseMs
                    )
                }
            }

            // Right: Alarms + Service mode
            Row(
                modifier = Modifier.weight(0.25f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlarmChip(
                    alarmSummary = systemStatus.alarmSummary,
                    onClick = onAlarmsClick
                )

                if (systemStatus.isServiceModeEnabled) {
                    Spacer(modifier = Modifier.width(12.dp))
                    ServiceModeChip()
                }
            }
        }
    }
}

@Composable
private fun ConnectionChip(
    connectionState: ConnectionState,
    rssiDbm: Int?,
    signalQuality: SignalQuality,
    onClick: () -> Unit
) {
    val backgroundColor = when (connectionState) {
        ConnectionState.LIVE -> ConnectionStateColors.Verified
        ConnectionState.DEGRADED -> SemanticColors.Warning
        ConnectionState.ERROR -> SemanticColors.Alarm
        ConnectionState.DISCONNECTED -> ConnectionStateColors.Disconnected
        else -> ConnectionStateColors.Connecting
    }.copy(alpha = 0.2f)

    val contentColor = when (connectionState) {
        ConnectionState.LIVE -> ConnectionStateColors.Verified
        ConnectionState.DEGRADED -> SemanticColors.Warning
        ConnectionState.ERROR -> SemanticColors.Alarm
        ConnectionState.DISCONNECTED -> ConnectionStateColors.Disconnected
        else -> ConnectionStateColors.Connecting
    }

    StatusChip(
        label = connectionState.displayName,
        secondaryText = if (connectionState.isConnected && rssiDbm != null) "${signalQuality.displayName} ($rssiDbm dBm)" else null,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        onClick = onClick,
        icon = if (connectionState.isConnected) {
            { SignalBars(bars = signalQuality.bars, color = contentColor) }
        } else null
    )
}

/**
 * Combined menu/back button area with fixed width to prevent layout shift.
 * Shows either the menu icon (on top-level pages) or back arrow (on sub-pages).
 * Both icons use the same 48dp space to prevent title text from shifting.
 */
@Composable
private fun MenuBackButtonArea(
    canNavigateBack: Boolean,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    // Fixed width container - always 48dp, preventing layout shift
    Box(modifier = Modifier.size(48.dp)) {
        Crossfade(
            targetState = canNavigateBack,
            animationSpec = tween(200),
            label = "menuBackCrossfade"
        ) { showBack ->
            if (showBack) {
                BackButton(onClick = onBackClick)
            } else {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu"
                    )
                }
            }
        }
    }
}

/**
 * Back button with subtle pulse animation on first appearance.
 * The pulse draws attention without being intrusive.
 */
@Composable
private fun BackButton(
    onClick: () -> Unit
) {
    // Subtle pulse animation when button appears
    val infiniteTransition = rememberInfiniteTransition(label = "backButtonPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // After 2 pulses (~3.2 seconds), stop the animation and stay at full opacity
    var showPulse by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3200)
        showPulse = false
    }

    val displayAlpha = if (showPulse) alpha else 1f

    Box(
        modifier = Modifier
            .size(48.dp)
            .padding(4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = displayAlpha * 0.15f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Go back",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = displayAlpha),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Signal strength bars indicator.
 */
@Composable
private fun SignalBars(
    bars: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(6.dp, 9.dp, 12.dp, 15.dp)
        heights.forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (index < bars) color else color.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

/**
 * MCU heartbeat chip with pulsing animation.
 * Shows a beating heart icon when healthy to indicate real-time communication.
 */
@Composable
private fun McuHeartbeatChip(
    heartbeatStatus: HeartbeatStatus,
    ageMs: Long
) {
    val backgroundColor = when (heartbeatStatus) {
        HeartbeatStatus.OK -> SemanticColors.Normal.copy(alpha = 0.2f)
        HeartbeatStatus.STALE -> SemanticColors.Warning.copy(alpha = 0.2f)
        HeartbeatStatus.MISSING -> SemanticColors.Alarm.copy(alpha = 0.2f)
    }

    val contentColor = when (heartbeatStatus) {
        HeartbeatStatus.OK -> SemanticColors.Normal
        HeartbeatStatus.STALE -> SemanticColors.Warning
        HeartbeatStatus.MISSING -> SemanticColors.Alarm
    }

    // Pulsing animation for the heartbeat dot - mimics actual heartbeat rhythm
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (heartbeatStatus == HeartbeatStatus.OK) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0 using FastOutSlowInEasing
                1.3f at 100 using FastOutSlowInEasing
                1f at 200 using FastOutSlowInEasing
                1.2f at 300 using FastOutSlowInEasing
                1f at 400 using FastOutSlowInEasing
                1f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (heartbeatStatus == HeartbeatStatus.OK) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                0.6f at 100
                1f at 200
                0.7f at 300
                1f at 400
                1f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    StatusChip(
        label = "MCU",
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        icon = {
            Box(
                modifier = Modifier
                    .size((10 * pulseScale).dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = pulseAlpha))
            )
        }
    )
}

@Composable
private fun SessionLeaseChip(
    sessionLeaseStatus: SessionLeaseStatus,
    leaseAgeMs: Long,
    leaseMs: Int
) {
    val backgroundColor = when (sessionLeaseStatus) {
        SessionLeaseStatus.EXPIRED -> SemanticColors.Critical.copy(alpha = 0.3f)
        SessionLeaseStatus.WARNING -> SemanticColors.Warning.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (sessionLeaseStatus) {
        SessionLeaseStatus.EXPIRED -> SemanticColors.Critical
        SessionLeaseStatus.WARNING -> SemanticColors.Warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val label = when (sessionLeaseStatus) {
        SessionLeaseStatus.EXPIRED -> "Session: Expired"
        SessionLeaseStatus.WARNING -> "Session: Stale"
        else -> "Session: ${sessionLeaseStatus.displayName}"
    }

    val percentage = ((leaseAgeMs.toFloat() / leaseMs) * 100).toInt().coerceAtMost(999)
    val secondaryText = if (sessionLeaseStatus == SessionLeaseStatus.EXPIRED) {
        "Reconnecting..."
    } else {
        "$percentage% of lease"
    }

    StatusChip(
        label = label,
        secondaryText = secondaryText,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
        }
    )
}

@Composable
private fun MachineStateChip(machineState: MachineState) {
    val backgroundColor = when (machineState) {
        MachineState.IDLE -> MachineStateColors.Idle.copy(alpha = 0.2f)
        MachineState.READY -> MachineStateColors.Ready.copy(alpha = 0.2f)
        MachineState.RUNNING -> MachineStateColors.Running.copy(alpha = 0.2f)
        MachineState.PAUSED -> MachineStateColors.Paused.copy(alpha = 0.2f)
        MachineState.FAULT -> MachineStateColors.Fault.copy(alpha = 0.2f)
        MachineState.E_STOP -> MachineStateColors.EStop.copy(alpha = 0.3f)
    }

    val contentColor = when (machineState) {
        MachineState.IDLE -> MachineStateColors.Idle
        MachineState.READY -> MachineStateColors.Ready
        MachineState.RUNNING -> MachineStateColors.Running
        MachineState.PAUSED -> MachineStateColors.Paused
        MachineState.FAULT -> MachineStateColors.Fault
        MachineState.E_STOP -> MachineStateColors.EStop
    }

    StatusChip(
        label = machineState.displayName,
        backgroundColor = backgroundColor,
        contentColor = contentColor
    )
}

@Composable
private fun AlarmChip(
    alarmSummary: AlarmSummary,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        alarmSummary.highestSeverity == AlarmSeverity.CRITICAL -> SemanticColors.Critical.copy(alpha = 0.3f)
        alarmSummary.highestSeverity == AlarmSeverity.ALARM -> SemanticColors.Alarm.copy(alpha = 0.2f)
        alarmSummary.highestSeverity == AlarmSeverity.WARNING -> SemanticColors.Warning.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        alarmSummary.highestSeverity == AlarmSeverity.CRITICAL -> SemanticColors.Critical
        alarmSummary.highestSeverity == AlarmSeverity.ALARM -> SemanticColors.Alarm
        alarmSummary.highestSeverity == AlarmSeverity.WARNING -> SemanticColors.Warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val label = if (alarmSummary.highCount > 0) {
        "Alarms: ${alarmSummary.totalCount} (${alarmSummary.highCount} high)"
    } else {
        "Alarms: ${alarmSummary.totalCount}"
    }

    StatusChip(
        label = label,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        onClick = onClick
    )
}

@Composable
private fun ServiceModeChip() {
    StatusChip(
        label = "Service: on",
        backgroundColor = SemanticColors.Warning.copy(alpha = 0.2f),
        contentColor = SemanticColors.Warning,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
private fun StatusChip(
    label: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    icon: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val chipModifier = modifier
        .clip(RoundedCornerShape(8.dp))
        .background(backgroundColor)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 12.dp, vertical = 6.dp)

    Row(
        modifier = chipModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
            if (secondaryText != null) {
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Preview(widthDp = 1200)
@Composable
private fun StatusStripPreview() {
    ShakerControlTheme {
        StatusStrip(
            screenTitle = "Home",
            systemStatus = SystemStatus(
                connectionState = ConnectionState.LIVE,
                machineState = MachineState.READY,
                mcuHeartbeatAgeMs = 120,
                bleHeartbeatAgeMs = 80,
                alarmSummary = AlarmSummary(totalCount = 2, highCount = 1, highestSeverity = AlarmSeverity.ALARM),
                isServiceModeEnabled = false,
                deviceName = "SYS-CTRL-001",
                rssiDbm = -58,
                firmwareVersion = "1.0.0",
                protocolVersion = 1
            ),
            onMenuClick = {},
            onConnectionClick = {},
            onAlarmsClick = {}
        )
    }
}
