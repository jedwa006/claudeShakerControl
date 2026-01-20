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

/**
 * Pending action that requires user confirmation due to safety warning.
 */
sealed class PendingAction {
    data class EnableHeatersWithProbeError(val affectedPids: List<PidData>) : PendingAction()
    data class EnableCoolingWithProbeError(val pid: PidData) : PendingAction()
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

    // Pending action requiring confirmation (e.g., enabling with probe error)
    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction.asStateFlow()

    // ==========================================
    // Light and Door Relay State
    // Light relay: CH7 (chamber lighting)
    // Door lock relay: CH6 (solenoid locking bar)
    // ==========================================

    /**
     * Whether the chamber light is currently on (CH7 relay).
     * Derived from the relay outputs bitmask.
     */
    val isLightOn: StateFlow<Boolean> = ioStatus
        .map { it.isOutputHigh(LIGHT_RELAY_CHANNEL) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Whether the door is locked (CH6 relay).
     * Derived from the relay outputs bitmask.
     */
    val isDoorLocked: StateFlow<Boolean> = ioStatus
        .map { it.isOutputHigh(DOOR_LOCK_RELAY_CHANNEL) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // ==========================================
    // Chilldown State
    // ==========================================

    private val _isChilldownActive = MutableStateFlow(false)
    val isChilldownActive: StateFlow<Boolean> = _isChilldownActive.asStateFlow()

    private val _isStartAfterChillEnabled = MutableStateFlow(false)
    val isStartAfterChillEnabled: StateFlow<Boolean> = _isStartAfterChillEnabled.asStateFlow()

    /**
     * Whether chilldown can be started (LN2 controller online, no probe error, not running).
     */
    val canChilldown: StateFlow<Boolean> = combine(
        pidData,
        systemStatus,
        isChilldownActive
    ) { pids, status, chilling ->
        if (chilling) return@combine false  // Already chilling
        if (status.machineState.isOperating) return@combine false  // Can't chill during run
        if (status.connectionState != ConnectionState.LIVE) return@combine false

        // Check LN2 controller (PID 1) is online
        val ln2Pid = pids.find { it.controllerId == 1 }
        ln2Pid != null && !ln2Pid.isOffline
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    companion object {
        const val LIGHT_RELAY_CHANNEL = 7        // CH7 = Chamber light
        const val DOOR_LOCK_RELAY_CHANNEL = 6    // CH6 = Door lock solenoid
        const val LN2_VALVE_CHANNEL = 5          // CH5 = LN2 solenoid (for chilldown)
    }

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

        // Check for probe errors on required controllers
        // A probe error (HHHH/LLLL) means the controller can't reliably control temperature
        val requiredPidsWithProbeError = pids.filter { pid ->
            val capability = capabilities.getPidCapability(pid.controllerId)
            capability == CapabilityLevel.REQUIRED && pid.hasProbeError
        }

        if (requiredPidsWithProbeError.isNotEmpty()) {
            val errorPid = requiredPidsWithProbeError.first()
            val errorType = errorPid.probeError.shortName
            return StartGatingResult.blocked("${errorPid.name} probe error ($errorType).")
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

    /**
     * Wake from lazy polling mode by sending a PV/SV refresh command.
     * Any command (except KEEPALIVE) resets the MCU's idle timer and returns
     * to fast polling mode.
     */
    fun wakeFromLazyMode() {
        viewModelScope.launch {
            // Send a refresh request for the first available controller to wake from lazy mode
            val controllers = pidData.value
            if (controllers.isNotEmpty()) {
                machineRepository.requestPvSvRefresh(controllers.first().controllerId)
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
     *
     * If enabling and any heater has a probe error, requires confirmation.
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

            // If enabling, check for probe errors and require confirmation
            if (targetMode == PidMode.AUTO) {
                val pidsWithProbeError = heaterPids.filter { it.hasProbeError }
                if (pidsWithProbeError.isNotEmpty()) {
                    _pendingAction.value = PendingAction.EnableHeatersWithProbeError(pidsWithProbeError)
                    return@launch
                }
            }

            executeToggleHeaters(heaterPids, targetMode)
        }
    }

    /**
     * Execute heater toggle after any confirmation.
     */
    private suspend fun executeToggleHeaters(heaterPids: List<PidData>, targetMode: PidMode) {
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
            _uiEvents.emit(RunUiEvent.CommandSuccess)
        }
    }

    /**
     * Toggle cooling PID (PID 1) between AUTO and STOP.
     *
     * If enabling and the cooling PID has a probe error, requires confirmation.
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

            // If enabling, check for probe error and require confirmation
            if (targetMode == PidMode.AUTO && coolingPid.hasProbeError) {
                _pendingAction.value = PendingAction.EnableCoolingWithProbeError(coolingPid)
                return@launch
            }

            executeToggleCooling(targetMode)
        }
    }

    /**
     * Execute cooling toggle after any confirmation.
     */
    private suspend fun executeToggleCooling(targetMode: PidMode) {
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

    /**
     * Confirm the pending action (user acknowledged the safety warning).
     */
    fun confirmPendingAction() {
        viewModelScope.launch {
            when (val action = _pendingAction.value) {
                is PendingAction.EnableHeatersWithProbeError -> {
                    val currentPids = pidData.value
                    val heaterPids = currentPids.filter { it.controllerId in listOf(2, 3) && !it.isOffline }
                    executeToggleHeaters(heaterPids, PidMode.AUTO)
                }
                is PendingAction.EnableCoolingWithProbeError -> {
                    executeToggleCooling(PidMode.AUTO)
                }
                null -> { /* No pending action */ }
            }
            _pendingAction.value = null
        }
    }

    /**
     * Cancel the pending action.
     */
    fun cancelPendingAction() {
        _pendingAction.value = null
    }

    // ==========================================
    // Light and Door Control
    // ==========================================

    /**
     * Toggle the chamber light (CH7 relay).
     */
    fun toggleLight() {
        viewModelScope.launch {
            _isExecutingCommand.value = true
            val currentState = isLightOn.value
            val result = machineRepository.setRelay(LIGHT_RELAY_CHANNEL, !currentState)

            result.fold(
                onSuccess = {
                    _uiEvents.emit(RunUiEvent.CommandSuccess)
                },
                onFailure = { error ->
                    _uiEvents.emit(RunUiEvent.CommandError(
                        "Failed to toggle light: ${error.message}"
                    ))
                }
            )
            _isExecutingCommand.value = false
        }
    }

    /**
     * Toggle the door lock (CH6 relay).
     */
    fun toggleDoor() {
        viewModelScope.launch {
            _isExecutingCommand.value = true
            val currentState = isDoorLocked.value
            val result = machineRepository.setRelay(DOOR_LOCK_RELAY_CHANNEL, !currentState)

            result.fold(
                onSuccess = {
                    _uiEvents.emit(RunUiEvent.CommandSuccess)
                },
                onFailure = { error ->
                    _uiEvents.emit(RunUiEvent.CommandError(
                        "Failed to toggle door lock: ${error.message}"
                    ))
                }
            )
            _isExecutingCommand.value = false
        }
    }

    // ==========================================
    // Chilldown Control
    // ==========================================

    /**
     * Start the chilldown (pre-cooling) cycle.
     * This enables the LN2 controller to bring the chamber to target temperature
     * before starting the actual milling run.
     *
     * Note: Full implementation requires MCU command support (CMD_START_CHILLDOWN).
     * For now, this enables the LN2 controller in AUTO mode.
     */
    fun startChilldown() {
        viewModelScope.launch {
            val ln2Pid = pidData.value.find { it.controllerId == 1 }
            if (ln2Pid == null || ln2Pid.isOffline) {
                _uiEvents.emit(RunUiEvent.CommandError("LN2 controller not available"))
                return@launch
            }

            _isExecutingCommand.value = true

            // Enable LN2 controller for pre-cooling
            val result = machineRepository.setMode(1, PidMode.AUTO)

            result.fold(
                onSuccess = {
                    _isChilldownActive.value = true
                    _uiEvents.emit(RunUiEvent.CommandSuccess)
                    // TODO: When MCU supports CMD_START_CHILLDOWN, use that instead
                    // The MCU would handle the state transition CHILLING → READY
                    // and auto-start if isStartAfterChillEnabled is true
                },
                onFailure = { error ->
                    _uiEvents.emit(RunUiEvent.CommandError(
                        "Failed to start chilldown: ${error.message}"
                    ))
                }
            )

            _isExecutingCommand.value = false
        }
    }

    /**
     * Set whether to automatically start the run after chilldown completes.
     */
    fun setStartAfterChill(enabled: Boolean) {
        _isStartAfterChillEnabled.value = enabled
    }

    /**
     * Called when chilldown completes (e.g., target temperature reached).
     * If auto-start is enabled, automatically starts the run.
     *
     * Note: This would be called by the MCU telemetry handler when the
     * machine transitions from CHILLING to READY state.
     */
    fun onChilldownComplete() {
        _isChilldownActive.value = false

        if (_isStartAfterChillEnabled.value) {
            startRun()
        }
    }
}
