package com.shakercontrol.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shakercontrol.app.domain.model.*
import com.shakercontrol.app.ui.theme.ShakerControlTheme

/**
 * Home screen with cards layout.
 * Spec: docs/dashboard-sec-v1.md section 4.
 */
@Composable
fun HomeScreen(
    onNavigateToRun: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToPid: (Int) -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val systemStatus by viewModel.systemStatus.collectAsStateWithLifecycle()
    val pidData by viewModel.pidData.collectAsStateWithLifecycle()
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    val runProgress by viewModel.runProgress.collectAsStateWithLifecycle()
    val interlockStatus by viewModel.interlockStatus.collectAsStateWithLifecycle()

    HomeScreenContent(
        systemStatus = systemStatus,
        pidData = pidData,
        recipe = recipe,
        runProgress = runProgress,
        interlockStatus = interlockStatus,
        onNavigateToRun = onNavigateToRun,
        onNavigateToDevices = onNavigateToDevices,
        onNavigateToPid = onNavigateToPid,
        onNavigateToDiagnostics = onNavigateToDiagnostics,
        onNavigateToSettings = onNavigateToSettings
    )
}

@Composable
private fun HomeScreenContent(
    systemStatus: SystemStatus,
    pidData: List<PidData>,
    recipe: Recipe,
    runProgress: RunProgress?,
    interlockStatus: InterlockStatus,
    onNavigateToRun: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToPid: (Int) -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left column - Primary card (Run)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            RunCard(
                machineState = systemStatus.machineState,
                connectionState = systemStatus.connectionState,
                recipe = recipe,
                runProgress = runProgress,
                onNavigateToRun = onNavigateToRun,
                onNavigateToDevices = onNavigateToDevices,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Right column - Other cards
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TemperaturesCard(
                pidData = pidData,
                onPidClick = onNavigateToPid
            )

            StatusCard(
                interlockStatus = interlockStatus
            )

            DiagnosticsCard(
                onClick = onNavigateToDiagnostics
            )

            SettingsCard(
                onClick = onNavigateToSettings
            )
        }
    }
}

@Preview(widthDp = 1200, heightDp = 700)
@Composable
private fun HomeScreenPreview() {
    ShakerControlTheme {
        Surface {
            HomeScreenContent(
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
                ),
                pidData = listOf(
                    PidData(1, "Axle bearings", 25.4f, 30.0f, 45.6f, PidMode.AUTO, true, true, false, 120, CapabilityLevel.REQUIRED),
                    PidData(2, "Orbital bearings", 28.1f, 30.0f, 32.1f, PidMode.AUTO, true, true, false, 120, CapabilityLevel.REQUIRED),
                    PidData(3, "LN2 line", -180.5f, -185.0f, 0.0f, PidMode.AUTO, true, false, false, 120, CapabilityLevel.OPTIONAL)
                ),
                recipe = Recipe.DEFAULT,
                runProgress = null,
                interlockStatus = InterlockStatus(
                    isEStopActive = false,
                    isDoorLocked = true,
                    isLn2Present = true,
                    isPowerEnabled = true,
                    isHeatersEnabled = false,
                    isMotorEnabled = false
                ),
                onNavigateToRun = {},
                onNavigateToDevices = {},
                onNavigateToPid = {},
                onNavigateToDiagnostics = {},
                onNavigateToSettings = {}
            )
        }
    }
}
