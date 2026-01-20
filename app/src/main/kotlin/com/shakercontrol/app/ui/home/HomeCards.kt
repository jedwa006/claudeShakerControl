package com.shakercontrol.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shakercontrol.app.domain.model.*
import com.shakercontrol.app.ui.components.LedIndicator
import com.shakercontrol.app.ui.components.PidStatusLeds
import com.shakercontrol.app.ui.theme.MachineStateColors
import com.shakercontrol.app.ui.theme.SemanticColors
import com.shakercontrol.app.ui.theme.ShakerControlTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Primary Run/Live Session card.
 * Spec: docs/dashboard-sec-v1.md section 4.1 and docs/ui-copy-labels-v1.md section 2.1
 */
@Composable
fun RunCard(
    machineState: MachineState,
    connectionState: ConnectionState,
    recipe: Recipe,
    runProgress: RunProgress?,
    onNavigateToRun: () -> Unit,
    onNavigateToDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOperating = machineState.isOperating
    val title = if (isOperating) "Live session" else "Run"
    val isDisconnected = connectionState == ConnectionState.DISCONNECTED

    val subtitle = when {
        isDisconnected -> "Not connected - connect to start"
        connectionState != ConnectionState.LIVE && connectionState != ConnectionState.DEGRADED ->
            "Connected - verify to start"
        machineState == MachineState.RUNNING && runProgress != null ->
            "Running - cycle ${runProgress.currentCycle} of ${runProgress.totalCycles} (${runProgress.currentPhase.displayName.lowercase()})"
        machineState == MachineState.PAUSED -> "Paused - tap to resume"
        machineState == MachineState.READY -> "Ready - configure and start"
        else -> "Idle"
    }

    val buttonText = when {
        isDisconnected -> "Connect"
        connectionState != ConnectionState.LIVE && connectionState != ConnectionState.DEGRADED -> "Verify connection"
        isOperating -> "Resume live session"
        else -> "Open run"
    }

    val buttonColor = when {
        isOperating -> MachineStateColors.Running
        machineState == MachineState.READY -> SemanticColors.Normal
        else -> MaterialTheme.colorScheme.primary
    }

    // Navigate to Devices when disconnected, Run otherwise
    val onButtonClick = if (isDisconnected) onNavigateToDevices else onNavigateToRun

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isOperating)
                MachineStateColors.Running.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Recipe summary
                if (connectionState.isConnected || connectionState == ConnectionState.LIVE) {
                    RecipeSummary(recipe = recipe)
                }

                // Run progress if active
                if (runProgress != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    RunProgressSummary(runProgress = runProgress)
                }
            }

            Button(
                onClick = onButtonClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun RecipeSummary(recipe: Recipe) {
    Column {
        Text(
            text = "Recipe",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${formatDuration(recipe.millingDuration)} on / ${formatDuration(recipe.holdDuration)} hold × ${recipe.cycleCount}",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Est. total: ${formatDurationLong(recipe.totalRuntime)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RunProgressSummary(runProgress: RunProgress) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MachineStateColors.Running.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Cycle ${runProgress.currentCycle} of ${runProgress.totalCycles}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = runProgress.currentPhase.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MachineStateColors.Running
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Phase remaining: ${formatDuration(runProgress.phaseRemaining)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Total remaining: ${formatDurationLong(runProgress.totalRemaining)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Temperatures card showing PID summaries.
 * Spec: docs/dashboard-sec-v1.md section 4.1 and docs/ui-copy-labels-v1.md section 2.2
 */
@Composable
fun TemperaturesCard(
    pidData: List<PidData>,
    onPidClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Temperatures",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            pidData.forEach { pid ->
                PidSummaryRow(
                    pid = pid,
                    onClick = { onPidClick(pid.controllerId) }
                )
                if (pid != pidData.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            if (pidData.isEmpty()) {
                Text(
                    text = "No PID data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PidSummaryRow(
    pid: PidData,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PID ${pid.controllerId} - ${pid.name}",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "PV: ${String.format("%.1f", pid.processValue)}°C",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "SV: ${String.format("%.1f", pid.setpointValue)}°C",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            PidStatusLeds(
                isEnabled = pid.isEnabled,
                isOutputActive = pid.isOutputActive,
                hasFault = pid.hasFault,
                isStale = pid.isStale
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open PID details",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Status/Interlocks card.
 * Spec: docs/dashboard-sec-v1.md section 4.1 and docs/ui-copy-labels-v1.md section 2.3
 */
@Composable
fun StatusCard(
    interlockStatus: InterlockStatus,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            StatusRow("E-stop", !interlockStatus.isEStopActive, invertColor = true)
            StatusRow("Door locked", interlockStatus.isDoorLocked)
            StatusRow("LN2 present", interlockStatus.isLn2Present)
            StatusRow("Power enabled", interlockStatus.isPowerEnabled)
            StatusRow("Heaters enabled", interlockStatus.isHeatersEnabled)
            StatusRow("Motor enabled", interlockStatus.isMotorEnabled)
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    isOk: Boolean,
    invertColor: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        LedIndicator(
            isOn = isOk,
            onColor = if (invertColor && !isOk) SemanticColors.Alarm else SemanticColors.Normal,
            offColor = if (invertColor) SemanticColors.Normal else SemanticColors.Stale
        )
    }
}

/**
 * Diagnostics card.
 */
@Composable
fun DiagnosticsCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "View connection health, inputs, capabilities, and firmware info.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open diagnostics"
            )
        }
    }
}

/**
 * Settings card.
 */
@Composable
fun SettingsCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "App settings and export.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open settings"
            )
        }
    }
}

// Utility functions for duration formatting
private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun formatDurationLong(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Preview
@Composable
private fun TemperaturesCardPreview() {
    ShakerControlTheme {
        TemperaturesCard(
            pidData = listOf(
                PidData(1, "LN2 (Cold)", -180.5f, -185.0f, 0.0f, PidMode.AUTO, true, false, false, 3500, CapabilityLevel.OPTIONAL),
                PidData(2, "Axle bearings", 25.4f, 30.0f, 45.6f, PidMode.AUTO, true, true, false, 120, CapabilityLevel.REQUIRED),
                PidData(3, "Orbital bearings", 28.1f, 30.0f, 32.1f, PidMode.AUTO, true, false, false, 120, CapabilityLevel.REQUIRED)
            ),
            onPidClick = {}
        )
    }
}

@Preview
@Composable
private fun StatusCardPreview() {
    ShakerControlTheme {
        StatusCard(
            interlockStatus = InterlockStatus(
                isEStopActive = false,
                isDoorLocked = true,
                isLn2Present = true,
                isPowerEnabled = true,
                isHeatersEnabled = false,
                isMotorEnabled = false
            )
        )
    }
}
