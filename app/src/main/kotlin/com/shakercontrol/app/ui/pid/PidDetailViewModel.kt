package com.shakercontrol.app.ui.pid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.domain.model.ConnectionState
import com.shakercontrol.app.domain.model.PidData
import com.shakercontrol.app.domain.model.PidMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PidUiEvent {
    data class CommandError(val message: String) : PidUiEvent()
    data object CommandSuccess : PidUiEvent()
}

@HiltViewModel
class PidDetailViewModel @Inject constructor(
    private val machineRepository: MachineRepository
) : ViewModel() {

    private val _isExecutingCommand = MutableStateFlow(false)
    val isExecutingCommand: StateFlow<Boolean> = _isExecutingCommand.asStateFlow()

    private val _uiEvents = MutableSharedFlow<PidUiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    val connectionState: StateFlow<ConnectionState> = machineRepository.systemStatus
        .map { it.connectionState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionState.DISCONNECTED
        )

    fun getPidData(pidId: Int): Flow<PidData?> {
        return machineRepository.pidData.map { pidList ->
            pidList.find { it.controllerId == pidId }
        }
    }

    fun setSetpoint(controllerId: Int, setpoint: Float) {
        viewModelScope.launch {
            _isExecutingCommand.value = true
            try {
                val result = machineRepository.setSetpoint(controllerId, setpoint)
                result.fold(
                    onSuccess = {
                        // Force poll after write for immediate UI update
                        machineRepository.requestPvSvRefresh(controllerId)
                        _uiEvents.emit(PidUiEvent.CommandSuccess)
                    },
                    onFailure = { error ->
                        _uiEvents.emit(PidUiEvent.CommandError(error.message ?: "Failed to set setpoint"))
                    }
                )
            } finally {
                _isExecutingCommand.value = false
            }
        }
    }

    fun setMode(controllerId: Int, mode: PidMode) {
        viewModelScope.launch {
            _isExecutingCommand.value = true
            try {
                val result = machineRepository.setMode(controllerId, mode)
                result.fold(
                    onSuccess = {
                        _uiEvents.emit(PidUiEvent.CommandSuccess)
                    },
                    onFailure = { error ->
                        _uiEvents.emit(PidUiEvent.CommandError(error.message ?: "Failed to set mode"))
                    }
                )
            } finally {
                _isExecutingCommand.value = false
            }
        }
    }
}
