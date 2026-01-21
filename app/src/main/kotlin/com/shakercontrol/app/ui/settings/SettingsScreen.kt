package com.shakercontrol.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shakercontrol.app.BuildConfig
import com.shakercontrol.app.data.ble.BleConnectionState
import com.shakercontrol.app.data.preferences.LastConnectedDevice
import com.shakercontrol.app.ui.theme.SemanticColors
import com.shakercontrol.app.ui.theme.ShakerControlTheme

/**
 * Settings screen.
 * Spec: docs/dashboard-sec-v1.md section 11 and docs/ui-copy-labels-v1.md section 8.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDevices: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val controllerVersion by viewModel.controllerVersion.collectAsStateWithLifecycle()
    val controllerBuildInfo by viewModel.controllerBuildInfo.collectAsStateWithLifecycle()
    val isServiceModeEnabled by viewModel.isServiceModeEnabled.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()
    val lastConnectedDevice by viewModel.lastConnectedDevice.collectAsStateWithLifecycle()
    val autoReconnectEnabled by viewModel.autoReconnectEnabled.collectAsStateWithLifecycle()
    val lazyPollingEnabled by viewModel.lazyPollingEnabled.collectAsStateWithLifecycle()
    val lazyPollingIdleTimeoutMinutes by viewModel.lazyPollingIdleTimeoutMinutes.collectAsStateWithLifecycle()
    val demoModeEnabled by viewModel.demoModeEnabled.collectAsStateWithLifecycle()

    SettingsScreenContent(
        controllerVersion = controllerVersion,
        controllerBuildInfo = controllerBuildInfo,
        isServiceModeEnabled = isServiceModeEnabled,
        onServiceModeToggle = viewModel::toggleServiceMode,
        connectionState = connectionState,
        connectedDeviceName = connectedDeviceName,
        lastConnectedDevice = lastConnectedDevice,
        autoReconnectEnabled = autoReconnectEnabled,
        onDisconnect = viewModel::disconnect,
        onReconnect = viewModel::reconnect,
        onForgetDevice = viewModel::forgetDevice,
        onAutoReconnectChanged = viewModel::setAutoReconnectEnabled,
        onNavigateToDevices = onNavigateToDevices,
        lazyPollingEnabled = lazyPollingEnabled,
        lazyPollingIdleTimeoutMinutes = lazyPollingIdleTimeoutMinutes,
        onLazyPollingEnabledChanged = viewModel::setLazyPollingEnabled,
        onLazyPollingIdleTimeoutChanged = viewModel::setLazyPollingIdleTimeoutMinutes,
        demoModeEnabled = demoModeEnabled,
        onDemoModeChanged = viewModel::setDemoModeEnabled
    )
}

@Composable
private fun SettingsScreenContent(
    controllerVersion: String,
    controllerBuildInfo: ControllerBuildInfo,
    isServiceModeEnabled: Boolean,
    onServiceModeToggle: () -> Unit,
    connectionState: BleConnectionState,
    connectedDeviceName: String?,
    lastConnectedDevice: LastConnectedDevice?,
    autoReconnectEnabled: Boolean,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onForgetDevice: () -> Unit,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onNavigateToDevices: () -> Unit,
    lazyPollingEnabled: Boolean = false,
    lazyPollingIdleTimeoutMinutes: Int = 3,
    onLazyPollingEnabledChanged: (Boolean) -> Unit = {},
    onLazyPollingIdleTimeoutChanged: (Int) -> Unit = {},
    demoModeEnabled: Boolean = false,
    onDemoModeChanged: (Boolean) -> Unit = {}
) {
    var selectedTheme by remember { mutableStateOf("Dark") }
    var showControllerInfoDialog by remember { mutableStateOf(false) }
    var showIdleTimeoutDropdown by remember { mutableStateOf(false) }
    var showDemoModeRestartDialog by remember { mutableStateOf(false) }
    var pendingDemoModeValue by remember { mutableStateOf(false) }

    // Controller build info popup dialog
    if (showControllerInfoDialog) {
        ControllerInfoDialog(
            buildInfo = controllerBuildInfo,
            onDismiss = { showControllerInfoDialog = false }
        )
    }

    // Demo mode restart confirmation dialog
    if (showDemoModeRestartDialog) {
        AlertDialog(
            onDismissRequest = { showDemoModeRestartDialog = false },
            title = { Text(if (pendingDemoModeValue) "Enable Demo Mode?" else "Disable Demo Mode?") },
            text = {
                Text(
                    if (pendingDemoModeValue)
                        "Demo mode simulates a connected controller for demonstrations. The app will need to be restarted for this change to take effect."
                    else
                        "The app will switch back to real BLE communication. The app will need to be restarted for this change to take effect."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDemoModeChanged(pendingDemoModeValue)
                        showDemoModeRestartDialog = false
                    }
                ) {
                    Text("Restart Later")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDemoModeRestartDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device section
        DeviceSection(
            connectionState = connectionState,
            connectedDeviceName = connectedDeviceName,
            lastConnectedDevice = lastConnectedDevice,
            autoReconnectEnabled = autoReconnectEnabled,
            onDisconnect = onDisconnect,
            onReconnect = onReconnect,
            onForgetDevice = onForgetDevice,
            onAutoReconnectChanged = onAutoReconnectChanged,
            onNavigateToDevices = onNavigateToDevices
        )

        // Theme setting
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingRow(
                    title = "Theme",
                    value = selectedTheme,
                    onClick = {
                        selectedTheme = if (selectedTheme == "Dark") "System" else "Dark"
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingRow(
                    title = "Time input format",
                    value = "mm:ss"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Export logs", style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { /* TODO: Future */ }) {
                        Text("Export")
                    }
                }
            }
        }

        // Lazy Polling section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lazy Polling",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Reduce PID polling frequency when system is idle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lazyPollingEnabled,
                        onCheckedChange = onLazyPollingEnabledChanged,
                        modifier = Modifier.testTag("LazyPollingSwitch")
                    )
                }

                if (lazyPollingEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Idle timeout dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Idle timeout", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Time before reducing polling rate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            OutlinedButton(onClick = { showIdleTimeoutDropdown = true }) {
                                Text(formatIdleTimeout(lazyPollingIdleTimeoutMinutes))
                            }
                            DropdownMenu(
                                expanded = showIdleTimeoutDropdown,
                                onDismissRequest = { showIdleTimeoutDropdown = false }
                            ) {
                                listOf(1, 2, 3, 5, 10, 15, 30, 60).forEach { minutes ->
                                    DropdownMenuItem(
                                        text = { Text(formatIdleTimeout(minutes)) },
                                        onClick = {
                                            onLazyPollingIdleTimeoutChanged(minutes)
                                            showIdleTimeoutDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Reduces coil whine from PID controllers when idle. Polling resumes at full speed during runs.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Service Mode section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Service Mode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Enables manual control of outputs and diagnostics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isServiceModeEnabled,
                        onCheckedChange = { onServiceModeToggle() },
                        modifier = Modifier.testTag("ServiceModeSwitch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SemanticColors.Warning,
                            checkedTrackColor = SemanticColors.Warning.copy(alpha = 0.5f)
                        )
                    )
                }
                if (isServiceModeEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = SemanticColors.Warning.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Service mode active - manual controls enabled",
                            style = MaterialTheme.typography.labelMedium,
                            color = SemanticColors.Warning,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Demo Mode section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Demo Mode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Simulate connected controller for demonstrations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = demoModeEnabled,
                        onCheckedChange = { newValue ->
                            pendingDemoModeValue = newValue
                            showDemoModeRestartDialog = true
                        },
                        modifier = Modifier.testTag("DemoModeSwitch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SemanticColors.Active,
                            checkedTrackColor = SemanticColors.Active.copy(alpha = 0.5f)
                        )
                    )
                }
                if (demoModeEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = SemanticColors.Active.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Demo mode active - using simulated controller",
                            style = MaterialTheme.typography.labelMedium,
                            color = SemanticColors.Active,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // About section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                SettingRow(title = "App version", value = BuildConfig.VERSION_NAME)
                SettingRow(
                    title = "Controller version",
                    value = controllerVersion,
                    onClick = if (controllerBuildInfo.isConnected) {
                        { showControllerInfoDialog = true }
                    } else null
                )
            }
        }
    }
}

/**
 * Dialog showing detailed controller build information.
 */
@Composable
private fun ControllerInfoDialog(
    buildInfo: ControllerBuildInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Controller Build Info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Firmware version", buildInfo.firmwareVersion ?: "Unknown")
                InfoRow("Build ID", buildInfo.buildId ?: "Unknown")
                InfoRow("Protocol version", buildInfo.protocolVersion?.toString() ?: "Unknown")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceSection(
    connectionState: BleConnectionState,
    connectedDeviceName: String?,
    lastConnectedDevice: LastConnectedDevice?,
    autoReconnectEnabled: Boolean,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onForgetDevice: () -> Unit,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onNavigateToDevices: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Device",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = when (connectionState) {
                        BleConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                        BleConnectionState.CONNECTING -> Icons.Default.BluetoothSearching
                        else -> Icons.Default.BluetoothDisabled
                    },
                    contentDescription = null,
                    tint = when (connectionState) {
                        BleConnectionState.CONNECTED -> SemanticColors.Normal
                        BleConnectionState.CONNECTING -> SemanticColors.Warning
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Connection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when (connectionState) {
                        BleConnectionState.CONNECTED -> "Connected"
                        BleConnectionState.CONNECTING,
                        BleConnectionState.DISCOVERING_SERVICES,
                        BleConnectionState.ENABLING_NOTIFICATIONS -> "Connecting..."
                        else -> "Disconnected"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (connectionState) {
                        BleConnectionState.CONNECTED -> SemanticColors.Normal
                        BleConnectionState.CONNECTING,
                        BleConnectionState.DISCOVERING_SERVICES,
                        BleConnectionState.ENABLING_NOTIFICATIONS -> SemanticColors.Warning
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // Connected device name or last known device
            if (connectionState == BleConnectionState.CONNECTED && connectedDeviceName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Device", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = connectedDeviceName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (lastConnectedDevice != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Last device", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = lastConnectedDevice.name ?: lastConnectedDevice.address,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Auto-reconnect toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Auto-reconnect", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Automatically reconnect to last device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoReconnectEnabled,
                    onCheckedChange = onAutoReconnectChanged,
                    modifier = Modifier.testTag("AutoReconnectSwitch")
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (connectionState) {
                    BleConnectionState.CONNECTED -> {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Disconnect")
                        }
                    }
                    BleConnectionState.CONNECTING,
                    BleConnectionState.DISCOVERING_SERVICES,
                    BleConnectionState.ENABLING_NOTIFICATIONS -> {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                    }
                    else -> {
                        if (lastConnectedDevice != null) {
                            Button(
                                onClick = onReconnect,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reconnect")
                            }
                            OutlinedButton(
                                onClick = onForgetDevice,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Forget")
                            }
                        }
                        OutlinedButton(
                            onClick = onNavigateToDevices,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format idle timeout for display.
 */
private fun formatIdleTimeout(minutes: Int): String {
    return if (minutes == 1) "1 minute" else "$minutes minutes"
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun SettingsScreenPreview() {
    ShakerControlTheme {
        Surface {
            SettingsScreenContent(
                controllerVersion = "Not connected",
                controllerBuildInfo = ControllerBuildInfo(null, null, null, false),
                isServiceModeEnabled = false,
                onServiceModeToggle = {},
                connectionState = BleConnectionState.DISCONNECTED,
                connectedDeviceName = null,
                lastConnectedDevice = LastConnectedDevice("AA:BB:CC:DD:EE:FF", "NuNuShaker-001"),
                autoReconnectEnabled = true,
                onDisconnect = {},
                onReconnect = {},
                onForgetDevice = {},
                onAutoReconnectChanged = {},
                onNavigateToDevices = {}
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun SettingsScreenConnectedPreview() {
    ShakerControlTheme {
        Surface {
            SettingsScreenContent(
                controllerVersion = "0.2.0+26011901",
                controllerBuildInfo = ControllerBuildInfo("0.2.0", "26011901", 1, true),
                isServiceModeEnabled = false,
                onServiceModeToggle = {},
                connectionState = BleConnectionState.CONNECTED,
                connectedDeviceName = "NuNuShaker-001",
                lastConnectedDevice = LastConnectedDevice("AA:BB:CC:DD:EE:FF", "NuNuShaker-001"),
                autoReconnectEnabled = true,
                onDisconnect = {},
                onReconnect = {},
                onForgetDevice = {},
                onAutoReconnectChanged = {},
                onNavigateToDevices = {}
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun SettingsScreenServiceModePreview() {
    ShakerControlTheme {
        Surface {
            SettingsScreenContent(
                controllerVersion = "0.2.0+26011901",
                controllerBuildInfo = ControllerBuildInfo("0.2.0", "26011901", 1, true),
                isServiceModeEnabled = true,
                onServiceModeToggle = {},
                connectionState = BleConnectionState.CONNECTED,
                connectedDeviceName = "NuNuShaker-001",
                lastConnectedDevice = LastConnectedDevice("AA:BB:CC:DD:EE:FF", "NuNuShaker-001"),
                autoReconnectEnabled = true,
                onDisconnect = {},
                onReconnect = {},
                onForgetDevice = {},
                onAutoReconnectChanged = {},
                onNavigateToDevices = {}
            )
        }
    }
}
