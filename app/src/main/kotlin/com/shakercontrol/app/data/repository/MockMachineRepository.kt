package com.shakercontrol.app.data.repository

import com.shakercontrol.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Mock implementation of MachineRepository for UI development and testing.
 * Provides realistic-looking data without BLE connection.
 */
@Singleton
class MockMachineRepository @Inject constructor() : MachineRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Run progress timer
    private var runTimerJob: Job? = null
    private var runStartTime: Long = 0L
    private var pausedElapsedMs: Long = 0L

    private val _systemStatus = MutableStateFlow(createMockSystemStatus())
    override val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    private val _pidData = MutableStateFlow(createMockPidData())
    override val pidData: StateFlow<List<PidData>> = _pidData.asStateFlow()

    private val _recipe = MutableStateFlow(Recipe.DEFAULT)
    override val recipe: StateFlow<Recipe> = _recipe.asStateFlow()

    private val _runProgress = MutableStateFlow<RunProgress?>(null)
    override val runProgress: StateFlow<RunProgress?> = _runProgress.asStateFlow()

    private val _alarms = MutableStateFlow(createMockAlarms())
    override val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _ioStatus = MutableStateFlow(IoStatus(digitalInputs = 0b00000101, relayOutputs = 0b00000001))
    override val ioStatus: StateFlow<IoStatus> = _ioStatus.asStateFlow()

    private val _interlockStatus = MutableStateFlow(createMockInterlockStatus())
    override val interlockStatus: StateFlow<InterlockStatus> = _interlockStatus.asStateFlow()

    private val _isSimulationEnabled = MutableStateFlow(false)
    override val isSimulationEnabled: StateFlow<Boolean> = _isSimulationEnabled.asStateFlow()

    // Simulated input values (used when simulation mode is enabled)
    private var simulatedInputs = 0

    private fun createMockSystemStatus() = SystemStatus(
        connectionState = ConnectionState.LIVE,
        machineState = MachineState.READY,
        mcuHeartbeatAgeMs = 120,
        bleHeartbeatAgeMs = 80,
        alarmSummary = AlarmSummary(totalCount = 0, highCount = 0, highestSeverity = null),
        isServiceModeEnabled = false,
        deviceName = "SYS-CTRL-001",
        rssiDbm = -58,
        firmwareVersion = "1.0.0",
        protocolVersion = 1
    )

    private fun createMockPidData() = listOf(
        PidData(
            controllerId = 1,
            name = "LN2 (Cold)",
            processValue = -180.5f,
            setpointValue = -185.0f,
            outputPercent = 0.0f,
            mode = PidMode.AUTO,
            isEnabled = true,
            isOutputActive = false,
            hasFault = false,
            ageMs = 120,
            capabilityLevel = CapabilityLevel.OPTIONAL
        ),
        PidData(
            controllerId = 2,
            name = "Axle bearings",
            processValue = 25.4f,
            setpointValue = 30.0f,
            outputPercent = 45.6f,
            mode = PidMode.AUTO,
            isEnabled = true,
            isOutputActive = true,
            hasFault = false,
            ageMs = 120,
            capabilityLevel = CapabilityLevel.REQUIRED
        ),
        PidData(
            controllerId = 3,
            name = "Orbital bearings",
            processValue = 28.1f,
            setpointValue = 30.0f,
            outputPercent = 32.1f,
            mode = PidMode.AUTO,
            isEnabled = true,
            isOutputActive = true,
            hasFault = false,
            ageMs = 120,
            capabilityLevel = CapabilityLevel.REQUIRED
        )
    )

    private fun createMockAlarms(): List<Alarm> = emptyList()

    private fun createMockInterlockStatus() = InterlockStatus(
        isEStopActive = false,
        isDoorLocked = true,
        isLn2Present = true,
        isPowerEnabled = true,
        isHeatersEnabled = false,
        isMotorEnabled = false
    )

    // Timer functions
    private fun startRunTimer() {
        runTimerJob?.cancel()
        runStartTime = System.currentTimeMillis()
        runTimerJob = scope.launch {
            while (isActive) {
                delay(1000) // Update every second
                updateRunProgress()
            }
        }
    }

    private fun pauseRunTimer() {
        runTimerJob?.cancel()
        runTimerJob = null
        // Save the elapsed time when paused
        pausedElapsedMs += System.currentTimeMillis() - runStartTime
    }

    private fun resumeRunTimer() {
        runTimerJob?.cancel()
        runStartTime = System.currentTimeMillis()
        runTimerJob = scope.launch {
            while (isActive) {
                delay(1000)
                updateRunProgress()
            }
        }
    }

    private fun stopRunTimer() {
        runTimerJob?.cancel()
        runTimerJob = null
        pausedElapsedMs = 0L
        runStartTime = 0L
    }

    private fun updateRunProgress() {
        val currentRecipe = _recipe.value
        val currentProgress = _runProgress.value ?: return

        // Calculate total elapsed time including any paused time
        val totalElapsedMs = pausedElapsedMs + (System.currentTimeMillis() - runStartTime)

        // Calculate which cycle and phase we're in
        val millingMs = currentRecipe.millingDuration.inWholeMilliseconds
        val holdMs = currentRecipe.holdDuration.inWholeMilliseconds
        val totalCycles = currentRecipe.cycleCount

        // Determine current position
        var remainingMs = totalElapsedMs
        var currentCycle = 1
        var currentPhase = RunPhase.MILLING
        var phaseElapsedMs = 0L

        for (cycle in 1..totalCycles) {
            currentCycle = cycle

            // Milling phase
            if (remainingMs < millingMs) {
                currentPhase = RunPhase.MILLING
                phaseElapsedMs = remainingMs
                break
            }
            remainingMs -= millingMs

            // Hold phase (no hold after last cycle)
            if (cycle < totalCycles) {
                if (remainingMs < holdMs) {
                    currentPhase = RunPhase.HOLDING
                    phaseElapsedMs = remainingMs
                    break
                }
                remainingMs -= holdMs
            } else {
                // Run complete
                stopRunTimer()
                _runProgress.value = null
                _systemStatus.value = _systemStatus.value.copy(
                    machineState = MachineState.IDLE
                )
                return
            }
        }

        // Calculate remaining times
        val phaseDuration = if (currentPhase == RunPhase.MILLING) millingMs else holdMs
        val phaseRemainingMs = (phaseDuration - phaseElapsedMs).coerceAtLeast(0)
        val totalRemainingMs = (currentRecipe.totalRuntime.inWholeMilliseconds - totalElapsedMs).coerceAtLeast(0)

        _runProgress.value = RunProgress(
            currentCycle = currentCycle,
            totalCycles = totalCycles,
            currentPhase = currentPhase,
            phaseElapsed = (phaseElapsedMs / 1000).seconds,
            phaseRemaining = (phaseRemainingMs / 1000).seconds,
            totalRemaining = (totalRemainingMs / 1000).seconds
        )
    }

    override suspend fun startScan() {
        _systemStatus.value = _systemStatus.value.copy(
            connectionState = ConnectionState.SCANNING
        )
        delay(2000)
        _systemStatus.value = _systemStatus.value.copy(
            connectionState = ConnectionState.DEVICE_SELECTED
        )
    }

    override suspend fun stopScan() {
        if (_systemStatus.value.connectionState == ConnectionState.SCANNING) {
            _systemStatus.value = _systemStatus.value.copy(
                connectionState = ConnectionState.DISCONNECTED
            )
        }
    }

    override suspend fun connect(deviceAddress: String) {
        _systemStatus.value = _systemStatus.value.copy(
            connectionState = ConnectionState.CONNECTING
        )
        delay(500)
        _systemStatus.value = _systemStatus.value.copy(
            connectionState = ConnectionState.DISCOVERING
        )
        delay(500)
        _systemStatus.value = _systemStatus.value.copy(
            connectionState = ConnectionState.SUBSCRIBING
        )
        delay(300)
        _systemStatus.value = _systemStatus.value.copy(
            connectionState = ConnectionState.SESSION_OPENING
        )
        delay(200)
        _systemStatus.value = createMockSystemStatus()
    }

    override suspend fun disconnect() {
        stopRunTimer()
        _systemStatus.value = _systemStatus.value.copy(
            connectionState = ConnectionState.DISCONNECTED,
            deviceName = null,
            rssiDbm = null
        )
        _runProgress.value = null
    }

    override suspend fun forgetDevice() {
        disconnect()
    }

    override suspend fun startRun(): Result<Unit> {
        if (!_systemStatus.value.machineState.canStart) {
            return Result.failure(IllegalStateException("Cannot start: machine not ready"))
        }
        if (_systemStatus.value.connectionState != ConnectionState.LIVE) {
            return Result.failure(IllegalStateException("Cannot start: not connected"))
        }

        pausedElapsedMs = 0L  // Reset pause accumulator
        _systemStatus.value = _systemStatus.value.copy(
            machineState = MachineState.RUNNING
        )
        _runProgress.value = RunProgress(
            currentCycle = 1,
            totalCycles = _recipe.value.cycleCount,
            currentPhase = RunPhase.MILLING,
            phaseElapsed = 0.seconds,
            phaseRemaining = _recipe.value.millingDuration,
            totalRemaining = _recipe.value.totalRuntime
        )
        startRunTimer()  // Start the countdown timer
        return Result.success(Unit)
    }

    override suspend fun pauseRun(): Result<Unit> {
        if (!_systemStatus.value.machineState.canPause) {
            return Result.failure(IllegalStateException("Cannot pause: machine not running"))
        }

        pauseRunTimer()  // Pause the countdown timer
        _systemStatus.value = _systemStatus.value.copy(
            machineState = MachineState.PAUSED
        )
        return Result.success(Unit)
    }

    override suspend fun resumeRun(): Result<Unit> {
        if (!_systemStatus.value.machineState.canResume) {
            return Result.failure(IllegalStateException("Cannot resume: machine not paused"))
        }

        resumeRunTimer()  // Resume the countdown timer
        _systemStatus.value = _systemStatus.value.copy(
            machineState = MachineState.RUNNING
        )
        return Result.success(Unit)
    }

    override suspend fun stopRun(): Result<Unit> {
        if (!_systemStatus.value.machineState.canStop) {
            return Result.failure(IllegalStateException("Cannot stop: machine not operating"))
        }

        stopRunTimer()  // Stop the countdown timer
        _systemStatus.value = _systemStatus.value.copy(
            machineState = MachineState.IDLE
        )
        _runProgress.value = null
        return Result.success(Unit)
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        _recipe.value = recipe
    }

    override suspend fun enableServiceMode() {
        _systemStatus.value = _systemStatus.value.copy(
            isServiceModeEnabled = true
        )
    }

    override suspend fun disableServiceMode() {
        _systemStatus.value = _systemStatus.value.copy(
            isServiceModeEnabled = false
        )
    }

    override suspend fun setSetpoint(controllerId: Int, setpoint: Float): Result<Unit> {
        if (_systemStatus.value.connectionState != ConnectionState.LIVE) {
            return Result.failure(IllegalStateException("Not connected"))
        }

        // Simulate command delay
        delay(200)

        // Update local state
        val currentPidList = _pidData.value.toMutableList()
        val index = currentPidList.indexOfFirst { it.controllerId == controllerId }
        if (index >= 0) {
            currentPidList[index] = currentPidList[index].copy(setpointValue = setpoint)
            _pidData.value = currentPidList
        }
        return Result.success(Unit)
    }

    override suspend fun setMode(controllerId: Int, mode: PidMode): Result<Unit> {
        if (_systemStatus.value.connectionState != ConnectionState.LIVE) {
            return Result.failure(IllegalStateException("Not connected"))
        }

        // Simulate command delay
        delay(200)

        // Update local state
        val currentPidList = _pidData.value.toMutableList()
        val index = currentPidList.indexOfFirst { it.controllerId == controllerId }
        if (index >= 0) {
            currentPidList[index] = currentPidList[index].copy(
                mode = mode,
                isEnabled = mode != PidMode.STOP
            )
            _pidData.value = currentPidList
        }
        return Result.success(Unit)
    }

    override suspend fun acknowledgeAlarm(alarmId: String): Result<Unit> {
        if (_systemStatus.value.connectionState != ConnectionState.LIVE) {
            return Result.failure(IllegalStateException("Not connected"))
        }

        // Simulate command delay
        delay(200)

        // Update alarm state
        val currentAlarms = _alarms.value.toMutableList()
        val index = currentAlarms.indexOfFirst { it.id == alarmId }
        if (index >= 0) {
            currentAlarms[index] = currentAlarms[index].copy(isAcknowledged = true)
            _alarms.value = currentAlarms
        }
        return Result.success(Unit)
    }

    override suspend fun clearLatchedAlarms(): Result<Unit> {
        if (_systemStatus.value.connectionState != ConnectionState.LIVE) {
            return Result.failure(IllegalStateException("Not connected"))
        }

        // Simulate command delay
        delay(200)

        // Clear all acknowledged alarms that are no longer active
        val currentAlarms = _alarms.value.filter {
            it.state == AlarmState.ACTIVE && !it.isAcknowledged
        }
        _alarms.value = currentAlarms
        return Result.success(Unit)
    }

    override suspend fun setRelay(channel: Int, on: Boolean): Result<Unit> {
        if (_systemStatus.value.connectionState != ConnectionState.LIVE) {
            return Result.failure(IllegalStateException("Not connected"))
        }
        require(channel in 1..8) { "Channel must be 1-8" }

        // Simulate command delay
        delay(100)

        // Update relay state
        val current = _ioStatus.value
        val newBits = if (on) {
            current.relayOutputs or (1 shl (channel - 1))
        } else {
            current.relayOutputs and (1 shl (channel - 1)).inv()
        }
        _ioStatus.value = current.copy(relayOutputs = newBits)
        return Result.success(Unit)
    }

    override suspend fun setRelayMask(mask: Int, values: Int): Result<Unit> {
        if (_systemStatus.value.connectionState != ConnectionState.LIVE) {
            return Result.failure(IllegalStateException("Not connected"))
        }
        require(mask in 0..0xFF) { "Mask must be 0-255" }
        require(values in 0..0xFF) { "Values must be 0-255" }

        // Simulate command delay
        delay(100)

        // Update relay state atomically
        val current = _ioStatus.value
        // Clear bits where mask is set, then set new values
        val clearedBits = current.relayOutputs and mask.inv()
        val newBits = clearedBits or (values and mask)
        _ioStatus.value = current.copy(relayOutputs = newBits)
        return Result.success(Unit)
    }

    override suspend fun setSimulationEnabled(enabled: Boolean) {
        _isSimulationEnabled.value = enabled
        if (!enabled) {
            simulatedInputs = 0
        }
    }

    override suspend fun setSimulatedInput(channel: Int, high: Boolean) {
        require(channel in 1..8) { "Channel must be 1-8" }

        if (high) {
            simulatedInputs = simulatedInputs or (1 shl (channel - 1))
        } else {
            simulatedInputs = simulatedInputs and (1 shl (channel - 1)).inv()
        }

        // When simulation is enabled, use simulated values
        if (_isSimulationEnabled.value) {
            val current = _ioStatus.value
            _ioStatus.value = current.copy(digitalInputs = simulatedInputs)
        }
    }

    // Capability overrides
    private val _capabilityOverrides = MutableStateFlow<SubsystemCapabilities?>(null)
    override val capabilityOverrides: StateFlow<SubsystemCapabilities?> = _capabilityOverrides.asStateFlow()

    override suspend fun setCapabilityOverride(subsystem: String, level: CapabilityLevel) {
        val current = _capabilityOverrides.value ?: SubsystemCapabilities.DEFAULT

        val updated = when (subsystem) {
            "pid1" -> current.copy(pid1 = level)
            "pid2" -> current.copy(pid2 = level)
            "pid3" -> current.copy(pid3 = level)
            "ln2Valve" -> current.copy(ln2Valve = level)
            "doorActuator" -> current.copy(doorActuator = level)
            "doorSwitch" -> current.copy(doorSwitch = level)
            "relayInternal" -> current.copy(relayInternal = level)
            "relayExternal" -> current.copy(relayExternal = level)
            else -> return
        }

        _capabilityOverrides.value = updated
        _systemStatus.value = _systemStatus.value.copy(capabilities = updated)
    }

    override suspend fun clearCapabilityOverrides() {
        _capabilityOverrides.value = null
        _systemStatus.value = _systemStatus.value.copy(capabilities = SubsystemCapabilities.DEFAULT)
    }
}
