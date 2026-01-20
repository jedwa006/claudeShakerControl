package com.shakercontrol.app.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.domain.model.Alarm
import com.shakercontrol.app.domain.model.AlarmHistoryEntry
import com.shakercontrol.app.domain.model.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AlarmsUiEvent {
    data class CommandError(val message: String) : AlarmsUiEvent()
    data object CommandSuccess : AlarmsUiEvent()
}

@HiltViewModel
class AlarmsViewModel @Inject constructor(
    private val machineRepository: MachineRepository
) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> = machineRepository.alarms
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Alarm transition history - records every alarm bit assertion and clearance.
     * Shows transient alarms that may appear briefly then clear.
     */
    val alarmHistory: StateFlow<List<AlarmHistoryEntry>> = machineRepository.alarmHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isConnected: StateFlow<Boolean> = machineRepository.systemStatus
        .map { it.connectionState == ConnectionState.LIVE }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _isExecutingCommand = MutableStateFlow(false)
    val isExecutingCommand: StateFlow<Boolean> = _isExecutingCommand.asStateFlow()

    private val _uiEvents = MutableSharedFlow<AlarmsUiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    fun acknowledgeAlarm(alarmId: String) {
        viewModelScope.launch {
            _isExecutingCommand.value = true
            try {
                val result = machineRepository.acknowledgeAlarm(alarmId)
                result.fold(
                    onSuccess = {
                        _uiEvents.emit(AlarmsUiEvent.CommandSuccess)
                    },
                    onFailure = { error ->
                        _uiEvents.emit(AlarmsUiEvent.CommandError(
                            error.message ?: "Failed to acknowledge alarm"
                        ))
                    }
                )
            } finally {
                _isExecutingCommand.value = false
            }
        }
    }

    fun clearLatchedAlarms() {
        viewModelScope.launch {
            _isExecutingCommand.value = true
            try {
                val result = machineRepository.clearLatchedAlarms()
                result.fold(
                    onSuccess = {
                        _uiEvents.emit(AlarmsUiEvent.CommandSuccess)
                    },
                    onFailure = { error ->
                        _uiEvents.emit(AlarmsUiEvent.CommandError(
                            error.message ?: "Failed to clear alarms"
                        ))
                    }
                )
            } finally {
                _isExecutingCommand.value = false
            }
        }
    }
}
