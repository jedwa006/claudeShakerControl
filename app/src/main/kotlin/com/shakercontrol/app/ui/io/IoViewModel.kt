package com.shakercontrol.app.ui.io

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.domain.model.ConnectionState
import com.shakercontrol.app.domain.model.IoStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class IoUiEvent {
    data class CommandError(val message: String) : IoUiEvent()
    data object CommandSuccess : IoUiEvent()
}

@HiltViewModel
class IoViewModel @Inject constructor(
    private val machineRepository: MachineRepository
) : ViewModel() {

    val ioStatus: StateFlow<IoStatus> = machineRepository.ioStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = IoStatus(digitalInputs = 0, relayOutputs = 0)
        )

    val isConnected: StateFlow<Boolean> = machineRepository.systemStatus
        .map { it.connectionState == ConnectionState.LIVE }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isServiceMode: StateFlow<Boolean> = machineRepository.systemStatus
        .map { it.isServiceModeEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isSimulationEnabled: StateFlow<Boolean> = machineRepository.isSimulationEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Combined state for enabling relay controls (connected + service mode)
    val canControlRelays: StateFlow<Boolean> = combine(
        isConnected,
        isServiceMode
    ) { connected, serviceMode ->
        connected && serviceMode
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _isExecutingCommand = MutableStateFlow(false)
    val isExecutingCommand: StateFlow<Boolean> = _isExecutingCommand.asStateFlow()

    private val _uiEvents = MutableSharedFlow<IoUiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    fun setRelay(channel: Int, on: Boolean) {
        viewModelScope.launch {
            _isExecutingCommand.value = true
            try {
                val result = machineRepository.setRelay(channel, on)
                result.fold(
                    onSuccess = {
                        _uiEvents.emit(IoUiEvent.CommandSuccess)
                    },
                    onFailure = { error ->
                        _uiEvents.emit(IoUiEvent.CommandError(
                            error.message ?: "Failed to set relay $channel"
                        ))
                    }
                )
            } finally {
                _isExecutingCommand.value = false
            }
        }
    }

    fun setSimulationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            machineRepository.setSimulationEnabled(enabled)
        }
    }

    fun setSimulatedInput(channel: Int, high: Boolean) {
        viewModelScope.launch {
            machineRepository.setSimulatedInput(channel, high)
        }
    }
}
