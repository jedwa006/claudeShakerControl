package com.shakercontrol.app.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shakercontrol.app.domain.model.*
import com.shakercontrol.app.ui.theme.ShakerControlTheme
import com.shakercontrol.app.ui.theme.StatusActive
import com.shakercontrol.app.ui.theme.StatusAlarm
import com.shakercontrol.app.ui.theme.StatusWarning

/**
 * Diagnostics screen.
 * Spec: docs/dashboard-sec-v1.md section 10 and docs/ui-copy-labels-v1.md section 7.
 */
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val systemStatus by viewModel.systemStatus.collectAsStateWithLifecycle()
    val pidData by viewModel.pidData.collectAsStateWithLifecycle()
    val isServiceMode by viewModel.isServiceMode.collectAsStateWithLifecycle()
    val hasOverrides by viewModel.hasCapabilityOverrides.collectAsStateWithLifecycle()

    DiagnosticsContent(
        systemStatus = systemStatus,
        pidData = pidData,
        isServiceMode = isServiceMode,
        hasCapabilityOverrides = hasOverrides,
        onCapabilityChange = { subsystem, level -> viewModel.setCapabilityOverride(subsystem, level) },
        onClearOverrides = { viewModel.clearCapabilityOverrides() }
    )
}

@Composable
private fun DiagnosticsContent(
    systemStatus: SystemStatus,
    pidData: List<PidData> = emptyList(),
    isServiceMode: Boolean = false,
    hasCapabilityOverrides: Boolean = false,
    onCapabilityChange: (String, CapabilityLevel) -> Unit = { _, _ -> },
    onClearOverrides: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Connection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                DiagnosticRow("Connection state", systemStatus.connectionState.displayName)
                DiagnosticRow("Device name", systemStatus.deviceName ?: "Not connected")
                DiagnosticRow("RSSI", systemStatus.rssiDbm?.let { "$it dBm" } ?: "N/A")
            }
        }

        // Heartbeats section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Heartbeats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                DiagnosticRow("BLE link age", "${systemStatus.bleHeartbeatAgeMs} ms")
                DiagnosticRow("MCU heartbeat age", "${systemStatus.mcuHeartbeatAgeMs} ms")
                DiagnosticRow("MCU status", systemStatus.mcuHeartbeatStatus.displayName)
            }
        }

        // Firmware section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Firmware",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                DiagnosticRow("Firmware version", systemStatus.fullFirmwareVersion ?: "Unknown")
                DiagnosticRow("Build ID", systemStatus.firmwareBuildId ?: "Unknown")
                DiagnosticRow("Protocol version", systemStatus.protocolVersion?.toString() ?: "Unknown")
            }
        }

        // PID Controllers section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PID Controllers (RS-485)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (pidData.isEmpty()) {
                    Text(
                        text = "No controllers detected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    pidData.forEach { pid ->
                        PidControllerStatusRow(pid = pid)
                    }
                }
            }
        }

        // Capabilities section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subsystem Capabilities",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasCapabilityOverrides) {
                            Surface(
                                color = StatusWarning.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "OVERRIDE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusWarning,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (isServiceMode) {
                            Text(
                                text = "Tap to edit",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusWarning
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                val caps = systemStatus.capabilities
                CapabilityRow("PID 1 (LN2 Cold)", "pid1", caps.pid1, isServiceMode, onCapabilityChange)
                CapabilityRow("PID 2 (Axle)", "pid2", caps.pid2, isServiceMode, onCapabilityChange)
                CapabilityRow("PID 3 (Orbital)", "pid3", caps.pid3, isServiceMode, onCapabilityChange)
                CapabilityRow("LN2 Valve", "ln2Valve", caps.ln2Valve, isServiceMode, onCapabilityChange)
                CapabilityRow("Door Actuator", "doorActuator", caps.doorActuator, isServiceMode, onCapabilityChange)
                CapabilityRow("Door Switch", "doorSwitch", caps.doorSwitch, isServiceMode, onCapabilityChange)
                CapabilityRow("Internal Relays", "relayInternal", caps.relayInternal, isServiceMode, onCapabilityChange)
                CapabilityRow("External Relays (RS-485)", "relayExternal", caps.relayExternal, isServiceMode, onCapabilityChange)

                // Reset button
                if (hasCapabilityOverrides && isServiceMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onClearOverrides,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset to Defaults")
                    }
                }
            }
        }
    }
}

@Composable
private fun PidControllerStatusRow(pid: PidData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status indicator
            val statusColor = when {
                pid.isOffline -> StatusAlarm
                pid.isStale -> StatusWarning
                pid.hasFault -> StatusAlarm
                else -> StatusActive
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Column {
                Text(
                    text = pid.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "ID: ${pid.controllerId} • Age: ${pid.ageMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Status text
        val statusText = when {
            pid.isOffline -> "Offline"
            pid.isStale -> "Stale"
            pid.hasFault -> "Fault"
            else -> "Online"
        }
        val statusTextColor = when {
            pid.isOffline -> StatusAlarm
            pid.isStale -> StatusWarning
            pid.hasFault -> StatusAlarm
            else -> StatusActive
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = statusTextColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CapabilityRow(
    name: String,
    subsystemKey: String,
    level: CapabilityLevel,
    isServiceMode: Boolean,
    onCapabilityChange: (String, CapabilityLevel) -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box {
            // Level indicator chip (clickable in service mode)
            val (chipColor, textColor) = when (level) {
                CapabilityLevel.REQUIRED -> StatusActive to Color.Black
                CapabilityLevel.OPTIONAL -> StatusWarning to Color.Black
                CapabilityLevel.SIMULATED -> MaterialTheme.colorScheme.tertiary to Color.White
                CapabilityLevel.NOT_PRESENT -> Color.DarkGray to Color.Gray
            }

            Surface(
                onClick = { if (isServiceMode) showDropdown = true },
                enabled = isServiceMode,
                shape = MaterialTheme.shapes.small,
                color = chipColor
            ) {
                Text(
                    text = level.displayName,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
            }

            // Dropdown menu for editing
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }
            ) {
                CapabilityLevel.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            onCapabilityChange(subsystemKey, option)
                            showDropdown = false
                        },
                        leadingIcon = {
                            val iconColor = when (option) {
                                CapabilityLevel.REQUIRED -> StatusActive
                                CapabilityLevel.OPTIONAL -> StatusWarning
                                CapabilityLevel.SIMULATED -> MaterialTheme.colorScheme.tertiary
                                CapabilityLevel.NOT_PRESENT -> Color.Gray
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(iconColor)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(widthDp = 800, heightDp = 800)
@Composable
private fun DiagnosticsScreenPreview() {
    ShakerControlTheme {
        Surface {
            DiagnosticsContent(
                systemStatus = SystemStatus(
                    connectionState = ConnectionState.LIVE,
                    machineState = MachineState.READY,
                    mcuHeartbeatAgeMs = 120,
                    bleHeartbeatAgeMs = 80,
                    alarmSummary = AlarmSummary(0, 0, null),
                    isServiceModeEnabled = true,
                    deviceName = "SYS-CTRL-D776",
                    rssiDbm = -58,
                    firmwareVersion = "0.2.0",
                    firmwareBuildId = "26011901",
                    protocolVersion = 1
                ),
                pidData = listOf(
                    PidData(
                        controllerId = 1,
                        name = "LN2 (Cold)",
                        processValue = -120.5f,
                        setpointValue = -130.0f,
                        outputPercent = 78.2f,
                        mode = PidMode.AUTO,
                        isEnabled = true,
                        isOutputActive = true,
                        hasFault = false,
                        ageMs = 150,
                        capabilityLevel = CapabilityLevel.OPTIONAL
                    ),
                    PidData(
                        controllerId = 2,
                        name = "Axle Bearings",
                        processValue = 25.3f,
                        setpointValue = 30.0f,
                        outputPercent = 45.6f,
                        mode = PidMode.AUTO,
                        isEnabled = true,
                        isOutputActive = true,
                        hasFault = false,
                        ageMs = 120,
                        capabilityLevel = CapabilityLevel.REQUIRED
                    ),
                    PidData(
                        controllerId = 3,
                        name = "Orbital Bearings",
                        processValue = 28.1f,
                        setpointValue = 30.0f,
                        outputPercent = 32.1f,
                        mode = PidMode.AUTO,
                        isEnabled = true,
                        isOutputActive = true,
                        hasFault = false,
                        ageMs = 120,
                        capabilityLevel = CapabilityLevel.REQUIRED
                    )
                ),
                isServiceMode = true
            )
        }
    }
}
