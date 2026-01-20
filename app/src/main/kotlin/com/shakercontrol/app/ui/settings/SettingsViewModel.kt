package com.shakercontrol.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.ble.BleConnectionState
import com.shakercontrol.app.data.ble.BleManager
import com.shakercontrol.app.data.preferences.DevicePreferences
import com.shakercontrol.app.data.preferences.LastConnectedDevice
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.domain.model.ConnectionState
import com.shakercontrol.app.domain.model.SystemStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Controller build information for display in popup dialog.
 */
data class ControllerBuildInfo(
    val firmwareVersion: String?,
    val buildId: String?,
    val protocolVersion: Int?,
    val isConnected: Boolean
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val machineRepository: MachineRepository,
    private val bleManager: BleManager,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    val systemStatus: StateFlow<SystemStatus> = machineRepository.systemStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SystemStatus.DISCONNECTED
        )

    /**
     * Controller version string for display.
     * Shows full firmware version (with build ID) when connected, "Not connected" otherwise.
     */
    val controllerVersion: StateFlow<String> = machineRepository.systemStatus
        .map { status ->
            when {
                status.connectionState != ConnectionState.LIVE -> "Not connected"
                !status.fullFirmwareVersion.isNullOrBlank() -> status.fullFirmwareVersion!!
                else -> "Unknown"
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Not connected"
        )

    /**
     * Controller build info for the popup dialog.
     */
    val controllerBuildInfo: StateFlow<ControllerBuildInfo> = machineRepository.systemStatus
        .map { status ->
            ControllerBuildInfo(
                firmwareVersion = status.firmwareVersion,
                buildId = status.firmwareBuildId,
                protocolVersion = status.protocolVersion,
                isConnected = status.connectionState == ConnectionState.LIVE
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ControllerBuildInfo(null, null, null, false)
        )

    /**
     * Service mode state.
     */
    val isServiceModeEnabled: StateFlow<Boolean> = machineRepository.systemStatus
        .map { it.isServiceModeEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Toggle service mode on/off.
     */
    fun toggleServiceMode() {
        viewModelScope.launch {
            if (isServiceModeEnabled.value) {
                machineRepository.disableServiceMode()
            } else {
                machineRepository.enableServiceMode()
            }
        }
    }

    // ==========================================
    // Device management (moved from Devices screen)
    // ==========================================

    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BleConnectionState.DISCONNECTED
        )

    val connectedDeviceName: StateFlow<String?> = bleManager.connectedDevice
        .map { it?.name }
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

    fun disconnect() {
        bleManager.disconnect(userInitiated = true)
    }

    fun reconnect() {
        viewModelScope.launch {
            val lastDevice = lastConnectedDevice.value
            if (lastDevice != null && bleManager.connectionState.value == BleConnectionState.DISCONNECTED) {
                bleManager.connect(lastDevice.address, nameHint = lastDevice.name)
            }
        }
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
