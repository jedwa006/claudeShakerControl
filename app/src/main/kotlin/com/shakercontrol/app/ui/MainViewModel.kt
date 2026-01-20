package com.shakercontrol.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.repository.MachineRepository
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
}
