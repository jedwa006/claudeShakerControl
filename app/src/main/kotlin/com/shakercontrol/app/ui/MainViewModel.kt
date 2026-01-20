package com.shakercontrol.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.domain.model.PidMode
import com.shakercontrol.app.domain.model.SystemStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val machineRepository: MachineRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    val systemStatus: StateFlow<SystemStatus> = machineRepository.systemStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SystemStatus.DISCONNECTED
        )

    fun enableServiceMode() {
        viewModelScope.launch {
            machineRepository.enableServiceMode()
        }
    }

    fun disableServiceMode() {
        viewModelScope.launch {
            machineRepository.disableServiceMode()
        }
    }

    /**
     * Initiate connection to the last known device.
     */
    fun connect() {
        viewModelScope.launch {
            // Get saved device address from repository and connect
            // For now, just start scan - actual connect needs device address
            machineRepository.startScan()
        }
    }

    /**
     * Disconnect from current device.
     */
    fun disconnect() {
        viewModelScope.launch {
            machineRepository.disconnect()
        }
    }

    /**
     * Reconnect to the last known device.
     */
    fun reconnect() {
        viewModelScope.launch {
            // This would use saved device address for auto-reconnect
            // For deep link, we can try to reconnect via scan + connect
            machineRepository.startScan()
        }
    }

    // ==========================================
    // Test Deep Link Actions (for automated testing)
    // ==========================================

    /**
     * Set relay state via deep link.
     * @param channel Relay channel (1-8)
     * @param on true=ON, false=OFF
     */
    fun setRelay(channel: Int, on: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "Deep link: setRelay(channel=$channel, on=$on)")
            val result = machineRepository.setRelay(channel, on)
            result.fold(
                onSuccess = { Log.d(TAG, "Relay $channel set to ${if (on) "ON" else "OFF"}") },
                onFailure = { Log.e(TAG, "Failed to set relay $channel: ${it.message}") }
            )
        }
    }

    /**
     * Set capability level via deep link.
     * @param subsystemId Subsystem ID (0=PID1, 1=PID2, 2=PID3, 3=E-Stop, 4=Door, 5=LN2, 6=Motor)
     * @param level Capability level (0=NOT_PRESENT, 1=OPTIONAL, 2=REQUIRED)
     */
    fun setCapability(subsystemId: Int, level: Int) {
        viewModelScope.launch {
            Log.d(TAG, "Deep link: setCapability(subsystem=$subsystemId, level=$level)")
            val result = machineRepository.setCapability(subsystemId, level)
            result.fold(
                onSuccess = { Log.d(TAG, "Capability $subsystemId set to level $level") },
                onFailure = { Log.e(TAG, "Failed to set capability $subsystemId: ${it.message}") }
            )
        }
    }

    /**
     * Set safety gate state via deep link.
     * @param gateId Gate ID (0=E-Stop, 1=Door, 2=HMI, 3-5=PID Online, 6-8=PID No Probe Error)
     * @param enabled true=enabled (gate active), false=bypassed
     */
    fun setSafetyGate(gateId: Int, enabled: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "Deep link: setSafetyGate(gate=$gateId, enabled=$enabled)")
            val result = machineRepository.setSafetyGate(gateId, enabled)
            result.fold(
                onSuccess = { Log.d(TAG, "Safety gate $gateId ${if (enabled) "enabled" else "bypassed"}") },
                onFailure = { Log.e(TAG, "Failed to set safety gate $gateId: ${it.message}") }
            )
        }
    }

    /**
     * Toggle chamber light (CH7) via deep link.
     */
    fun toggleLight() {
        viewModelScope.launch {
            Log.d(TAG, "Deep link: toggleLight")
            // Read current state from IO status and toggle
            val ioStatus = machineRepository.ioStatus.value
            val currentState = ioStatus.isOutputHigh(7)
            val result = machineRepository.setRelay(7, !currentState)
            result.fold(
                onSuccess = { Log.d(TAG, "Light toggled to ${if (!currentState) "ON" else "OFF"}") },
                onFailure = { Log.e(TAG, "Failed to toggle light: ${it.message}") }
            )
        }
    }

    /**
     * Toggle door lock (CH6) via deep link.
     */
    fun toggleDoor() {
        viewModelScope.launch {
            Log.d(TAG, "Deep link: toggleDoor")
            // Read current state from IO status and toggle
            val ioStatus = machineRepository.ioStatus.value
            val currentState = ioStatus.isOutputHigh(6)
            val result = machineRepository.setRelay(6, !currentState)
            result.fold(
                onSuccess = { Log.d(TAG, "Door lock toggled to ${if (!currentState) "LOCKED" else "UNLOCKED"}") },
                onFailure = { Log.e(TAG, "Failed to toggle door lock: ${it.message}") }
            )
        }
    }

    /**
     * Start chilldown (enable LN2 controller in AUTO mode) via deep link.
     */
    fun startChilldown() {
        viewModelScope.launch {
            Log.d(TAG, "Deep link: startChilldown")
            val result = machineRepository.setMode(1, PidMode.AUTO)
            result.fold(
                onSuccess = { Log.d(TAG, "Chilldown started (LN2 controller in AUTO)") },
                onFailure = { Log.e(TAG, "Failed to start chilldown: ${it.message}") }
            )
        }
    }
}
