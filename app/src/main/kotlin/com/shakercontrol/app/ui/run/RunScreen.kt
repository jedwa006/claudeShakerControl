package com.shakercontrol.app.ui.run

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onNavigateBack: () -> Unit,
    viewModel: RunViewModel = hiltViewModel()
) {
    val systemStatus by viewModel.systemStatus.collectAsStateWithLifecycle()
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    val runProgress by viewModel.runProgress.collectAsStateWithLifecycle()
    val pidData by viewModel.pidData.collectAsStateWithLifecycle()
    val interlockStatus by viewModel.interlockStatus.collectAsStateWithLifecycle()
    val isExecutingCommand by viewModel.isExecutingCommand.collectAsStateWithLifecycle()
    val startGating by viewModel.startGating.collectAsStateWithLifecycle()

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
            isExecutingCommand = isExecutingCommand,
            startGating = startGating,
            onRecipeChange = viewModel::updateRecipe,
            onStart = viewModel::startRun,
            onPause = viewModel::pauseRun,
            onResume = viewModel::resumeRun,
            onStop = { showStopDialog = true },
            onNavigateToPid = onNavigateToPid,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun RunScreenContent(
    systemStatus: SystemStatus,
    recipe: Recipe,
    runProgress: RunProgress?,
    pidData: List<PidData>,
    interlockStatus: InterlockStatus,
    isExecutingCommand: Boolean,
    startGating: StartGatingResult,
    onRecipeChange: (Recipe) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onNavigateToPid: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left column - Recipe + Controls
        Column(
            modifier = Modifier
                .weight(0.5f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RecipeSection(
                recipe = recipe,
                runProgress = runProgress,
                isRunning = systemStatus.machineState.isOperating,
                onRecipeChange = onRecipeChange
            )

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

            ManualControlsSection(
                isServiceMode = systemStatus.isServiceModeEnabled
            )
        }

        // Right column - PIDs + Indicators
        Column(
            modifier = Modifier
                .weight(0.5f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TemperaturesSection(
                pidData = pidData,
                onNavigateToPid = onNavigateToPid
            )

            IndicatorsSection(
                interlockStatus = interlockStatus,
                mcuHeartbeatStatus = systemStatus.mcuHeartbeatStatus,
                mcuHeartbeatAgeMs = systemStatus.mcuHeartbeatAgeMs,
                connectionState = systemStatus.connectionState
            )
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
                    PidData(1, "Axle bearings", 25.4f, 30.0f, 45.6f, PidMode.AUTO, true, true, false, 120, CapabilityLevel.REQUIRED),
                    PidData(2, "Orbital bearings", 28.1f, 30.0f, 32.1f, PidMode.AUTO, true, true, false, 120, CapabilityLevel.REQUIRED),
                    PidData(3, "LN2 line", -180.5f, -185.0f, 0.0f, PidMode.AUTO, true, false, false, 120, CapabilityLevel.OPTIONAL)
                ),
                interlockStatus = InterlockStatus(
                    isEStopActive = false,
                    isDoorLocked = true,
                    isLn2Present = true,
                    isPowerEnabled = true,
                    isHeatersEnabled = true,
                    isMotorEnabled = true
                ),
                isExecutingCommand = false,
                startGating = StartGatingResult.OK,
                onRecipeChange = {},
                onStart = {},
                onPause = {},
                onResume = {},
                onStop = {},
                onNavigateToPid = {}
            )
        }
    }
}
