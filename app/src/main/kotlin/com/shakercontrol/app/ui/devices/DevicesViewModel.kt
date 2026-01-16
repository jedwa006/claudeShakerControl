package com.shakercontrol.app.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.ble.BleConnectionState
import com.shakercontrol.app.data.ble.BleManager
import com.shakercontrol.app.data.ble.ConnectedDevice
import com.shakercontrol.app.data.ble.DisconnectEvent
import com.shakercontrol.app.data.ble.ScannedDevice
import com.shakercontrol.app.data.preferences.DevicePreferences
import com.shakercontrol.app.data.preferences.LastConnectedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DevicesUiEvent {
    data class ConnectionLost(val deviceName: String, val willReconnect: Boolean) : DevicesUiEvent()
    data class ReconnectFailed(val deviceName: String) : DevicesUiEvent()
    data object ReconnectSucceeded : DevicesUiEvent()
}

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val bleManager: BleManager,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    companion object {
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }

    private var reconnectAttempts = 0
    private var isReconnecting = false

    val scannedDevices: StateFlow<List<ScannedDevice>> = bleManager.scannedDevices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isScanning: StateFlow<Boolean> = bleManager.isScanning
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BleConnectionState.DISCONNECTED
        )

    val connectedDevice: StateFlow<ConnectedDevice?> = bleManager.connectedDevice
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val lastConnectedDevice: StateFlow<LastConnectedDevice?> = devicePreferences.lastConnectedDevice
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val autoReconnectEnabled: StateFlow<Boolean> = devicePreferences.autoReconnectEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _uiEvents = MutableSharedFlow<DevicesUiEvent>()
    val uiEvents: SharedFlow<DevicesUiEvent> = _uiEvents.asSharedFlow()

    val isBluetoothEnabled: Boolean
        get() = bleManager.isBluetoothEnabled()

    init {
        // Monitor disconnect events for auto-reconnection
        viewModelScope.launch {
            bleManager.disconnectEvents.collect { event ->
                handleDisconnect(event)
            }
        }

        // Save device info when connected
        viewModelScope.launch {
            bleManager.connectedDevice.collect { device ->
                if (device != null) {
                    devicePreferences.saveLastConnectedDevice(device.address, device.name)
                    reconnectAttempts = 0
                    isReconnecting = false
                }
            }
        }
    }

    private suspend fun handleDisconnect(event: DisconnectEvent) {
        if (event.wasUserInitiated) {
            // User disconnected, don't auto-reconnect
            return
        }

        val shouldReconnect = autoReconnectEnabled.value && reconnectAttempts < MAX_RECONNECT_ATTEMPTS

        // Notify UI of connection loss
        _uiEvents.emit(DevicesUiEvent.ConnectionLost(event.name, shouldReconnect))

        if (shouldReconnect) {
            attemptReconnect(event.address, event.name)
        }
    }

    private suspend fun attemptReconnect(address: String, name: String) {
        isReconnecting = true
        reconnectAttempts++

        delay(RECONNECT_DELAY_MS)

        if (bleManager.connectionState.value == BleConnectionState.DISCONNECTED) {
            bleManager.connect(address, nameHint = name)

            // Wait for connection result
            delay(5000) // Give it time to connect

            if (bleManager.connectionState.value == BleConnectionState.CONNECTED) {
                _uiEvents.emit(DevicesUiEvent.ReconnectSucceeded)
                reconnectAttempts = 0
            } else if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                // Try again
                attemptReconnect(address, name)
            } else {
                // Give up
                _uiEvents.emit(DevicesUiEvent.ReconnectFailed(name))
                isReconnecting = false
            }
        }
    }

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connect(address: String, nameHint: String? = null) {
        reconnectAttempts = 0
        bleManager.stopScan()
        bleManager.connect(address, nameHint)
    }

    fun connectToLastDevice() {
        viewModelScope.launch {
            val lastDevice = lastConnectedDevice.value
            if (lastDevice != null && bleManager.connectionState.value == BleConnectionState.DISCONNECTED) {
                connect(lastDevice.address, nameHint = lastDevice.name)
            }
        }
    }

    fun disconnect() {
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS // Prevent auto-reconnect
        bleManager.disconnect(userInitiated = true)
    }

    fun forgetDevice() {
        viewModelScope.launch {
            disconnect()
            devicePreferences.clearLastConnectedDevice()
        }
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            devicePreferences.setAutoReconnectEnabled(enabled)
        }
    }
}
