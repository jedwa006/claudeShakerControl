package com.shakercontrol.app.ui.run

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
 * Recipe editor section with integrated readiness indicators.
 * Layout: Recipe inputs → Controls/Progress → Interlocks (static at bottom)
 *
 * When idle: Shows recipe inputs, then controls (Lights/Door), then interlocks
 * When running: Shows recipe inputs, then progress display, then interlocks
 *
 * Spec: docs/dashboard-sec-v1.md section 5.1 and docs/ui-copy-labels-v1.md section 3.2
 */
@Composable
fun RecipeSection(
    recipe: Recipe,
    runProgress: RunProgress?,
    isRunning: Boolean,
    interlockStatus: InterlockStatus,
    isServiceMode: Boolean,
    isConnected: Boolean = false,
    isLightOn: Boolean = false,
    isDoorLocked: Boolean = false,
    areHeatersEnabled: Boolean = false,
    isCoolingEnabled: Boolean = false,
    onRecipeChange: (Recipe) -> Unit,
    onToggleLight: () -> Unit = {},
    onToggleDoor: () -> Unit = {},
    onToggleHeaters: () -> Unit = {},
    onToggleCooling: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            // === RECIPE CONFIGURATION SECTION (top) ===
            RecipeConfigSection(
                recipe = recipe,
                isRunning = isRunning,
                onRecipeChange = onRecipeChange
            )

            Spacer(modifier = Modifier.height(8.dp))
            SectionDivider()

            // === MIDDLE SECTION: Controls when idle, Progress when running ===
            if (runProgress != null) {
                // Running: Show progress display
                Spacer(modifier = Modifier.height(8.dp))
                RunProgressDisplay(runProgress = runProgress)
            } else {
                // Idle: Show controls (Lights, Door, + Heat/Cool in service mode)
                Spacer(modifier = Modifier.height(8.dp))
                ControlsRow(
                    isServiceMode = isServiceMode,
                    isLightOn = isLightOn,
                    isDoorLocked = isDoorLocked,
                    isConnected = isConnected,
                    areHeatersEnabled = areHeatersEnabled,
                    isCoolingEnabled = isCoolingEnabled,
                    onToggleLight = onToggleLight,
                    onToggleDoor = onToggleDoor,
                    onToggleHeaters = onToggleHeaters,
                    onToggleCooling = onToggleCooling
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            SectionDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // === INTERLOCKS SECTION (static at bottom) ===
            InterlocksRow(
                interlockStatus = interlockStatus,
                isServiceMode = isServiceMode
            )
        }
    }
}

/**
 * Recipe configuration sub-section with inputs and totals.
 */
@Composable
private fun RecipeConfigSection(
    recipe: Recipe,
    isRunning: Boolean,
    onRecipeChange: (Recipe) -> Unit
) {
    // Header row with title, Mill/Hold totals, and estimated total
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Recipe",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        // Mill/Hold totals and estimated total grouped together
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mill total
            Text(
                text = "Mill: ${formatDurationLong(recipe.totalMillingTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Hold total
            Text(
                text = "Hold: ${formatDurationLong(recipe.totalHoldingTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Prominent total time display
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = formatDurationLong(recipe.totalRuntime),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Input fields - compact row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            modifier = Modifier.weight(0.7f)
        )
    }

}

/**
 * Visual section divider (simple horizontal line).
 */
@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/**
 * Controls row with Lights, Door, and service-mode controls.
 * These are locked/hidden when a run is in progress.
 *
 * Light relay: CH7 (chamber lighting)
 * Door lock relay: CH6 (solenoid locking bar)
 */
@Composable
private fun ControlsRow(
    isServiceMode: Boolean,
    isLightOn: Boolean = false,
    isDoorLocked: Boolean = false,
    isConnected: Boolean = false,
    areHeatersEnabled: Boolean = false,
    isCoolingEnabled: Boolean = false,
    onToggleLight: () -> Unit = {},
    onToggleDoor: () -> Unit = {},
    onToggleHeaters: () -> Unit = {},
    onToggleCooling: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Controls",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.width(4.dp))

        ReadinessToggleButton(
            label = "Lights",
            icon = Icons.Default.Lightbulb,
            isOn = isLightOn,
            onToggle = onToggleLight,
            enabled = isConnected
        )
        ReadinessToggleButton(
            label = "Door",
            icon = if (isDoorLocked) Icons.Default.Lock else Icons.Default.LockOpen,
            isOn = isDoorLocked,
            onToggle = onToggleDoor,
            enabled = isConnected
        )

        // Service mode controls - Heat and Cool buttons for PID control
        if (isServiceMode) {
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                color = SemanticColors.Warning.copy(alpha = 0.15f),
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
            // Heat button - controls PID 2 & 3 (heater bearings)
            PidControlToggleButton(
                label = "Heat",
                icon = Icons.Default.Whatshot,
                isEnabled = areHeatersEnabled,
                warningColor = true,
                onClick = onToggleHeaters
            )
            // Cool button - controls PID 1 (LN2)
            PidControlToggleButton(
                label = "Cool",
                icon = Icons.Default.AcUnit,
                isEnabled = isCoolingEnabled,
                warningColor = true,
                onClick = onToggleCooling
            )
        }
    }
}

/**
 * Static interlocks row showing system status.
 * Always visible at the bottom of the Recipe section.
 */
@Composable
private fun InterlocksRow(
    interlockStatus: InterlockStatus,
    isServiceMode: Boolean
) {
    val allOk = interlockStatus.isDoorLocked &&
            interlockStatus.isLn2Present &&
            !interlockStatus.isEStopActive &&
            interlockStatus.isPowerEnabled

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Interlock indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Interlocks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            ReadinessIndicator("Door", interlockStatus.isDoorLocked)
            ReadinessIndicator("LN2", interlockStatus.isLn2Present)
            ReadinessIndicator("E-stop", !interlockStatus.isEStopActive, invertColor = true)
            ReadinessIndicator("Pwr", interlockStatus.isPowerEnabled)
            if (isServiceMode) {
                ReadinessIndicator("Heat", interlockStatus.isHeatersEnabled)
                ReadinessIndicator("Mtr", interlockStatus.isMotorEnabled)
            }
        }

        // Status badge
        Surface(
            color = if (allOk) SemanticColors.Normal.copy(alpha = 0.2f) else SemanticColors.Warning.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = if (allOk) "OK" else "Check",
                style = MaterialTheme.typography.labelSmall,
                color = if (allOk) SemanticColors.Normal else SemanticColors.Warning,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Compact indicator for readiness panel.
 */
@Composable
private fun ReadinessIndicator(
    label: String,
    isOn: Boolean,
    invertColor: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedIndicator(
            isOn = isOn,
            size = 10.dp,
            onColor = if (invertColor && !isOn) SemanticColors.Alarm else SemanticColors.OutputActive,
            offColor = if (invertColor) SemanticColors.Normal else SemanticColors.OutputInactive
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Toggle button for relay controls (Lights, Door).
 * @param isOn Current state of the relay
 * @param onToggle Callback when button is clicked
 * @param enabled Whether the button is enabled (e.g., when connected)
 * @param compact If true, shows icon only (for space-constrained layouts)
 */
@Composable
private fun ReadinessToggleButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isOn: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    warningColor: Boolean = false,
    compact: Boolean = false
) {
    val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isOn && warningColor -> SemanticColors.Warning.copy(alpha = 0.3f)
        isOn -> SemanticColors.Normal.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isOn && warningColor -> SemanticColors.Warning
        isOn -> SemanticColors.Normal
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onToggle,
        enabled = enabled,
        color = backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = 6.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(if (compact) 18.dp else 16.dp),
                tint = contentColor
            )
            if (!compact) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * PID control toggle button for service mode Heat/Cool controls.
 * Shows actual PID state with pulsing animation when enabled.
 */
@Composable
private fun PidControlToggleButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean,
    warningColor: Boolean = true,
    onClick: () -> Unit
) {
    // Pulsing animation when enabled
    val infiniteTransition = rememberInfiniteTransition(label = "${label}_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isEnabled) 0.5f else 0.3f,
        targetValue = if (isEnabled) 0.8f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "${label}_alpha"
    )

    val backgroundColor = when {
        isEnabled && warningColor -> SemanticColors.Warning.copy(alpha = pulseAlpha)
        isEnabled -> SemanticColors.Normal.copy(alpha = pulseAlpha)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = when {
        isEnabled && warningColor -> SemanticColors.Warning
        isEnabled -> SemanticColors.Normal
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        color = backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Duration input that shows a picker dialog when tapped.
 * Much better UX than trying to type mm:ss format.
 */
@Composable
private fun DurationInput(
    label: String,
    duration: Duration,
    onDurationChange: (Duration) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        DurationPickerDialog(
            title = label,
            initialDuration = duration,
            onDismiss = { showPicker = false },
            onConfirm = { newDuration ->
                onDurationChange(newDuration)
                showPicker = false
            }
        )
    }

    // Clickable display that opens the picker
    OutlinedCard(
        onClick = { if (enabled) showPicker = true },
        modifier = modifier,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

/**
 * Dialog with minute and second spinners for duration input.
 */
@Composable
private fun DurationPickerDialog(
    title: String,
    initialDuration: Duration,
    onDismiss: () -> Unit,
    onConfirm: (Duration) -> Unit
) {
    val totalSeconds = initialDuration.inWholeSeconds.toInt()
    var minutes by remember { mutableStateOf(totalSeconds / 60) }
    var seconds by remember { mutableStateOf(totalSeconds % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minutes spinner
                NumberSpinner(
                    value = minutes,
                    onValueChange = { minutes = it },
                    range = 0..99,
                    label = "min"
                )

                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Seconds spinner
                NumberSpinner(
                    value = seconds,
                    onValueChange = { seconds = it },
                    range = 0..59,
                    label = "sec"
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm((minutes.minutes + seconds.seconds))
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Simple number spinner with +/- buttons.
 */
@Composable
private fun NumberSpinner(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Increment button
        IconButton(
            onClick = { if (value < range.last) onValueChange(value + 1) }
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase")
        }

        // Value display
        Text(
            text = String.format("%02d", value),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Decrement button
        IconButton(
            onClick = { if (value > range.first) onValueChange(value - 1) }
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease")
        }
    }
}

/**
 * Cycle count input with picker dialog.
 */
@Composable
private fun CycleInput(
    cycles: Int,
    onCyclesChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        CyclePickerDialog(
            initialCycles = cycles,
            onDismiss = { showPicker = false },
            onConfirm = { newCycles ->
                onCyclesChange(newCycles)
                showPicker = false
            }
        )
    }

    // Clickable display that opens the picker
    OutlinedCard(
        onClick = { if (enabled) showPicker = true },
        modifier = modifier,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Cycles",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = cycles.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

/**
 * Dialog for selecting cycle count.
 */
@Composable
private fun CyclePickerDialog(
    initialCycles: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var cycles by remember { mutableStateOf(initialCycles) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cycles") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberSpinner(
                    value = cycles,
                    onValueChange = { cycles = it },
                    range = 1..99,
                    label = "cycles"
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(cycles) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
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
 * Big control buttons section with Chilldown and Start controls.
 * Spec: docs/dashboard-sec-v1.md section 5.1 B and docs/ui-copy-labels-v1.md section 3.3
 *
 * Layout when not running:
 * ┌──────────────────────────────────────┐
 * │  [Chilldown] [✓ Start after chill]   │
 * ├──────────────────────────────────────┤
 * │            [  START  ]               │
 * └──────────────────────────────────────┘
 */
@Composable
fun ControlsSection(
    machineState: MachineState,
    connectionState: ConnectionState,
    isExecutingCommand: Boolean = false,
    startGating: StartGatingResult = StartGatingResult.OK,
    isChilldownActive: Boolean = false,
    isStartAfterChillEnabled: Boolean = false,
    canChilldown: Boolean = false,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onChilldown: () -> Unit = {},
    onToggleStartAfterChill: (Boolean) -> Unit = {},
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

            // Chilldown row (only shown when not running)
            if (!machineState.isOperating) {
                ChilldownRow(
                    isChilldownActive = isChilldownActive,
                    isStartAfterChillEnabled = isStartAfterChillEnabled,
                    canChilldown = canChilldown && !isExecutingCommand,
                    onChilldown = onChilldown,
                    onToggleStartAfterChill = onToggleStartAfterChill
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

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
 * Chilldown row with button and "Start after chill" checkbox.
 * Used to pre-cool the chamber with LN2 before starting a run.
 */
@Composable
private fun ChilldownRow(
    isChilldownActive: Boolean,
    isStartAfterChillEnabled: Boolean,
    canChilldown: Boolean,
    onChilldown: () -> Unit,
    onToggleStartAfterChill: (Boolean) -> Unit
) {
    // Pulsing animation when chilldown is active
    val infiniteTransition = rememberInfiniteTransition(label = "chilldown_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isChilldownActive) 0.5f else 1f,
        targetValue = if (isChilldownActive) 1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chilldown_alpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chilldown button
        OutlinedButton(
            onClick = onChilldown,
            enabled = canChilldown && !isChilldownActive,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isChilldownActive) {
                    SemanticColors.Active.copy(alpha = pulseAlpha * 0.2f)
                } else {
                    Color.Transparent
                }
            ),
            modifier = Modifier.height(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AcUnit,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isChilldownActive) SemanticColors.Active else LocalContentColor.current
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isChilldownActive) "Chilling..." else "Chilldown",
                style = MaterialTheme.typography.labelLarge
            )
        }

        // Start after chill checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onToggleStartAfterChill(!isStartAfterChillEnabled) }
        ) {
            Checkbox(
                checked = isStartAfterChillEnabled,
                onCheckedChange = onToggleStartAfterChill,
                enabled = !isChilldownActive
            )
            Text(
                text = "Start after chill",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isChilldownActive) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Status badge when chilling
        if (isChilldownActive) {
            Surface(
                color = SemanticColors.Active.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Pre-cooling",
                    style = MaterialTheme.typography.labelSmall,
                    color = SemanticColors.Active,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
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
    onWakeFromLazy: () -> Unit = {},
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
                        onWakeFromLazy = onWakeFromLazy,
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
 *
 * Visual states:
 * - Error (offline/probe error): Red pulsing border
 * - Warning (stale): Yellow static border
 * - Lazy mode: Yellow breathing border (slower pulse)
 * - Normal: No border
 */
@Composable
private fun CompactPidTile(
    pid: PidData,
    onClick: () -> Unit,
    onWakeFromLazy: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulsing border for error states
    val hasError = pid.isOffline || pid.hasProbeError
    val hasWarning = pid.isStale && !hasError
    val isLazyMode = pid.lazyPollActive && !hasError && !hasWarning

    val infiniteTransition = rememberInfiniteTransition(label = "error_pulse")

    // Red pulsing for errors (fast pulse)
    val errorBorderAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "error_border_alpha"
    )

    // Yellow breathing for lazy mode (slower, gentler pulse)
    val lazyBorderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lazy_border_alpha"
    )

    val borderColor = when {
        hasError -> SemanticColors.Alarm.copy(alpha = errorBorderAlpha)
        hasWarning -> SemanticColors.Warning.copy(alpha = 0.7f)
        isLazyMode -> SemanticColors.Warning.copy(alpha = lazyBorderAlpha)
        else -> Color.Transparent
    }

    val borderWidth = if (hasError || hasWarning || isLazyMode) 2.dp else 0.dp

    // Tap behavior: navigate to PID detail, but also wake from lazy mode
    val handleClick = {
        if (isLazyMode) {
            onWakeFromLazy()
        }
        onClick()
    }

    Card(
        modifier = modifier
            .then(
                if (hasError || hasWarning || isLazyMode) {
                    Modifier.border(borderWidth, borderColor, MaterialTheme.shapes.medium)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = handleClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                hasError -> SemanticColors.Alarm.copy(alpha = 0.1f)
                hasWarning -> SemanticColors.Warning.copy(alpha = 0.1f)
                isLazyMode -> SemanticColors.Warning.copy(alpha = 0.05f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title row with health LED and status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Health/comm status LED - shows data freshness
                    LedIndicator(
                        isOn = !pid.isOffline,
                        size = 6.dp,
                        onColor = when {
                            pid.isOffline -> SemanticColors.Alarm
                            pid.isStale -> SemanticColors.Warning
                            else -> SemanticColors.Normal
                        },
                        isStale = pid.isStale,
                        isPulsing = !pid.isOffline && !pid.isStale // Pulse when healthy
                    )
                    Text(
                        text = pid.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
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
 * Combined dashboard bar with manual controls and system status indicators.
 * Left side: Manual controls (Lights, Door, +Heat/Motor in service mode)
 * Right side: System interlock status indicators
 */
@Composable
fun DashboardBar(
    interlockStatus: InterlockStatus,
    isServiceMode: Boolean,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Manual controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashboardToggleButton(
                    label = "Lights",
                    icon = Icons.Default.Lightbulb
                )
                DashboardToggleButton(
                    label = "Door",
                    icon = Icons.Default.Lock
                )

                // Service mode controls
                if (isServiceMode) {
                    VerticalDivider(
                        modifier = Modifier.height(28.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Surface(
                        color = SemanticColors.Warning.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "SVC",
                            style = MaterialTheme.typography.labelSmall,
                            color = SemanticColors.Warning,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                    DashboardToggleButton(
                        label = "Heat",
                        icon = Icons.Default.Whatshot,
                        warningColor = true
                    )
                    DashboardToggleButton(
                        label = "Motor",
                        icon = Icons.Default.Settings,
                        warningColor = true
                    )
                }
            }

            // Right side - System status indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashboardIndicator("Door", interlockStatus.isDoorLocked)
                DashboardIndicator("LN2", interlockStatus.isLn2Present)
                DashboardIndicator("E-stop", !interlockStatus.isEStopActive, invertColor = true)
                DashboardIndicator("Power", interlockStatus.isPowerEnabled)
                DashboardIndicator("Heat", interlockStatus.isHeatersEnabled)
                DashboardIndicator("Motor", interlockStatus.isMotorEnabled)
            }
        }
    }
}

/**
 * Larger toggle button for dashboard bar.
 */
@Composable
private fun DashboardToggleButton(
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

/**
 * Compact indicator for dashboard bar (horizontal layout with LED and label).
 */
@Composable
private fun DashboardIndicator(
    label: String,
    isOn: Boolean,
    invertColor: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedIndicator(
            isOn = isOn,
            size = 10.dp,
            onColor = if (invertColor && !isOn) SemanticColors.Alarm else SemanticColors.OutputActive,
            offColor = if (invertColor) SemanticColors.Normal else SemanticColors.OutputInactive
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            interlockStatus = InterlockStatus(
                isEStopActive = false,
                isDoorLocked = true,
                isLn2Present = true,
                isPowerEnabled = true,
                isHeatersEnabled = true,
                isMotorEnabled = true
            ),
            isServiceMode = false,
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
