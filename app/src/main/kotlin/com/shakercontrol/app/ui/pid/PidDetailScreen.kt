package com.shakercontrol.app.ui.pid

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shakercontrol.app.domain.model.CapabilityLevel
import com.shakercontrol.app.domain.model.PidData
import com.shakercontrol.app.domain.model.PidMode
import com.shakercontrol.app.ui.components.PidStatusLeds
import com.shakercontrol.app.ui.theme.ShakerControlTheme

/**
 * PID detail page.
 * Spec: docs/dashboard-sec-v1.md section 7.
 *
 * Placeholder for Stage 1 - deeper functionality in Stage 4.
 */
@Composable
fun PidDetailScreen(
    pidId: Int,
    onNavigateBack: () -> Unit,
    viewModel: PidDetailViewModel = hiltViewModel()
) {
    val pidData by viewModel.getPidData(pidId).collectAsStateWithLifecycle(initialValue = null)

    PidDetailContent(
        pidId = pidId,
        pidData = pidData
    )
}

@Composable
private fun PidDetailContent(
    pidId: Int,
    pidData: PidData?
) {
    val pidNames = mapOf(
        1 to "Axle bearings",
        2 to "Orbital bearings",
        3 to "LN2 line"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PID $pidId - ${pidNames[pidId] ?: "Unknown"}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                if (pidData != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Process Value (PV)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${String.format("%.1f", pidData.processValue)}°C",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text("Setpoint Value (SV)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${String.format("%.1f", pidData.setpointValue)}°C",
                                style = MaterialTheme.typography.displaySmall
                            )
                        }
                        Column {
                            Text("Output (%)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${String.format("%.1f", pidData.outputPercent)}%",
                                style = MaterialTheme.typography.displaySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(pidData.mode.displayName, style = MaterialTheme.typography.titleMedium)
                        }
                        Column {
                            Text("Capability", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(pidData.capabilityLevel.displayName, style = MaterialTheme.typography.titleMedium)
                        }
                        Column {
                            Text("Data Age", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${pidData.ageMs} ms", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    PidStatusLeds(
                        isEnabled = pidData.isEnabled,
                        isOutputActive = pidData.isOutputActive,
                        hasFault = pidData.hasFault,
                        isStale = pidData.isStale
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No data available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Setpoint control placeholder
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Setpoint control",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Setpoint editing coming in Stage 4",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Mode control placeholder
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mode control",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Mode selection coming in Stage 4",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Modbus register map placeholder
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Modbus register map",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Register map coming in future release",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun PidDetailScreenPreview() {
    ShakerControlTheme {
        Surface {
            PidDetailContent(
                pidId = 1,
                pidData = PidData(
                    controllerId = 1,
                    name = "Axle bearings",
                    processValue = 25.4f,
                    setpointValue = 30.0f,
                    outputPercent = 45.6f,
                    mode = PidMode.AUTO,
                    isEnabled = true,
                    isOutputActive = true,
                    hasFault = false,
                    ageMs = 120,
                    capabilityLevel = CapabilityLevel.REQUIRED
                )
            )
        }
    }
}
