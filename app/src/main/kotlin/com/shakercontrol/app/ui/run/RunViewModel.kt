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
import kotlinx.coroutines.flow.map
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

    val ioStatus: StateFlow<IoStatus> = machineRepository.ioStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = IoStatus(digitalInputs = 0, relayOutputs = 0)
        )

    val isSimulationEnabled: StateFlow<Boolean> = machineRepository.isSimulationEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Display slots for configurable visualizations (temperature plots, thermal camera, etc.)
    private val _displaySlots = MutableStateFlow(
        listOf(
            DisplaySlot(0, DisplaySource.EMPTY, "Display 1"),
            DisplaySlot(1, DisplaySource.EMPTY, "Display 2")
        )
    )
    val displaySlots: StateFlow<List<DisplaySlot>> = _displaySlots.asStateFlow()

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

    /**
     * Update a display slot's source. Called when user configures a slot.
     */
    fun updateDisplaySlot(index: Int, source: DisplaySource, title: String) {
        _displaySlots.value = _displaySlots.value.map { slot ->
            if (slot.index == index) {
                slot.copy(source = source, title = title)
            } else {
                slot
            }
        }
    }

    // ==========================================
    // Service Mode PID Controls (Heat/Cool buttons)
    // ==========================================

    /**
     * Whether heater PIDs (PID 2 & 3) are currently enabled (mode = AUTO).
     * Returns true if at least one heater is enabled.
     */
    val areHeatersEnabled: StateFlow<Boolean> = pidData
        .map { pids ->
            val heaterPids = pids.filter { it.controllerId in listOf(2, 3) }
            heaterPids.any { it.mode == PidMode.AUTO }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Whether cooling PID (PID 1) is currently enabled (mode = AUTO).
     */
    val isCoolingEnabled: StateFlow<Boolean> = pidData
        .map { pids ->
            pids.find { it.controllerId == 1 }?.mode == PidMode.AUTO
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Toggle heater PIDs (PID 2 & 3) between AUTO and STOP.
     * If any heater is enabled, stops all heaters.
     * If all heaters are stopped, enables all connected heaters.
     */
    fun toggleHeaters() {
        viewModelScope.launch {
            val currentPids = pidData.value
            val heaterPids = currentPids.filter { it.controllerId in listOf(2, 3) && !it.isOffline }

            if (heaterPids.isEmpty()) {
                _uiEvents.emit(RunUiEvent.CommandError("No heater controllers connected"))
                return@launch
            }

            val anyEnabled = heaterPids.any { it.mode == PidMode.AUTO }
            val targetMode = if (anyEnabled) PidMode.STOP else PidMode.AUTO

            _isExecutingCommand.value = true
            var hasError = false

            for (pid in heaterPids) {
                val result = machineRepository.setMode(pid.controllerId, targetMode)
                result.onFailure { error ->
                    hasError = true
                    _uiEvents.emit(RunUiEvent.CommandError(
                        "Failed to set PID ${pid.controllerId} mode: ${error.message}"
                    ))
                }
            }

            _isExecutingCommand.value = false

            if (!hasError) {
                val action = if (targetMode == PidMode.AUTO) "enabled" else "disabled"
                _uiEvents.emit(RunUiEvent.CommandSuccess)
            }
        }
    }

    /**
     * Toggle cooling PID (PID 1) between AUTO and STOP.
     */
    fun toggleCooling() {
        viewModelScope.launch {
            val currentPids = pidData.value
            val coolingPid = currentPids.find { it.controllerId == 1 }

            if (coolingPid == null || coolingPid.isOffline) {
                _uiEvents.emit(RunUiEvent.CommandError("LN2 cooling controller not connected"))
                return@launch
            }

            val targetMode = if (coolingPid.mode == PidMode.AUTO) PidMode.STOP else PidMode.AUTO

            _isExecutingCommand.value = true

            val result = machineRepository.setMode(1, targetMode)
            result.fold(
                onSuccess = {
                    _uiEvents.emit(RunUiEvent.CommandSuccess)
                },
                onFailure = { error ->
                    _uiEvents.emit(RunUiEvent.CommandError(
                        "Failed to set LN2 mode: ${error.message}"
                    ))
                }
            )

            _isExecutingCommand.value = false
        }
    }
}
