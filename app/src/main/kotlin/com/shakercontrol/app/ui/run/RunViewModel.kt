package com.shakercontrol.app.ui.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI events from run commands.
 */
sealed class RunUiEvent {
    data class CommandError(val message: String) : RunUiEvent()
    data object CommandSuccess : RunUiEvent()
}

@HiltViewModel
class RunViewModel @Inject constructor(
    private val machineRepository: MachineRepository
) : ViewModel() {

    val systemStatus: StateFlow<SystemStatus> = machineRepository.systemStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SystemStatus.DISCONNECTED
        )

    val recipe: StateFlow<Recipe> = machineRepository.recipe
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Recipe.DEFAULT
        )

    val runProgress: StateFlow<RunProgress?> = machineRepository.runProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val pidData: StateFlow<List<PidData>> = machineRepository.pidData
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val interlockStatus: StateFlow<InterlockStatus> = machineRepository.interlockStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = InterlockStatus(
                isEStopActive = false,
                isDoorLocked = false,
                isLn2Present = false,
                isPowerEnabled = false,
                isHeatersEnabled = false,
                isMotorEnabled = false
            )
        )

    // Command execution state
    private val _isExecutingCommand = MutableStateFlow(false)
    val isExecutingCommand: StateFlow<Boolean> = _isExecutingCommand.asStateFlow()

    // UI events (errors, success messages)
    private val _uiEvents = MutableSharedFlow<RunUiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    /**
     * Start gating result based on connection, machine state, and capability checks.
     */
    val startGating: StateFlow<StartGatingResult> = combine(
        systemStatus,
        pidData
    ) { status, pids ->
        checkStartGating(status, pids)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StartGatingResult.blocked("Initializing...")
    )

    private fun checkStartGating(status: SystemStatus, pids: List<PidData>): StartGatingResult {
        // Check connection
        if (status.connectionState != ConnectionState.LIVE) {
            return StartGatingResult.blocked("Not connected to controller.")
        }

        // Check machine state
        if (!status.machineState.canStart) {
            return when (status.machineState) {
                MachineState.E_STOP -> StartGatingResult.blocked("E-Stop is active.")
                MachineState.FAULT -> StartGatingResult.blocked("Machine has faulted.")
                MachineState.RUNNING -> StartGatingResult.blocked("Already running.")
                MachineState.PAUSED -> StartGatingResult.blocked("Run is paused. Resume or stop first.")
                else -> StartGatingResult.blocked("Machine not ready.")
            }
        }

        // Check required capabilities (PIDs must be communicating)
        val capabilities = status.capabilities
        val pidStatuses = pids.associate { pid ->
            pid.controllerId to when {
                pid.isOffline -> SubsystemStatus.OFFLINE
                pid.hasFault -> SubsystemStatus.FAULT
                else -> SubsystemStatus.OK
            }
        }

        val missingRequired = capabilities.getMissingRequiredSubsystems(
            pid1Status = pidStatuses[1] ?: SubsystemStatus.OFFLINE,
            pid2Status = pidStatuses[2] ?: SubsystemStatus.OFFLINE,
            pid3Status = pidStatuses[3] ?: SubsystemStatus.OFFLINE
        )

        if (missingRequired.isNotEmpty()) {
            return StartGatingResult.blocked("Cannot start: ${missingRequired.first()} offline.")
        }

        return StartGatingResult.OK
    }

    fun updateRecipe(recipe: Recipe) {
        viewModelScope.launch {
            machineRepository.updateRecipe(recipe)
        }
    }

    fun startRun() {
        viewModelScope.launch {
            _isExecutingCommand.value = true
            val result = machineRepository.startRun()
            _isExecutingCommand.value = false

            result.onFailure { error ->
                _uiEvents.emit(RunUiEvent.CommandError(
                    formatErrorMessage("Start", error)
                ))
            }
        }
    }

    fun pauseRun() {
        viewModelScope.launch {
            _isExecutingCommand.value = true
            val result = machineRepository.pauseRun()
            _isExecutingCommand.value = false

            result.onFailure { error ->
                _uiEvents.emit(RunUiEvent.CommandError(
                    formatErrorMessage("Pause", error)
                ))
            }
        }
    }

    fun resumeRun() {
        viewModelScope.launch {
            _isExecutingCommand.value = true
            val result = machineRepository.resumeRun()
            _isExecutingCommand.value = false

            result.onFailure { error ->
                _uiEvents.emit(RunUiEvent.CommandError(
                    formatErrorMessage("Resume", error)
                ))
            }
        }
    }

    fun stopRun() {
        viewModelScope.launch {
            _isExecutingCommand.value = true
            val result = machineRepository.stopRun()
            _isExecutingCommand.value = false

            result.onFailure { error ->
                _uiEvents.emit(RunUiEvent.CommandError(
                    formatErrorMessage("Stop", error)
                ))
            }
        }
    }

    private fun formatErrorMessage(command: String, error: Throwable): String {
        val reason = error.message ?: "Unknown error"
        return when {
            reason.contains("No session") -> "$command failed: Not connected to controller."
            reason.contains("rejected") -> "$command rejected by controller."
            else -> "$command failed: $reason"
        }
    }
}
