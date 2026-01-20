package com.shakercontrol.app.ui.run

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import com.shakercontrol.app.domain.model.*
import com.shakercontrol.app.ui.theme.ShakerControlTheme
import kotlinx.coroutines.flow.collectLatest

/**
 * Run screen (Cockpit) - professional control center during operation.
 * Spec: docs/dashboard-sec-v1.md section 5.
 */
@Composable
fun RunScreen(
    onNavigateToPid: (Int) -> Unit,
    onNavigateToIo: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: RunViewModel = hiltViewModel()
) {
    val systemStatus by viewModel.systemStatus.collectAsStateWithLifecycle()
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    val runProgress by viewModel.runProgress.collectAsStateWithLifecycle()
    val pidData by viewModel.pidData.collectAsStateWithLifecycle()
    val interlockStatus by viewModel.interlockStatus.collectAsStateWithLifecycle()
    val ioStatus by viewModel.ioStatus.collectAsStateWithLifecycle()
    val isSimulationEnabled by viewModel.isSimulationEnabled.collectAsStateWithLifecycle()
    val isExecutingCommand by viewModel.isExecutingCommand.collectAsStateWithLifecycle()
    val startGating by viewModel.startGating.collectAsStateWithLifecycle()
    val displaySlots by viewModel.displaySlots.collectAsStateWithLifecycle()
    val areHeatersEnabled by viewModel.areHeatersEnabled.collectAsStateWithLifecycle()
    val isCoolingEnabled by viewModel.isCoolingEnabled.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showStopDialog by remember { mutableStateOf(false) }

    // Collect UI events for snackbar
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is RunUiEvent.CommandError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is RunUiEvent.CommandSuccess -> {
                    // Optionally show success feedback
                }
            }
        }
    }

    if (showStopDialog) {
        StopConfirmationDialog(
            onDismiss = { showStopDialog = false },
            onConfirm = {
                viewModel.stopRun()
                showStopDialog = false
            }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { paddingValues ->
        RunScreenContent(
            systemStatus = systemStatus,
            recipe = recipe,
            runProgress = runProgress,
            pidData = pidData,
            interlockStatus = interlockStatus,
            ioStatus = ioStatus,
            isSimulationEnabled = isSimulationEnabled,
            isExecutingCommand = isExecutingCommand,
            startGating = startGating,
            displaySlots = displaySlots,
            areHeatersEnabled = areHeatersEnabled,
            isCoolingEnabled = isCoolingEnabled,
            onRecipeChange = viewModel::updateRecipe,
            onStart = viewModel::startRun,
            onPause = viewModel::pauseRun,
            onResume = viewModel::resumeRun,
            onStop = { showStopDialog = true },
            onToggleHeaters = viewModel::toggleHeaters,
            onToggleCooling = viewModel::toggleCooling,
            onNavigateToPid = onNavigateToPid,
            onNavigateToIo = onNavigateToIo,
            onDisplaySlotClick = { /* TODO: Open slot config dialog */ },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Compact Run screen layout designed to fit without scrolling.
 * Layout:
 * ┌─────────────────────────────┬───────────────────────────┐
 * │  Recipe Card                │  PID1  │  PID2  │  PID3  │
 * │  (Mill/Hold/Cycles)         │  Stamp │  Stamp │  Stamp │
 * │  ─────────────────────────  ├───────────────────────────┤
 * │  Readiness: interlocks+ctrl │  Display Slot 1 │ Slot 2 │
 * ├─────────────────────────────┤  (plots, camera, etc.)    │
 * │  Controls Card              │  (I/O section in svc mode)│
 * │  [Start/Pause/Stop]         │                           │
 * └─────────────────────────────┴───────────────────────────┘
 */
@Composable
private fun RunScreenContent(
    systemStatus: SystemStatus,
    recipe: Recipe,
    runProgress: RunProgress?,
    pidData: List<PidData>,
    interlockStatus: InterlockStatus,
    ioStatus: IoStatus,
    isSimulationEnabled: Boolean,
    isExecutingCommand: Boolean,
    startGating: StartGatingResult,
    displaySlots: List<DisplaySlot>,
    areHeatersEnabled: Boolean,
    isCoolingEnabled: Boolean,
    onRecipeChange: (Recipe) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onToggleHeaters: () -> Unit,
    onToggleCooling: () -> Unit,
    onNavigateToPid: (Int) -> Unit,
    onNavigateToIo: () -> Unit,
    onDisplaySlotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Main content area - two columns
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left column - Recipe (with readiness) + Controls
        // Recipe does NOT expand - it uses only the space it needs
        // Empty space is above Controls section at the bottom
        Column(
            modifier = Modifier.weight(0.45f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RecipeSection(
                recipe = recipe,
                runProgress = runProgress,
                isRunning = systemStatus.machineState.isOperating,
                interlockStatus = interlockStatus,
                isServiceMode = systemStatus.isServiceModeEnabled,
                areHeatersEnabled = areHeatersEnabled,
                isCoolingEnabled = isCoolingEnabled,
                onRecipeChange = onRecipeChange,
                onToggleHeaters = onToggleHeaters,
                onToggleCooling = onToggleCooling
                // No weight modifier - use only needed space
            )

            // Spacer pushes Controls to the bottom
            Spacer(modifier = Modifier.weight(1f))

            ControlsSection(
                machineState = systemStatus.machineState,
                connectionState = systemStatus.connectionState,
                isExecutingCommand = isExecutingCommand,
                startGating = startGating,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop
            )
        }

        // Right column - PIDs + Display slots (+ I/O in service mode)
        Column(
            modifier = Modifier.weight(0.55f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // PID temperatures - compact grid
            TemperaturesSection(
                pidData = pidData,
                onNavigateToPid = onNavigateToPid
            )

            // Display slots - configurable visualization panels
            if (displaySlots.isNotEmpty()) {
                DisplaySlotsSection(
                    slots = displaySlots,
                    onSlotClick = onDisplaySlotClick,
                    modifier = Modifier.weight(1f)
                )
            }

            // I/O section - only visible in service mode
            if (systemStatus.isServiceModeEnabled) {
                IoSection(
                    ioStatus = ioStatus,
                    isSimulationEnabled = isSimulationEnabled,
                    onClick = onNavigateToIo
                )
            }
        }
    }
}

/**
 * Display slots section for configurable data visualizations.
 * Shows 1-2 slots side by side for plots, thermal camera, vibration, etc.
 */
@Composable
private fun DisplaySlotsSection(
    slots: List<DisplaySlot>,
    onSlotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slots.take(2).forEach { slot ->
            DisplaySlotCard(
                slot = slot,
                onClick = { onSlotClick(slot.index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Single display slot card - clickable to expand/configure.
 */
@Composable
private fun DisplaySlotCard(
    slot: DisplaySlot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            when (slot.source) {
                DisplaySource.EMPTY -> {
                    // Empty placeholder
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add display",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap to configure",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                DisplaySource.TEMPERATURE_HISTORY -> {
                    // Placeholder for temperature plot
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = slot.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Plot placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Temperature history plot",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                DisplaySource.THERMAL_CAMERA -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = slot.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Thermal camera feed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                DisplaySource.VIBRATION -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = slot.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Vibration waveform",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                DisplaySource.POWER_CONSUMPTION -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = slot.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Power usage chart",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stop run?") },
        text = {
            Text("This will stop the current run. The controller will transition to a safe state.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(widthDp = 1200, heightDp = 700)
@Composable
private fun RunScreenPreview() {
    ShakerControlTheme {
        Surface {
            RunScreenContent(
                systemStatus = SystemStatus(
                    connectionState = ConnectionState.LIVE,
                    machineState = MachineState.RUNNING,
                    mcuHeartbeatAgeMs = 120,
                    bleHeartbeatAgeMs = 80,
                    alarmSummary = AlarmSummary(0, 0, null),
                    isServiceModeEnabled = false,
                    deviceName = "SYS-CTRL-001",
                    rssiDbm = -58,
                    firmwareVersion = "1.0.0",
                    protocolVersion = 1
                ),
                recipe = Recipe.DEFAULT,
                runProgress = RunProgress(
                    currentCycle = 2,
                    totalCycles = 5,
                    currentPhase = RunPhase.MILLING,
                    phaseElapsed = kotlin.time.Duration.parse("1m30s"),
                    phaseRemaining = kotlin.time.Duration.parse("3m30s"),
                    totalRemaining = kotlin.time.Duration.parse("18m30s")
                ),
                pidData = listOf(
                    PidData(1, "LN2 (Cold)", -180.5f, -185.0f, 0.0f, PidMode.AUTO, true, false, false, 120, CapabilityLevel.OPTIONAL),
                    PidData(2, "Axle bearings", 25.4f, 30.0f, 45.6f, PidMode.AUTO, true, true, false, 120, CapabilityLevel.REQUIRED),
                    PidData(3, "Orbital bearings", 28.1f, 30.0f, 32.1f, PidMode.AUTO, true, true, false, 120, CapabilityLevel.REQUIRED)
                ),
                interlockStatus = InterlockStatus(
                    isEStopActive = false,
                    isDoorLocked = true,
                    isLn2Present = true,
                    isPowerEnabled = true,
                    isHeatersEnabled = true,
                    isMotorEnabled = true
                ),
                ioStatus = IoStatus(
                    digitalInputs = 0b00101101,  // Channels 1, 3, 4, 6 high
                    relayOutputs = 0b00010010    // Channels 2, 5 high
                ),
                isSimulationEnabled = false,
                isExecutingCommand = false,
                startGating = StartGatingResult.OK,
                displaySlots = emptyList(),
                areHeatersEnabled = true,
                isCoolingEnabled = true,
                onRecipeChange = {},
                onStart = {},
                onPause = {},
                onResume = {},
                onStop = {},
                onToggleHeaters = {},
                onToggleCooling = {},
                onNavigateToPid = {},
                onNavigateToIo = {},
                onDisplaySlotClick = {}
            )
        }
    }
}

@Preview(widthDp = 1200, heightDp = 700)
@Composable
private fun RunScreenServiceModePreview() {
    ShakerControlTheme {
        Surface {
            RunScreenContent(
                systemStatus = SystemStatus(
                    connectionState = ConnectionState.LIVE,
                    machineState = MachineState.READY,
                    mcuHeartbeatAgeMs = 120,
                    bleHeartbeatAgeMs = 80,
                    alarmSummary = AlarmSummary(0, 0, null),
                    isServiceModeEnabled = true,  // Service mode enabled
                    deviceName = "SYS-CTRL-001",
                    rssiDbm = -58,
                    firmwareVersion = "1.0.0",
                    protocolVersion = 1
                ),
                recipe = Recipe.DEFAULT,
                runProgress = null,
                pidData = listOf(
                    PidData(1, "LN2 (Cold)", -180.5f, -185.0f, 0.0f, PidMode.AUTO, true, false, false, 120, CapabilityLevel.OPTIONAL),
                    PidData(2, "Axle bearings", 25.4f, 30.0f, 45.6f, PidMode.AUTO, true, true, false, 120, CapabilityLevel.REQUIRED),
                    PidData(3, "Orbital bearings", 28.1f, 30.0f, 32.1f, PidMode.AUTO, true, true, false, 120, CapabilityLevel.REQUIRED)
                ),
                interlockStatus = InterlockStatus(
                    isEStopActive = false,
                    isDoorLocked = true,
                    isLn2Present = true,
                    isPowerEnabled = true,
                    isHeatersEnabled = true,
                    isMotorEnabled = true
                ),
                ioStatus = IoStatus(
                    digitalInputs = 0b00101101,
                    relayOutputs = 0b00010010
                ),
                isSimulationEnabled = true,
                isExecutingCommand = false,
                startGating = StartGatingResult.OK,
                displaySlots = listOf(
                    DisplaySlot(0, DisplaySource.TEMPERATURE_HISTORY, "Temperature Plot"),
                    DisplaySlot(1, DisplaySource.EMPTY, "Available")
                ),
                areHeatersEnabled = true,
                isCoolingEnabled = false,  // Show cooling disabled for contrast
                onRecipeChange = {},
                onStart = {},
                onPause = {},
                onResume = {},
                onStop = {},
                onToggleHeaters = {},
                onToggleCooling = {},
                onNavigateToPid = {},
                onNavigateToIo = {},
                onDisplaySlotClick = {}
            )
        }
    }
}
