package com.shakercontrol.app.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.ble.BleConnectionState
import com.shakercontrol.app.data.ble.BleManager
import com.shakercontrol.app.data.ble.ConnectedDevice
import com.shakercontrol.app.data.ble.ScannedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val bleManager: BleManager
) : ViewModel() {

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

    val isBluetoothEnabled: Boolean
        get() = bleManager.isBluetoothEnabled()

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connect(address: String) {
        bleManager.stopScan()
        bleManager.connect(address)
    }

    fun disconnect() {
        bleManager.disconnect()
    }
}
