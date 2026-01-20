package com.shakercontrol.app.ui.io

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shakercontrol.app.domain.model.IoStatus
import com.shakercontrol.app.ui.theme.StatusActive
import com.shakercontrol.app.ui.theme.StatusAlarm
import com.shakercontrol.app.ui.theme.StatusNormal
import com.shakercontrol.app.ui.theme.StatusWarning
import kotlinx.coroutines.flow.collectLatest

@Composable
fun IoScreen(
    onNavigateBack: () -> Unit,
    viewModel: IoViewModel = hiltViewModel()
) {
    val ioStatus by viewModel.ioStatus.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val isServiceMode by viewModel.isServiceMode.collectAsStateWithLifecycle()
    val isSimulationEnabled by viewModel.isSimulationEnabled.collectAsStateWithLifecycle()
    val canControlRelays by viewModel.canControlRelays.collectAsStateWithLifecycle()
    val isExecutingCommand by viewModel.isExecutingCommand.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is IoUiEvent.CommandError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                IoUiEvent.CommandSuccess -> {
                    // No snackbar on success - visual feedback is immediate
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection status info
            if (!isConnected) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Not connected - I/O status unavailable",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Digital Inputs Section
            DigitalInputsCard(
                ioStatus = ioStatus,
                isSimulationEnabled = isSimulationEnabled,
                isServiceMode = isServiceMode,
                onSimulationToggle = viewModel::setSimulationEnabled,
                onSimulatedInputChange = viewModel::setSimulatedInput
            )

            // Relay Outputs Section
            RelayOutputsCard(
                ioStatus = ioStatus,
                canControl = canControlRelays,
                isExecutingCommand = isExecutingCommand,
                onRelayToggle = viewModel::setRelay
            )

            // Service mode hint
            if (!isServiceMode && isConnected) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "Enable Service Mode from the navigation drawer to control relay outputs and simulate inputs.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DigitalInputsCard(
    ioStatus: IoStatus,
    isSimulationEnabled: Boolean,
    isServiceMode: Boolean,
    onSimulationToggle: (Boolean) -> Unit,
    onSimulatedInputChange: (Int, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Digital Inputs",
                    style = MaterialTheme.typography.titleLarge
                )

                // Simulation toggle (only visible in service mode)
                if (isServiceMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Simulate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSimulationEnabled)
                                StatusWarning
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = isSimulationEnabled,
                            onCheckedChange = onSimulationToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = StatusWarning,
                                checkedTrackColor = StatusWarning.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            if (isSimulationEnabled) {
                Text(
                    text = "Simulation active - inputs are simulated, not from MCU",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarning
                )
            }

            // DI indicators grid (2 rows of 4)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (channel in 1..4) {
                        DigitalInputIndicator(
                            channel = channel,
                            isHigh = ioStatus.isInputHigh(channel),
                            isSimulating = isSimulationEnabled && isServiceMode,
                            onSimulatedChange = { high -> onSimulatedInputChange(channel, high) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (channel in 5..8) {
                        DigitalInputIndicator(
                            channel = channel,
                            isHigh = ioStatus.isInputHigh(channel),
                            isSimulating = isSimulationEnabled && isServiceMode,
                            onSimulatedChange = { high -> onSimulatedInputChange(channel, high) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitalInputIndicator(
    channel: Int,
    isHigh: Boolean,
    isSimulating: Boolean,
    onSimulatedChange: (Boolean) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "DI$channel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isSimulating) {
            // Clickable LED for simulation
            Surface(
                onClick = { onSimulatedChange(!isHigh) },
                shape = CircleShape,
                color = if (isHigh) StatusActive else Color.DarkGray,
                modifier = Modifier
                    .size(40.dp)
                    .border(2.dp, StatusWarning, CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isHigh) "H" else "L",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isHigh) Color.Black else Color.Gray
                    )
                }
            }
        } else {
            // Read-only LED
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isHigh) StatusActive else Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isHigh) "H" else "L",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isHigh) Color.Black else Color.Gray
                )
            }
        }

        Text(
            text = if (isHigh) "HIGH" else "LOW",
            style = MaterialTheme.typography.labelSmall,
            color = if (isHigh) StatusActive else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RelayOutputsCard(
    ioStatus: IoStatus,
    canControl: Boolean,
    isExecutingCommand: Boolean,
    onRelayToggle: (Int, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Relay Outputs",
                    style = MaterialTheme.typography.titleLarge
                )

                if (isExecutingCommand) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            if (!canControl) {
                Text(
                    text = "Relay control requires Service Mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // RO controls grid (2 rows of 4)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (channel in 1..4) {
                        RelayOutputControl(
                            channel = channel,
                            isOn = ioStatus.isOutputHigh(channel),
                            canControl = canControl,
                            isExecuting = isExecutingCommand,
                            onToggle = { on -> onRelayToggle(channel, on) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (channel in 5..8) {
                        RelayOutputControl(
                            channel = channel,
                            isOn = ioStatus.isOutputHigh(channel),
                            canControl = canControl,
                            isExecuting = isExecutingCommand,
                            onToggle = { on -> onRelayToggle(channel, on) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayOutputControl(
    channel: Int,
    isOn: Boolean,
    canControl: Boolean,
    isExecuting: Boolean,
    onToggle: (Boolean) -> Unit
) {
    // Pulsing animation for active relay indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Channel label
        Text(
            text = "RO$channel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Status indicator LED - shows current state with pulse when ON
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isOn) StatusNormal.copy(alpha = pulseAlpha)
                    else Color.DarkGray
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isOn) "ON" else "OFF",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isOn) Color.White else Color.Gray
            )
        }

        // Action button - labeled to indicate it's an action
        OutlinedButton(
            onClick = { onToggle(!isOn) },
            enabled = canControl && !isExecuting,
            modifier = Modifier.width(72.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isOn)
                    MaterialTheme.colorScheme.error
                else
                    StatusNormal
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = canControl && !isExecuting)
        ) {
            Text(
                text = if (isOn) "Turn OFF" else "Turn ON",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}
