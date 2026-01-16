package com.shakercontrol.app.ui.run

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shakercontrol.app.domain.model.*
import com.shakercontrol.app.ui.components.LedIndicator
import com.shakercontrol.app.ui.components.PidStatusLeds
import com.shakercontrol.app.ui.theme.MachineStateColors
import com.shakercontrol.app.ui.theme.SemanticColors
import com.shakercontrol.app.ui.theme.ShakerControlTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Recipe editor section.
 * Spec: docs/dashboard-sec-v1.md section 5.1 and docs/ui-copy-labels-v1.md section 3.2
 */
@Composable
fun RecipeSection(
    recipe: Recipe,
    runProgress: RunProgress?,
    isRunning: Boolean,
    onRecipeChange: (Recipe) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Recipe",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Input fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DurationInput(
                    label = "Milling (on)",
                    duration = recipe.millingDuration,
                    onDurationChange = { onRecipeChange(recipe.copy(millingDuration = it)) },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                )
                DurationInput(
                    label = "Hold (off)",
                    duration = recipe.holdDuration,
                    onDurationChange = { onRecipeChange(recipe.copy(holdDuration = it)) },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                )
                CycleInput(
                    cycles = recipe.cycleCount,
                    onCyclesChange = { onRecipeChange(recipe.copy(cycleCount = it)) },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Derived totals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Milling total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDurationLong(recipe.totalMillingTime), style = MaterialTheme.typography.bodyLarge)
                }
                Column {
                    Text("Hold total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDurationLong(recipe.totalHoldingTime), style = MaterialTheme.typography.bodyLarge)
                }
                Column {
                    Text("Estimated total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatDurationLong(recipe.totalRuntime),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Running status
            if (runProgress != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                RunProgressDisplay(runProgress = runProgress)
            }
        }
    }
}

@Composable
private fun DurationInput(
    label: String,
    duration: Duration,
    onDurationChange: (Duration) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var text by remember(duration) {
        mutableStateOf(formatDuration(duration))
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            parseDuration(newText)?.let { onDurationChange(it) }
        },
        label = { Text(label) },
        placeholder = { Text("mm:ss") },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun CycleInput(
    cycles: Int,
    onCyclesChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var text by remember(cycles) {
        mutableStateOf(cycles.toString())
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toIntOrNull()?.takeIf { it >= 1 }?.let { onCyclesChange(it) }
        },
        label = { Text("Cycles") },
        placeholder = { Text("1") },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun RunProgressDisplay(runProgress: RunProgress) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MachineStateColors.Running.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cycle ${runProgress.currentCycle} of ${runProgress.totalCycles}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MachineStateColors.Running.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = runProgress.currentPhase.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MachineStateColors.Running,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Phase remaining", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatDuration(runProgress.phaseRemaining),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total remaining", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatDurationLong(runProgress.totalRemaining),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Big control buttons section.
 * Spec: docs/dashboard-sec-v1.md section 5.1 B and docs/ui-copy-labels-v1.md section 3.3
 */
@Composable
fun ControlsSection(
    machineState: MachineState,
    connectionState: ConnectionState,
    isExecutingCommand: Boolean = false,
    startGating: StartGatingResult = StartGatingResult.OK,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use startGating for Start button, other buttons use simple state checks
    val canStart = startGating.canStart && !isExecutingCommand
    val canPause = machineState.canPause && !isExecutingCommand
    val canResume = machineState.canResume && !isExecutingCommand
    val canStop = machineState.canStop && !isExecutingCommand

    val disabledReason = when {
        isExecutingCommand -> "Sending command..."
        !startGating.canStart && !machineState.isOperating -> startGating.reason
        else -> null
    }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Controls",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (isExecutingCommand) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Start button (when not running)
                if (!machineState.isOperating) {
                    Button(
                        onClick = onStart,
                        enabled = canStart,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SemanticColors.Normal
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Pause button (when running)
                if (machineState == MachineState.RUNNING) {
                    Button(
                        onClick = onPause,
                        enabled = canPause,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SemanticColors.Warning
                        )
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pause", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Resume button (when paused)
                if (machineState == MachineState.PAUSED) {
                    Button(
                        onClick = onResume,
                        enabled = canResume,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SemanticColors.Normal
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Resume", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Stop button (when operating)
                if (machineState.isOperating) {
                    Button(
                        onClick = onStop,
                        enabled = canStop,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SemanticColors.Alarm
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Disabled reason
            if (disabledReason != null && !machineState.isOperating) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = disabledReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Commands are confirmed by the controller.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * PID tiles section on Run screen.
 */
@Composable
fun TemperaturesSection(
    pidData: List<PidData>,
    onNavigateToPid: (Int) -> Unit,
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
                PidTile(pid = pid, onClick = { onNavigateToPid(pid.controllerId) })
                if (pid != pidData.last()) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun PidTile(
    pid: PidData,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PID ${pid.controllerId} - ${pid.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("PV", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${String.format("%.1f", pid.processValue)}°C",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("SV", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${String.format("%.1f", pid.setpointValue)}°C",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            PidStatusLeds(
                isEnabled = pid.isEnabled,
                isOutputActive = pid.isOutputActive,
                hasFault = pid.hasFault,
                isStale = pid.isStale
            )
        }
    }
}

/**
 * Indicator bank section.
 */
@Composable
fun IndicatorsSection(
    interlockStatus: InterlockStatus,
    mcuHeartbeatStatus: HeartbeatStatus,
    mcuHeartbeatAgeMs: Long,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Indicators",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // System indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IndicatorItem("Door", interlockStatus.isDoorLocked)
                IndicatorItem("LN2", interlockStatus.isLn2Present)
                IndicatorItem("E-stop", !interlockStatus.isEStopActive, invertColor = true)
                IndicatorItem("Power", interlockStatus.isPowerEnabled)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IndicatorItem("Heater", interlockStatus.isHeatersEnabled)
                IndicatorItem("Motor", interlockStatus.isMotorEnabled)
                IndicatorItem("Fault", false)
                Spacer(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Health section
            Text(
                text = "Health",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HealthIndicator(
                    label = "BLE link",
                    status = if (connectionState == ConnectionState.LIVE) HeartbeatStatus.OK else HeartbeatStatus.MISSING
                )
                HealthIndicator(
                    label = "MCU heartbeat",
                    status = mcuHeartbeatStatus,
                    ageMs = mcuHeartbeatAgeMs
                )
            }
        }
    }
}

@Composable
private fun IndicatorItem(
    label: String,
    isOn: Boolean,
    invertColor: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LedIndicator(
            isOn = isOn,
            size = 16.dp,
            onColor = if (invertColor && !isOn) SemanticColors.Alarm else SemanticColors.OutputActive,
            offColor = if (invertColor) SemanticColors.Normal else SemanticColors.OutputInactive
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HealthIndicator(
    label: String,
    status: HeartbeatStatus,
    ageMs: Long? = null
) {
    val color = when (status) {
        HeartbeatStatus.OK -> SemanticColors.Normal
        HeartbeatStatus.STALE -> SemanticColors.Warning
        HeartbeatStatus.MISSING -> SemanticColors.Alarm
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LedIndicator(
            isOn = status != HeartbeatStatus.MISSING,
            size = 16.dp,
            onColor = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
        if (ageMs != null && status != HeartbeatStatus.MISSING) {
            Text(
                if (status == HeartbeatStatus.STALE) "Stale ${ageMs / 1000.0}s" else "${ageMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Manual controls section.
 */
@Composable
fun ManualControlsSection(
    isServiceMode: Boolean,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Manual controls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Placeholder controls
            ManualControlRow(label = "Lights", enabled = true)
            ManualControlRow(label = "Door lock", enabled = true)

            if (isServiceMode) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SERVICE",
                    style = MaterialTheme.typography.labelMedium,
                    color = SemanticColors.Warning
                )
                Spacer(modifier = Modifier.height(8.dp))
                ManualControlRow(label = "Heater override", enabled = true)
                ManualControlRow(label = "Motor override", enabled = true)
            }
        }
    }
}

@Composable
private fun ManualControlRow(
    label: String,
    enabled: Boolean
) {
    var isOn by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = isOn,
            onCheckedChange = { isOn = it },
            enabled = enabled
        )
    }
}

// Utility functions
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

private fun parseDuration(text: String): Duration? {
    val parts = text.split(":")
    return when (parts.size) {
        2 -> {
            val minutes = parts[0].toIntOrNull() ?: return null
            val seconds = parts[1].toIntOrNull() ?: return null
            if (seconds > 59) return null
            minutes.minutes + seconds.seconds
        }
        else -> null
    }
}

/**
 * I/O section with "blinken-lights" style LED indicators.
 * Shows DI and RO channels in compact LED format.
 * Clickable to navigate to I/O detail page.
 */
@Composable
fun IoSection(
    ioStatus: IoStatus,
    isSimulationEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "I/O",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSimulationEnabled) {
                        Surface(
                            color = SemanticColors.Warning.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "SIM",
                                style = MaterialTheme.typography.labelSmall,
                                color = SemanticColors.Warning,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open I/O details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Digital Inputs row
            Text(
                text = "Digital Inputs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                (1..8).forEach { channel ->
                    IoLedIndicator(
                        channel = channel,
                        isOn = ioStatus.isInputHigh(channel),
                        type = IoType.INPUT
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Relay Outputs row
            Text(
                text = "Relay Outputs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                (1..8).forEach { channel ->
                    IoLedIndicator(
                        channel = channel,
                        isOn = ioStatus.isOutputHigh(channel),
                        type = IoType.OUTPUT
                    )
                }
            }
        }
    }
}

/**
 * Type of I/O channel for styling.
 */
private enum class IoType {
    INPUT, OUTPUT
}

/**
 * Single I/O LED with channel number.
 */
@Composable
private fun IoLedIndicator(
    channel: Int,
    isOn: Boolean,
    type: IoType
) {
    val onColor = when (type) {
        IoType.INPUT -> SemanticColors.InputActive
        IoType.OUTPUT -> SemanticColors.OutputActive
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LedIndicator(
            isOn = isOn,
            size = 14.dp,
            onColor = onColor,
            isPulsing = isOn && type == IoType.OUTPUT
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = channel.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
private fun IoSectionPreview() {
    ShakerControlTheme {
        IoSection(
            ioStatus = IoStatus(
                digitalInputs = 0b00101101,  // Channels 1, 3, 4, 6 high
                relayOutputs = 0b00010010    // Channels 2, 5 high
            ),
            isSimulationEnabled = false,
            onClick = {}
        )
    }
}

@Preview
@Composable
private fun RecipeSectionPreview() {
    ShakerControlTheme {
        RecipeSection(
            recipe = Recipe.DEFAULT,
            runProgress = null,
            isRunning = false,
            onRecipeChange = {}
        )
    }
}

@Preview
@Composable
private fun ControlsSectionPreview() {
    ShakerControlTheme {
        ControlsSection(
            machineState = MachineState.READY,
            connectionState = ConnectionState.LIVE,
            startGating = StartGatingResult.OK,
            onStart = {},
            onPause = {},
            onResume = {},
            onStop = {}
        )
    }
}
