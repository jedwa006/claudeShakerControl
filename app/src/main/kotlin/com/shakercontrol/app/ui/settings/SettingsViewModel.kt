package com.shakercontrol.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val machineRepository: MachineRepository
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
}
