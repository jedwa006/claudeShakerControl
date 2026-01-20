package com.shakercontrol.app.ui.devices

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shakercontrol.app.data.ble.BleConnectionState
import com.shakercontrol.app.data.ble.ScannedDevice
import com.shakercontrol.app.data.preferences.LastConnectedDevice
import com.shakercontrol.app.ui.theme.ShakerControlTheme

/**
 * Devices screen for BLE scan/connect.
 * Spec: docs/dashboard-sec-v1.md section 6 and docs/ui-copy-labels-v1.md section 4.
 */
@Composable
fun DevicesScreen(
    onNavigateBack: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()
    val lastConnectedDevice by viewModel.lastConnectedDevice.collectAsStateWithLifecycle()
    val autoReconnectEnabled by viewModel.autoReconnectEnabled.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // BLE permissions for Android 12+
    val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    fun hasBluetoothPermissions(): Boolean {
        return blePermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED
        }
    }

    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.startScan()
            permissionDenied = false
        } else {
            permissionDenied = true
        }
    }

    val onScanWithPermissionCheck: () -> Unit = {
        if (hasBluetoothPermissions()) {
            viewModel.startScan()
        } else {
            permissionLauncher.launch(blePermissions)
        }
    }

    // Collect UI events for feedback
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is DevicesUiEvent.ConnectionLost -> {
                    val message = if (event.willReconnect) {
                        "Connection lost to ${event.deviceName}. Reconnecting..."
                    } else {
                        "Connection lost to ${event.deviceName}."
                    }
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short
                    )
                }
                is DevicesUiEvent.ReconnectFailed -> {
                    snackbarHostState.showSnackbar(
                        message = "Failed to reconnect to ${event.deviceName}.",
                        duration = SnackbarDuration.Short
                    )
                }
                is DevicesUiEvent.ReconnectSucceeded -> {
                    snackbarHostState.showSnackbar(
                        message = "Reconnected successfully.",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DevicesContent(
            scannedDevices = scannedDevices,
            isScanning = isScanning,
            connectionState = connectionState,
            connectedDeviceName = connectedDevice?.name,
            connectedDeviceAddress = connectedDevice?.address,
            lastConnectedDevice = lastConnectedDevice,
            autoReconnectEnabled = autoReconnectEnabled,
            isBluetoothEnabled = viewModel.isBluetoothEnabled,
            permissionDenied = permissionDenied,
            onScanClick = onScanWithPermissionCheck,
            onStopScanClick = { viewModel.stopScan() },
            onDeviceClick = { viewModel.connect(it) },
            onDisconnectClick = { viewModel.disconnect() },
            onReconnectClick = { viewModel.connectToLastDevice() },
            onForgetClick = { viewModel.forgetDevice() },
            onAutoReconnectChange = { viewModel.setAutoReconnectEnabled(it) }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun DevicesContent(
    scannedDevices: List<ScannedDevice>,
    isScanning: Boolean,
    connectionState: BleConnectionState,
    connectedDeviceName: String?,
    connectedDeviceAddress: String?,
    lastConnectedDevice: LastConnectedDevice?,
    autoReconnectEnabled: Boolean,
    isBluetoothEnabled: Boolean,
    permissionDenied: Boolean = false,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onDeviceClick: (String) -> Unit,
    onDisconnectClick: () -> Unit,
    onReconnectClick: () -> Unit,
    onForgetClick: () -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Bluetooth status warning
        if (!isBluetoothEnabled) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Bluetooth is disabled. Please enable Bluetooth to connect to devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Permission denied warning
        if (permissionDenied) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Bluetooth permissions are required to scan for devices. Please grant permissions in app settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Connected device section
        if (connectionState != BleConnectionState.DISCONNECTED) {
            ConnectedDeviceCard(
                deviceName = connectedDeviceName ?: "Connecting...",
                deviceAddress = connectedDeviceAddress ?: "",
                connectionState = connectionState,
                onDisconnectClick = onDisconnectClick
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Last connected device section (when disconnected)
        if (connectionState == BleConnectionState.DISCONNECTED && lastConnectedDevice != null) {
            LastConnectedDeviceCard(
                deviceName = lastConnectedDevice.name,
                deviceAddress = lastConnectedDevice.address,
                onReconnectClick = onReconnectClick,
                onForgetClick = onForgetClick,
                enabled = isBluetoothEnabled
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Available devices section
        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Available devices",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isScanning) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            OutlinedButton(onClick = onStopScanClick) {
                                Text("Stop")
                            }
                        }
                    } else {
                        Button(
                            onClick = onScanClick,
                            enabled = isBluetoothEnabled && connectionState == BleConnectionState.DISCONNECTED
                        ) {
                            Text("Scan")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (scannedDevices.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isScanning) "Scanning..." else "No devices found.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Make sure the controller is powered and in range.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Device list
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scannedDevices) { device ->
                            DeviceListItem(
                                device = device,
                                onClick = { onDeviceClick(device.address) },
                                enabled = connectionState == BleConnectionState.DISCONNECTED
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Settings row
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-reconnect",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = autoReconnectEnabled,
                        onCheckedChange = onAutoReconnectChange
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Connection state:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = connectionState.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (connectionState) {
                            BleConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                            BleConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedDeviceCard(
    deviceName: String,
    deviceAddress: String,
    connectionState: BleConnectionState,
    onDisconnectClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (connectionState) {
                            BleConnectionState.CONNECTED -> "Connected"
                            BleConnectionState.CONNECTING -> "Connecting..."
                            BleConnectionState.DISCOVERING_SERVICES -> "Discovering services..."
                            BleConnectionState.ENABLING_NOTIFICATIONS -> "Setting up..."
                            else -> "Unknown"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (deviceAddress.isNotEmpty()) {
                        Text(
                            text = deviceAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                if (connectionState == BleConnectionState.CONNECTED) {
                    OutlinedButton(onClick = onDisconnectClick) {
                        Text("Disconnect")
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun LastConnectedDeviceCard(
    deviceName: String,
    deviceAddress: String,
    onReconnectClick: () -> Unit,
    onForgetClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Last connected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = deviceAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onForgetClick) {
                        Text("Forget")
                    }
                    Button(
                        onClick = onReconnectClick,
                        enabled = enabled
                    ) {
                        Text("Reconnect")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceListItem(
    device: ScannedDevice,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = getRssiColor(device.rssi)
                )
                Text(
                    text = getRssiLabel(device.rssi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getRssiColor(rssi: Int) = when {
    rssi >= -50 -> MaterialTheme.colorScheme.primary
    rssi >= -70 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private fun getRssiLabel(rssi: Int) = when {
    rssi >= -50 -> "Excellent"
    rssi >= -60 -> "Good"
    rssi >= -70 -> "Fair"
    else -> "Weak"
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun DevicesScreenPreview() {
    ShakerControlTheme {
        Surface {
            DevicesContent(
                scannedDevices = listOf(
                    ScannedDevice("AA:BB:CC:DD:EE:01", "SYS-CTRL-001", -45),
                    ScannedDevice("AA:BB:CC:DD:EE:02", "SYS-CTRL-002", -68),
                    ScannedDevice("AA:BB:CC:DD:EE:03", "SYS-CTRL-003", -82)
                ),
                isScanning = false,
                connectionState = BleConnectionState.DISCONNECTED,
                connectedDeviceName = null,
                connectedDeviceAddress = null,
                lastConnectedDevice = LastConnectedDevice("AA:BB:CC:DD:EE:01", "SYS-CTRL-001"),
                autoReconnectEnabled = true,
                isBluetoothEnabled = true,
                onScanClick = {},
                onStopScanClick = {},
                onDeviceClick = {},
                onDisconnectClick = {},
                onReconnectClick = {},
                onForgetClick = {},
                onAutoReconnectChange = {}
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun DevicesScreenConnectedPreview() {
    ShakerControlTheme {
        Surface {
            DevicesContent(
                scannedDevices = emptyList(),
                isScanning = false,
                connectionState = BleConnectionState.CONNECTED,
                connectedDeviceName = "SYS-CTRL-001",
                connectedDeviceAddress = "AA:BB:CC:DD:EE:01",
                lastConnectedDevice = LastConnectedDevice("AA:BB:CC:DD:EE:01", "SYS-CTRL-001"),
                autoReconnectEnabled = true,
                isBluetoothEnabled = true,
                onScanClick = {},
                onStopScanClick = {},
                onDeviceClick = {},
                onDisconnectClick = {},
                onReconnectClick = {},
                onForgetClick = {},
                onAutoReconnectChange = {}
            )
        }
    }
}
