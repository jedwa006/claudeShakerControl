package com.shakercontrol.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    val isServiceModeEnabled by viewModel.isServiceModeEnabled.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()
    val lastConnectedDevice by viewModel.lastConnectedDevice.collectAsStateWithLifecycle()
    val autoReconnectEnabled by viewModel.autoReconnectEnabled.collectAsStateWithLifecycle()

    SettingsScreenContent(
        controllerVersion = controllerVersion,
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
        onNavigateToDevices = onNavigateToDevices
    )
}

@Composable
private fun SettingsScreenContent(
    controllerVersion: String,
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
    onNavigateToDevices: () -> Unit
) {
    var selectedTheme by remember { mutableStateOf("Dark") }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        // About section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                SettingRow(title = "App version", value = "0.1.0")
                SettingRow(title = "Controller version", value = controllerVersion)
            }
        }
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

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun SettingsScreenPreview() {
    ShakerControlTheme {
        Surface {
            SettingsScreenContent(
                controllerVersion = "0.2.0+26011901",
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
