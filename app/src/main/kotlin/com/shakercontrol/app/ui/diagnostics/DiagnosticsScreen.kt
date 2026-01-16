package com.shakercontrol.app.ui.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shakercontrol.app.domain.model.*
import com.shakercontrol.app.ui.theme.ShakerControlTheme

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

    DiagnosticsContent(systemStatus = systemStatus)
}

@Composable
private fun DiagnosticsContent(systemStatus: SystemStatus) {
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

                DiagnosticRow("Firmware version", systemStatus.firmwareVersion ?: "Unknown")
                DiagnosticRow("Protocol version", systemStatus.protocolVersion?.toString() ?: "Unknown")
            }
        }

        // Capabilities section (placeholder)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Capabilities",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Capability bits table coming in Stage 5",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Inputs section (placeholder)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Inputs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Digital input states coming in Stage 2",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

@Preview(widthDp = 800, heightDp = 600)
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
                    isServiceModeEnabled = false,
                    deviceName = "SYS-CTRL-001",
                    rssiDbm = -58,
                    firmwareVersion = "1.0.0",
                    protocolVersion = 1
                )
            )
        }
    }
}
