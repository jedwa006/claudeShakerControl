package com.shakercontrol.app.ui.diagnostics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.ble.SafetyGateId
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.domain.model.CapabilityLevel
import com.shakercontrol.app.domain.model.ConnectionState
import com.shakercontrol.app.domain.model.PidData
import com.shakercontrol.app.domain.model.SystemStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents a safety gate's state.
 * @property id Gate ID (see SafetyGateId constants)
 * @property name Human-readable name
 * @property isEnabled Whether the gate is enabled (active) or bypassed
 * @property isSatisfied Whether the gate condition is met
 * @property canBypass Whether this gate can be bypassed (E-Stop cannot)
 */
data class SafetyGateState(
    val id: Int,
    val name: String,
    val isEnabled: Boolean,
    val isSatisfied: Boolean,
    val canBypass: Boolean
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val machineRepository: MachineRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DiagnosticsViewModel"
    }

    val systemStatus: StateFlow<SystemStatus> = machineRepository.systemStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SystemStatus.DISCONNECTED
        )

    val pidData: StateFlow<List<PidData>> = machineRepository.pidData
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isServiceMode: StateFlow<Boolean> = machineRepository.systemStatus
        .map { it.isServiceModeEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val hasCapabilityOverrides: StateFlow<Boolean> = machineRepository.capabilityOverrides
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Safety gates state
    private val _safetyGates = MutableStateFlow<List<SafetyGateState>>(emptyList())
    val safetyGates: StateFlow<List<SafetyGateState>> = _safetyGates.asStateFlow()

    private val _hasBypassedGates = MutableStateFlow(false)
    val hasBypassedGates: StateFlow<Boolean> = _hasBypassedGates.asStateFlow()

    init {
        // Refresh safety gates when connection state changes to LIVE
        viewModelScope.launch {
            machineRepository.systemStatus
                .map { it.connectionState }
                .collectLatest { state ->
                    if (state == ConnectionState.LIVE) {
                        refreshSafetyGates()
                    } else if (state == ConnectionState.DISCONNECTED) {
                        _safetyGates.value = emptyList()
                        _hasBypassedGates.value = false
                    }
                }
        }
    }

    /**
     * Fetch current safety gate states from the MCU.
     */
    fun refreshSafetyGates() {
        viewModelScope.launch {
            val result = machineRepository.getSafetyGates()
            result.onSuccess { (enableMask, statusMask) ->
                Log.d(TAG, "Safety gates: enableMask=0x${enableMask.toString(16)}, statusMask=0x${statusMask.toString(16)}")

                val gates = listOf(
                    SafetyGateState(
                        id = SafetyGateId.ESTOP.toInt(),
                        name = "E-Stop",
                        isEnabled = (enableMask and (1 shl SafetyGateId.ESTOP.toInt())) != 0,
                        isSatisfied = (statusMask and (1 shl SafetyGateId.ESTOP.toInt())) != 0,
                        canBypass = false  // E-Stop can NEVER be bypassed
                    ),
                    SafetyGateState(
                        id = SafetyGateId.DOOR.toInt(),
                        name = "Door Interlock",
                        isEnabled = (enableMask and (1 shl SafetyGateId.DOOR.toInt())) != 0,
                        isSatisfied = (statusMask and (1 shl SafetyGateId.DOOR.toInt())) != 0,
                        canBypass = true
                    ),
                    SafetyGateState(
                        id = SafetyGateId.HMI.toInt(),
                        name = "HMI Connection",
                        isEnabled = (enableMask and (1 shl SafetyGateId.HMI.toInt())) != 0,
                        isSatisfied = (statusMask and (1 shl SafetyGateId.HMI.toInt())) != 0,
                        canBypass = true
                    ),
                    SafetyGateState(
                        id = SafetyGateId.PID1_ONLINE.toInt(),
                        name = "PID 1 Online",
                        isEnabled = (enableMask and (1 shl SafetyGateId.PID1_ONLINE.toInt())) != 0,
                        isSatisfied = (statusMask and (1 shl SafetyGateId.PID1_ONLINE.toInt())) != 0,
                        canBypass = true
                    ),
                    SafetyGateState(
                        id = SafetyGateId.PID2_ONLINE.toInt(),
                        name = "PID 2 Online",
                        isEnabled = (enableMask and (1 shl SafetyGateId.PID2_ONLINE.toInt())) != 0,
                        isSatisfied = (statusMask and (1 shl SafetyGateId.PID2_ONLINE.toInt())) != 0,
                        canBypass = true
                    ),
                    SafetyGateState(
                        id = SafetyGateId.PID3_ONLINE.toInt(),
                        name = "PID 3 Online",
                        isEnabled = (enableMask and (1 shl SafetyGateId.PID3_ONLINE.toInt())) != 0,
                        isSatisfied = (statusMask and (1 shl SafetyGateId.PID3_ONLINE.toInt())) != 0,
                        canBypass = true
                    ),
                    SafetyGateState(
                        id = SafetyGateId.PID1_NO_PROBE_ERR.toInt(),
                        name = "PID 1 Probe OK",
                        isEnabled = (enableMask and (1 shl SafetyGateId.PID1_NO_PROBE_ERR.toInt())) != 0,
                        isSatisfied = (statusMask and (1 shl SafetyGateId.PID1_NO_PROBE_ERR.toInt())) != 0,
                        canBypass = true
                    ),
                    SafetyGateState(
                        id = SafetyGateId.PID2_NO_PROBE_ERR.toInt(),
                        name = "PID 2 Probe OK",
                        isEnabled = (enableMask and (1 shl SafetyGateId.PID2_NO_PROBE_ERR.toInt())) != 0,
                        isSatisfied = (statusMask and (1 shl SafetyGateId.PID2_NO_PROBE_ERR.toInt())) != 0,
                        canBypass = true
                    ),
                    SafetyGateState(
                        id = SafetyGateId.PID3_NO_PROBE_ERR.toInt(),
                        name = "PID 3 Probe OK",
                        isEnabled = (enableMask and (1 shl SafetyGateId.PID3_NO_PROBE_ERR.toInt())) != 0,
                        isSatisfied = (statusMask and (1 shl SafetyGateId.PID3_NO_PROBE_ERR.toInt())) != 0,
                        canBypass = true
                    )
                )

                _safetyGates.value = gates
                _hasBypassedGates.value = gates.any { it.canBypass && !it.isEnabled }
            }.onFailure { e ->
                Log.e(TAG, "Failed to get safety gates", e)
            }
        }
    }

    /**
     * Toggle a safety gate between enabled (active) and bypassed.
     */
    fun toggleSafetyGate(gateId: Int) {
        viewModelScope.launch {
            val currentGate = _safetyGates.value.find { it.id == gateId } ?: return@launch

            // Don't allow bypassing E-Stop
            if (!currentGate.canBypass) {
                Log.w(TAG, "Cannot bypass gate $gateId (E-Stop)")
                return@launch
            }

            val newEnabled = !currentGate.isEnabled
            Log.d(TAG, "Toggling gate $gateId: ${if (newEnabled) "enabling" else "bypassing"}")

            val result = machineRepository.setSafetyGate(gateId, newEnabled)
            result.onSuccess {
                // Refresh gates to get updated state
                refreshSafetyGates()
            }.onFailure { e ->
                Log.e(TAG, "Failed to set safety gate $gateId", e)
            }
        }
    }

    fun setCapabilityOverride(subsystem: String, level: CapabilityLevel) {
        viewModelScope.launch {
            machineRepository.setCapabilityOverride(subsystem, level)
        }
    }

    fun clearCapabilityOverrides() {
        viewModelScope.launch {
            machineRepository.clearCapabilityOverrides()
        }
    }
}
