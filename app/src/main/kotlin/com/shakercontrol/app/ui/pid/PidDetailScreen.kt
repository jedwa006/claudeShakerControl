package com.shakercontrol.app.ui.pid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shakercontrol.app.domain.model.CapabilityLevel
import com.shakercontrol.app.domain.model.ConnectionState
import com.shakercontrol.app.domain.model.PidData
import com.shakercontrol.app.domain.model.PidMode
import com.shakercontrol.app.ui.components.PidStatusLeds
import com.shakercontrol.app.ui.theme.ShakerControlTheme
import kotlinx.coroutines.flow.collectLatest

/**
 * PID detail page with setpoint editing and mode control.
 * Spec: docs/dashboard-sec-v1.md section 7.
 */
@Composable
fun PidDetailScreen(
    pidId: Int,
    onNavigateBack: () -> Unit,
    viewModel: PidDetailViewModel = hiltViewModel()
) {
    val pidData by viewModel.getPidData(pidId).collectAsStateWithLifecycle(initialValue = null)
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isExecutingCommand by viewModel.isExecutingCommand.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Collect UI events for snackbar
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is PidUiEvent.CommandError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is PidUiEvent.CommandSuccess -> {
                    // Optionally show success feedback
                }
            }
        }
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
        PidDetailContent(
            pidId = pidId,
            pidData = pidData,
            connectionState = connectionState,
            isExecutingCommand = isExecutingCommand,
            onSetSetpoint = { setpoint -> viewModel.setSetpoint(pidId, setpoint) },
            onSetMode = { mode -> viewModel.setMode(pidId, mode) },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun PidDetailContent(
    pidId: Int,
    pidData: PidData?,
    connectionState: ConnectionState,
    isExecutingCommand: Boolean,
    onSetSetpoint: (Float) -> Unit,
    onSetMode: (PidMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val pidNames = mapOf(
        1 to "Axle bearings",
        2 to "Orbital bearings",
        3 to "LN2 line"
    )

    val isConnected = connectionState == ConnectionState.LIVE

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
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
                        isStale = pidData.isStale,
                        al1Active = pidData.alarmRelays.al1,
                        al2Active = pidData.alarmRelays.al2
                    )

                    // Show alarm details if any alarms are active
                    if (pidData.hasActiveAlarm) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Alarm Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                val alarmText = buildString {
                                    if (pidData.alarmRelays.al1) append("AL1 triggered")
                                    if (pidData.alarmRelays.al1 && pidData.alarmRelays.al2) append(" • ")
                                    if (pidData.alarmRelays.al2) append("AL2 triggered")
                                }
                                Text(
                                    text = alarmText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
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

        // Setpoint control
        SetpointControlCard(
            currentSetpoint = pidData?.setpointValue ?: 0f,
            isConnected = isConnected,
            isExecutingCommand = isExecutingCommand,
            onSetSetpoint = onSetSetpoint
        )

        // Mode control
        ModeControlCard(
            currentMode = pidData?.mode ?: PidMode.STOP,
            isConnected = isConnected,
            isExecutingCommand = isExecutingCommand,
            onSetMode = onSetMode
        )

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

@Composable
private fun SetpointControlCard(
    currentSetpoint: Float,
    isConnected: Boolean,
    isExecutingCommand: Boolean,
    onSetSetpoint: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputValue by remember(currentSetpoint) { mutableStateOf(String.format("%.1f", currentSetpoint)) }
    var isEditing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val canEdit = isConnected && !isExecutingCommand
    val hasChanged = try {
        inputValue.toFloat() != currentSetpoint
    } catch (e: NumberFormatException) {
        false
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Setpoint control",
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

            if (!isConnected) {
                Text(
                    text = "Connect to device to edit setpoint",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = {
                            inputValue = it
                            isEditing = true
                        },
                        label = { Text("Setpoint (°C)") },
                        enabled = canEdit,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (hasChanged) {
                                    try {
                                        val newSetpoint = inputValue.toFloat()
                                        onSetSetpoint(newSetpoint)
                                        isEditing = false
                                    } catch (e: NumberFormatException) {
                                        // Reset to current value
                                        inputValue = String.format("%.1f", currentSetpoint)
                                    }
                                }
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            try {
                                val newSetpoint = inputValue.toFloat()
                                onSetSetpoint(newSetpoint)
                                isEditing = false
                            } catch (e: NumberFormatException) {
                                // Reset to current value
                                inputValue = String.format("%.1f", currentSetpoint)
                            }
                        },
                        enabled = canEdit && hasChanged
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Apply",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Current: ${String.format("%.1f", currentSetpoint)}°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeControlCard(
    currentMode: PidMode,
    isConnected: Boolean,
    isExecutingCommand: Boolean,
    onSetMode: (PidMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(PidMode.STOP, PidMode.MANUAL, PidMode.AUTO)
    val canEdit = isConnected && !isExecutingCommand

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mode control",
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

            if (!isConnected) {
                Text(
                    text = "Connect to device to change mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            onClick = {
                                if (mode != currentMode) {
                                    onSetMode(mode)
                                }
                            },
                            selected = mode == currentMode,
                            enabled = canEdit,
                            colors = if (mode == currentMode) {
                                SegmentedButtonDefaults.colors(
                                    activeContainerColor = when (mode) {
                                        PidMode.STOP -> MaterialTheme.colorScheme.errorContainer
                                        PidMode.MANUAL -> MaterialTheme.colorScheme.tertiaryContainer
                                        PidMode.AUTO -> MaterialTheme.colorScheme.primaryContainer
                                        PidMode.PROGRAM -> MaterialTheme.colorScheme.secondaryContainer
                                    }
                                )
                            } else {
                                SegmentedButtonDefaults.colors()
                            }
                        ) {
                            Text(mode.displayName)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val modeDescription = when (currentMode) {
                    PidMode.STOP -> "Controller output is disabled."
                    PidMode.MANUAL -> "Manual output control. Setpoint has no effect."
                    PidMode.AUTO -> "Automatic PID control to reach setpoint."
                    PidMode.PROGRAM -> "Following programmed profile."
                }
                Text(
                    text = modeDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 800)
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
                ),
                connectionState = ConnectionState.LIVE,
                isExecutingCommand = false,
                onSetSetpoint = {},
                onSetMode = {}
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 800)
@Composable
private fun PidDetailScreenDisconnectedPreview() {
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
                ),
                connectionState = ConnectionState.DISCONNECTED,
                isExecutingCommand = false,
                onSetSetpoint = {},
                onSetMode = {}
            )
        }
    }
}
