package com.shakercontrol.app.ui.settings

import android.util.Log
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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

    init {
        // Observe MCU idle timeout and sync to local preferences
        // MCU is the source of truth - when we connect, we read the MCU's value
        viewModelScope.launch {
            machineRepository.mcuIdleTimeoutMinutes
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { mcuMinutes ->
                    Log.d(TAG, "MCU idle timeout synced: $mcuMinutes minutes")
                    // Update local preferences to match MCU value
                    if (mcuMinutes == 0) {
                        // MCU has lazy polling disabled
                        devicePreferences.setLazyPollingEnabled(false)
                    } else {
                        // MCU has lazy polling enabled with this timeout
                        devicePreferences.setLazyPollingEnabled(true)
                        devicePreferences.setLazyPollingIdleTimeoutMinutes(mcuMinutes)
                    }
                }
        }
    }

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

    // ==========================================
    // Lazy Polling Settings
    // ==========================================

    val lazyPollingEnabled: StateFlow<Boolean> = devicePreferences.lazyPollingEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val lazyPollingIdleTimeoutMinutes: StateFlow<Int> = devicePreferences.lazyPollingIdleTimeoutMinutes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DevicePreferences.DEFAULT_IDLE_TIMEOUT_MINUTES
        )

    fun setLazyPollingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            devicePreferences.setLazyPollingEnabled(enabled)
            // Send command to firmware: 0 = disabled, else use configured timeout
            val timeout = if (enabled) lazyPollingIdleTimeoutMinutes.value else 0
            Log.d(TAG, "setLazyPollingEnabled: enabled=$enabled, sending timeout=$timeout minutes")
            val result = machineRepository.setIdleTimeout(timeout)
            Log.d(TAG, "setIdleTimeout result: ${result.isSuccess}, ${result.exceptionOrNull()?.message ?: "OK"}")
        }
    }

    fun setLazyPollingIdleTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            devicePreferences.setLazyPollingIdleTimeoutMinutes(minutes)
            // Send command to firmware if lazy polling is enabled
            if (lazyPollingEnabled.value) {
                Log.d(TAG, "setLazyPollingIdleTimeoutMinutes: sending timeout=$minutes minutes")
                val result = machineRepository.setIdleTimeout(minutes)
                Log.d(TAG, "setIdleTimeout result: ${result.isSuccess}, ${result.exceptionOrNull()?.message ?: "OK"}")
            } else {
                Log.d(TAG, "setLazyPollingIdleTimeoutMinutes: lazy polling disabled, not sending")
            }
        }
    }

    /**
     * Sync local lazy polling settings to MCU after connection established.
     * Called automatically when connection state becomes CONNECTED.
     */
    private fun syncLazyPollingToMcu() {
        viewModelScope.launch {
            val enabled = lazyPollingEnabled.value
            val timeout = if (enabled) lazyPollingIdleTimeoutMinutes.value else 0
            Log.d(TAG, "syncLazyPollingToMcu: enabled=$enabled, sending timeout=$timeout minutes")
            val result = machineRepository.setIdleTimeout(timeout)
            Log.d(TAG, "syncLazyPollingToMcu result: ${result.isSuccess}, ${result.exceptionOrNull()?.message ?: "OK"}")
        }
    }

    // ==========================================
    // Demo Mode Settings
    // ==========================================

    val demoModeEnabled: StateFlow<Boolean> = devicePreferences.demoModeEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Toggle demo mode. Requires app restart to take effect.
     */
    fun setDemoModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "setDemoModeEnabled: $enabled (requires restart)")
            devicePreferences.setDemoModeEnabled(enabled)
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
