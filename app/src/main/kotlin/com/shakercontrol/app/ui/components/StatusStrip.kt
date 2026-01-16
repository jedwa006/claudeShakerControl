package com.shakercontrol.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
    modifier: Modifier = Modifier
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
            // Left: Menu + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(0.25f)
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu"
                    )
                }
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
                    rssiDbm = systemStatus.rssiDbm,
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
        secondaryText = if (connectionState.isConnected && rssiDbm != null) "RSSI: $rssiDbm dBm" else null,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        onClick = onClick
    )
}

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

    val secondaryText = when (heartbeatStatus) {
        HeartbeatStatus.OK -> "${ageMs} ms"
        HeartbeatStatus.STALE -> "Stale ${ageMs / 1000.0}s"
        HeartbeatStatus.MISSING -> "Missing"
    }

    StatusChip(
        label = "MCU: ${heartbeatStatus.displayName}",
        secondaryText = secondaryText,
        backgroundColor = backgroundColor,
        contentColor = contentColor
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
