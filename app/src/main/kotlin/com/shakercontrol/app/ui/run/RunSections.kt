package com.shakercontrol.app.ui.run

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
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
 * Compact PID tiles section with responsive grid layout.
 * Shows 1/2/3 columns based on controller count for optimal space usage.
 * Dynamically shows only controllers that are connected via RS-485.
 */
@Composable
fun TemperaturesSection(
    pidData: List<PidData>,
    onNavigateToPid: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Filter to only show controllers that have capability level > NOT_PRESENT
    val visibleControllers = pidData.filter { it.capabilityLevel != CapabilityLevel.NOT_PRESENT }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Temperatures",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (visibleControllers.isNotEmpty()) {
                Text(
                    text = "${visibleControllers.size} PID${if (visibleControllers.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (visibleControllers.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No temperature controllers connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            // Responsive grid: tiles side-by-side based on count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleControllers.forEach { pid ->
                    CompactPidTile(
                        pid = pid,
                        onClick = { onNavigateToPid(pid.controllerId) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Compact stamp-style PID tile for grid layout.
 * Shows essential info: name, PV/SV, and compact status indicators.
 */
@Composable
private fun CompactPidTile(
    pid: PidData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulsing border for error states
    val hasError = pid.isOffline || pid.hasProbeError
    val hasWarning = pid.isStale && !hasError

    val infiniteTransition = rememberInfiniteTransition(label = "error_pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = if (hasError) 1f else 0.5f,
        targetValue = if (hasError) 0.3f else 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_alpha"
    )

    val borderColor = when {
        hasError -> SemanticColors.Alarm.copy(alpha = borderAlpha)
        hasWarning -> SemanticColors.Warning.copy(alpha = 0.7f)
        else -> Color.Transparent
    }

    val borderWidth = if (hasError || hasWarning) 2.dp else 0.dp

    Card(
        modifier = modifier
            .then(
                if (hasError || hasWarning) {
                    Modifier.border(borderWidth, borderColor, MaterialTheme.shapes.medium)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                hasError -> SemanticColors.Alarm.copy(alpha = 0.1f)
                hasWarning -> SemanticColors.Warning.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title row with status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pid.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                // Compact status indicator
                if (pid.isOffline) {
                    CompactStatusBadge(text = "OFF", color = SemanticColors.Alarm)
                } else if (pid.hasProbeError) {
                    CompactStatusBadge(text = pid.probeError.shortName, color = SemanticColors.Alarm)
                } else if (pid.isStale) {
                    CompactStatusBadge(text = "!", color = SemanticColors.Warning)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // PV - large and prominent
            if (pid.hasProbeError) {
                Text(
                    text = pid.probeError.shortName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = SemanticColors.Alarm
                )
            } else {
                Text(
                    text = "${String.format("%.1f", pid.processValue)}°",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (pid.isOffline) SemanticColors.Stale else Color.Unspecified
                )
            }

            // SV - smaller below
            Text(
                text = "→ ${String.format("%.1f", pid.setpointValue)}°",
                style = MaterialTheme.typography.bodySmall,
                color = if (pid.isOffline) SemanticColors.Stale else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Compact LED row - just dots, no labels
            CompactPidLeds(
                isEnabled = pid.isEnabled,
                isOutputActive = pid.isOutputActive,
                hasFault = pid.hasFault || pid.hasProbeError,
                isStale = pid.isStale,
                al1Active = pid.alarmRelays.al1,
                al2Active = pid.alarmRelays.al2
            )
        }
    }
}

/**
 * Compact status badge for tight spaces.
 */
@Composable
private fun CompactStatusBadge(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/**
 * Compact LED row showing just colored dots without labels.
 */
@Composable
private fun CompactPidLeds(
    isEnabled: Boolean,
    isOutputActive: Boolean,
    hasFault: Boolean,
    isStale: Boolean,
    al1Active: Boolean,
    al2Active: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedIndicator(
            isOn = isEnabled,
            size = 8.dp,
            onColor = SemanticColors.Enabled,
            isStale = isStale
        )
        LedIndicator(
            isOn = isOutputActive,
            size = 8.dp,
            onColor = SemanticColors.OutputActive,
            isPulsing = true,
            isStale = isStale
        )
        LedIndicator(
            isOn = hasFault,
            size = 8.dp,
            onColor = SemanticColors.Fault,
            isStale = isStale
        )
        if (al1Active || al2Active) {
            LedIndicator(
                isOn = al1Active,
                size = 8.dp,
                onColor = SemanticColors.Alarm,
                isPulsing = al1Active,
                isStale = isStale
            )
            LedIndicator(
                isOn = al2Active,
                size = 8.dp,
                onColor = SemanticColors.Alarm,
                isPulsing = al2Active,
                isStale = isStale
            )
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Compact indicator bank section.
 * Shows system interlock status in a single row for space efficiency.
 * Health indicators (BLE/MCU) are now in the StatusStrip.
 */
@Composable
fun IndicatorsSection(
    interlockStatus: InterlockStatus,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "System Status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // All indicators in one row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IndicatorItem("Door", interlockStatus.isDoorLocked)
                IndicatorItem("LN2", interlockStatus.isLn2Present)
                IndicatorItem("E-stop", !interlockStatus.isEStopActive, invertColor = true)
                IndicatorItem("Power", interlockStatus.isPowerEnabled)
                IndicatorItem("Heat", interlockStatus.isHeatersEnabled)
                IndicatorItem("Motor", interlockStatus.isMotorEnabled)
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

/**
 * Compact manual controls bar.
 * Shows common controls as icon buttons in a horizontal row.
 * Service mode controls appear when enabled.
 */
@Composable
fun ManualControlsSection(
    isServiceMode: Boolean,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Normal controls - always visible
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Controls",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CompactToggleButton(
                    label = "Lights",
                    icon = Icons.Default.Lightbulb
                )
                CompactToggleButton(
                    label = "Door",
                    icon = Icons.Default.Lock
                )
            }

            // Service mode controls
            if (isServiceMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = SemanticColors.Warning.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "SVC",
                            style = MaterialTheme.typography.labelSmall,
                            color = SemanticColors.Warning,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    CompactToggleButton(
                        label = "Heat",
                        icon = Icons.Default.Whatshot,
                        warningColor = true
                    )
                    CompactToggleButton(
                        label = "Motor",
                        icon = Icons.Default.Settings,
                        warningColor = true
                    )
                }
            }
        }
    }
}

/**
 * Compact toggle button with icon and label.
 */
@Composable
private fun CompactToggleButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    warningColor: Boolean = false
) {
    var isOn by remember { mutableStateOf(false) }

    val backgroundColor = when {
        isOn && warningColor -> SemanticColors.Warning.copy(alpha = 0.3f)
        isOn -> SemanticColors.Normal.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = when {
        isOn && warningColor -> SemanticColors.Warning
        isOn -> SemanticColors.Normal
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = { isOn = !isOn },
        color = backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
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
