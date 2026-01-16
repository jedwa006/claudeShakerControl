package com.shakercontrol.app.data.repository

import android.util.Log
import com.shakercontrol.app.data.ble.*
import com.shakercontrol.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * MachineRepository implementation using BLE communication.
 * Handles session management, heartbeat, and data mapping.
 */
@Singleton
class BleMachineRepository @Inject constructor(
    private val bleManager: BleManager
) : MachineRepository {

    companion object {
        private const val TAG = "BleMachineRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Session state
    private var sessionId: Int? = null
    private var leaseMs: Int = BleConstants.DEFAULT_LEASE_MS
    private var heartbeatJob: Job? = null

    // Run progress timer
    private var runTimerJob: Job? = null
    private var runStartTime: Long = 0L
    private var pausedElapsedMs: Long = 0L

    // Internal mutable state
    private val _systemStatus = MutableStateFlow(SystemStatus.DISCONNECTED)
    private val _pidData = MutableStateFlow<List<PidData>>(emptyList())
    private val _recipe = MutableStateFlow(Recipe.DEFAULT)
    private val _runProgress = MutableStateFlow<RunProgress?>(null)
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    private val _ioStatus = MutableStateFlow(IoStatus(0, 0))
    private val _interlockStatus = MutableStateFlow(
        InterlockStatus(
            isEStopActive = false,
            isDoorLocked = false,
            isLn2Present = false,
            isPowerEnabled = false,
            isHeatersEnabled = false,
            isMotorEnabled = false
        )
    )

    // Last heartbeat timestamp
    private var lastBleHeartbeatTime = 0L

    // Public flows exposed to UI - combine with BLE state for connection info
    override val systemStatus: StateFlow<SystemStatus> = combine(
        _systemStatus,
        bleManager.connectionState
    ) { status, bleState ->
        val connectionState = when (bleState) {
            BleConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
            BleConnectionState.CONNECTING -> ConnectionState.CONNECTING
            BleConnectionState.DISCOVERING_SERVICES -> ConnectionState.CONNECTING
            BleConnectionState.ENABLING_NOTIFICATIONS -> ConnectionState.CONNECTING
            BleConnectionState.CONNECTED -> {
                if (sessionId != null) {
                    if (System.currentTimeMillis() - lastBleHeartbeatTime < leaseMs) {
                        ConnectionState.LIVE
                    } else {
                        ConnectionState.DEGRADED
                    }
                } else {
                    ConnectionState.LIVE
                }
            }
        }

        status.copy(
            connectionState = connectionState,
            deviceName = bleManager.connectedDevice.value?.name,
            bleHeartbeatAgeMs = System.currentTimeMillis() - lastBleHeartbeatTime
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SystemStatus.DISCONNECTED
    )

    override val pidData: StateFlow<List<PidData>> = _pidData.asStateFlow()
    override val recipe: StateFlow<Recipe> = _recipe.asStateFlow()
    override val runProgress: StateFlow<RunProgress?> = _runProgress.asStateFlow()
    override val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()
    override val ioStatus: StateFlow<IoStatus> = _ioStatus.asStateFlow()
    override val interlockStatus: StateFlow<InterlockStatus> = _interlockStatus.asStateFlow()

    init {
        // Monitor BLE connection state
        scope.launch {
            bleManager.connectionState.collect { state ->
                when (state) {
                    BleConnectionState.CONNECTED -> {
                        // Start session after connection
                        openSession()
                    }
                    BleConnectionState.DISCONNECTED -> {
                        closeSession()
                        _systemStatus.value = SystemStatus.DISCONNECTED
                    }
                    else -> { /* Intermediate states */ }
                }
            }
        }

        // Process telemetry updates
        scope.launch {
            bleManager.telemetryFlow.collect { snapshot ->
                processTelemetry(snapshot)
            }
        }

        // Process events
        scope.launch {
            bleManager.eventFlow.collect { event ->
                processEvent(event)
            }
        }

        // Process ACKs
        scope.launch {
            bleManager.ackFlow.collect { ack ->
                processAck(ack)
            }
        }
    }

    private suspend fun openSession() {
        val nonce = Random.nextInt()
        Log.d(TAG, "Opening session with nonce: $nonce")

        val ack = bleManager.sendCommand(
            cmdId = CommandId.OPEN_SESSION,
            payload = CommandPayloadBuilder.openSession(nonce)
        )

        if (ack != null && ack.status == AckStatus.OK) {
            val sessionData = CommandAckParser.parseOpenSessionAckData(ack.optionalData)
            if (sessionData != null) {
                sessionId = sessionData.sessionId
                leaseMs = sessionData.leaseMs
                lastBleHeartbeatTime = System.currentTimeMillis()
                Log.d(TAG, "Session opened: id=${sessionData.sessionId}, lease=${sessionData.leaseMs}ms")
                startHeartbeat()
            }
        } else {
            Log.e(TAG, "Failed to open session: ${ack?.status}")
        }
    }

    private fun closeSession() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        sessionId = null
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(BleConstants.KEEPALIVE_INTERVAL_MS)
                sendKeepalive()
            }
        }
    }

    private fun sendKeepalive() {
        val id = sessionId ?: return

        bleManager.sendCommandAsync(
            cmdId = CommandId.KEEPALIVE,
            payload = CommandPayloadBuilder.keepalive(id)
        )

        // Update heartbeat time optimistically
        lastBleHeartbeatTime = System.currentTimeMillis()
    }

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
        val totalElapsed = totalElapsedMs.toInt().coerceAtLeast(0).seconds / 1000

        // Calculate which cycle and phase we're in
        val millingMs = currentRecipe.millingDuration.inWholeMilliseconds
        val holdMs = currentRecipe.holdDuration.inWholeMilliseconds
        val cycleMs = millingMs + holdMs
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
                val currentStatus = _systemStatus.value
                _systemStatus.value = currentStatus.copy(
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

    private fun processTelemetry(snapshot: TelemetryParser.TelemetrySnapshot) {
        lastBleHeartbeatTime = System.currentTimeMillis()

        // Map controller data to PidData
        val pidList = snapshot.controllers.map { controller ->
            PidData(
                controllerId = controller.controllerId,
                name = getPidName(controller.controllerId),
                processValue = controller.pv,
                setpointValue = controller.sv,
                outputPercent = controller.opPercent,
                mode = mapControllerModeToPidMode(controller.mode),
                isEnabled = controller.mode != ControllerMode.STOP,
                isOutputActive = controller.opPercent > 0,
                hasFault = false, // Checked via alarm bits
                ageMs = controller.ageMs,
                capabilityLevel = CapabilityLevel.REQUIRED
            )
        }
        _pidData.value = pidList

        // Map alarm bits to alarms
        updateAlarmsFromBits(snapshot.alarmBits)

        // Update IO status
        _ioStatus.value = IoStatus(
            digitalInputs = snapshot.diBits,
            relayOutputs = snapshot.roBits
        )

        // Update interlock status from bits
        _interlockStatus.value = InterlockStatus(
            isEStopActive = (snapshot.alarmBits.toInt() and AlarmBits.ESTOP_ACTIVE) != 0,
            isDoorLocked = (snapshot.diBits and 0x01) != 0,
            isLn2Present = (snapshot.diBits and 0x02) != 0,
            isPowerEnabled = (snapshot.roBits and 0x01) != 0,
            isHeatersEnabled = (snapshot.roBits and 0x02) != 0,
            isMotorEnabled = (snapshot.roBits and 0x04) != 0
        )

        // Update system status
        val alarmList = _alarms.value
        val currentStatus = _systemStatus.value
        _systemStatus.value = currentStatus.copy(
            mcuHeartbeatAgeMs = 0, // Fresh telemetry
            alarmSummary = AlarmSummary(
                totalCount = alarmList.count { it.state == AlarmState.ACTIVE },
                highCount = alarmList.count {
                    it.state == AlarmState.ACTIVE &&
                            (it.severity == AlarmSeverity.CRITICAL || it.severity == AlarmSeverity.ALARM)
                },
                highestSeverity = alarmList
                    .filter { it.state == AlarmState.ACTIVE }
                    .maxByOrNull { it.severity.value }
                    ?.severity
            )
        )
    }

    private fun getPidName(controllerId: Int): String = when (controllerId) {
        1 -> "Axle bearings"
        2 -> "Orbital bearings"
        3 -> "LN2 line"
        else -> "PID $controllerId"
    }

    private fun mapControllerModeToPidMode(mode: ControllerMode): PidMode = when (mode) {
        ControllerMode.STOP -> PidMode.STOP
        ControllerMode.MANUAL -> PidMode.MANUAL
        ControllerMode.AUTO -> PidMode.AUTO
        ControllerMode.PROGRAM -> PidMode.PROGRAM
    }

    private fun updateAlarmsFromBits(alarmBits: Long) {
        val newAlarms = mutableListOf<Alarm>()
        val bits = alarmBits.toInt()
        val now = Instant.now()

        if (bits and AlarmBits.ESTOP_ACTIVE != 0) {
            newAlarms.add(Alarm(
                id = "estop",
                eventId = EventId.ESTOP_ASSERTED.toInt(),
                message = "E-stop is active",
                severity = AlarmSeverity.CRITICAL,
                source = AlarmSource.SYSTEM,
                state = AlarmState.ACTIVE,
                timestamp = now,
                isAcknowledged = false
            ))
        }

        if (bits and AlarmBits.DOOR_INTERLOCK_OPEN != 0) {
            newAlarms.add(Alarm(
                id = "door",
                eventId = 0x2001,
                message = "Door interlock open",
                severity = AlarmSeverity.WARNING,
                source = AlarmSource.SYSTEM,
                state = AlarmState.ACTIVE,
                timestamp = now,
                isAcknowledged = false
            ))
        }

        if (bits and AlarmBits.OVER_TEMP != 0) {
            newAlarms.add(Alarm(
                id = "overtemp",
                eventId = 0x2002,
                message = "Over temperature",
                severity = AlarmSeverity.CRITICAL,
                source = AlarmSource.SYSTEM,
                state = AlarmState.ACTIVE,
                timestamp = now,
                isAcknowledged = false
            ))
        }

        if (bits and AlarmBits.RS485_FAULT != 0) {
            newAlarms.add(Alarm(
                id = "rs485",
                eventId = 0x2003,
                message = "RS-485 communication fault",
                severity = AlarmSeverity.WARNING,
                source = AlarmSource.SYSTEM,
                state = AlarmState.ACTIVE,
                timestamp = now,
                isAcknowledged = false
            ))
        }

        if (bits and AlarmBits.PID1_FAULT != 0) {
            newAlarms.add(Alarm(
                id = "pid1",
                eventId = 0x2006,
                message = "PID 1 fault",
                severity = AlarmSeverity.CRITICAL,
                source = AlarmSource.PID_1,
                state = AlarmState.ACTIVE,
                timestamp = now,
                isAcknowledged = false
            ))
        }

        if (bits and AlarmBits.PID2_FAULT != 0) {
            newAlarms.add(Alarm(
                id = "pid2",
                eventId = 0x2007,
                message = "PID 2 fault",
                severity = AlarmSeverity.CRITICAL,
                source = AlarmSource.PID_2,
                state = AlarmState.ACTIVE,
                timestamp = now,
                isAcknowledged = false
            ))
        }

        if (bits and AlarmBits.PID3_FAULT != 0) {
            newAlarms.add(Alarm(
                id = "pid3",
                eventId = 0x2008,
                message = "PID 3 fault",
                severity = AlarmSeverity.CRITICAL,
                source = AlarmSource.PID_3,
                state = AlarmState.ACTIVE,
                timestamp = now,
                isAcknowledged = false
            ))
        }

        _alarms.value = newAlarms
    }

    private fun processEvent(event: EventParser.Event) {
        Log.d(TAG, "Event received: id=${event.eventId}, severity=${event.severity}")

        when (event.eventId) {
            EventId.ESTOP_ASSERTED -> {
                val currentStatus = _systemStatus.value
                _systemStatus.value = currentStatus.copy(
                    machineState = MachineState.E_STOP
                )
            }
            EventId.ESTOP_CLEARED -> {
                val currentStatus = _systemStatus.value
                if (currentStatus.machineState == MachineState.E_STOP) {
                    _systemStatus.value = currentStatus.copy(
                        machineState = MachineState.IDLE
                    )
                }
            }
            EventId.RUN_STARTED -> {
                val currentStatus = _systemStatus.value
                _systemStatus.value = currentStatus.copy(
                    machineState = MachineState.RUNNING
                )
            }
            EventId.RUN_STOPPED -> {
                val currentStatus = _systemStatus.value
                _systemStatus.value = currentStatus.copy(
                    machineState = MachineState.IDLE
                )
                _runProgress.value = null
            }
        }
    }

    private fun processAck(ack: CommandAckParser.CommandAck) {
        Log.d(TAG, "ACK received: cmd=${ack.cmdId}, status=${ack.status}")

        if (ack.status != AckStatus.OK) {
            Log.w(TAG, "Command ${ack.cmdId} rejected: ${ack.status}, detail=${ack.detail}")
        }
    }

    // MachineRepository interface implementations

    override suspend fun startScan() {
        bleManager.startScan()
    }

    override suspend fun stopScan() {
        bleManager.stopScan()
    }

    override suspend fun connect(deviceAddress: String) {
        bleManager.connect(deviceAddress)
    }

    override suspend fun disconnect() {
        bleManager.disconnect()
    }

    override suspend fun forgetDevice() {
        bleManager.disconnect()
        // Could persist/clear last connected device here
    }

    override suspend fun startRun(): Result<Unit> {
        val id = sessionId
        if (id == null) {
            Log.e(TAG, "Cannot start run: no session")
            return Result.failure(IllegalStateException("No session"))
        }

        val ack = bleManager.sendCommand(
            cmdId = CommandId.START_RUN,
            payload = CommandPayloadBuilder.startRun(id, RunMode.NORMAL)
        )

        return if (ack?.status == AckStatus.OK) {
            val currentRecipe = _recipe.value
            pausedElapsedMs = 0L  // Reset pause accumulator
            _runProgress.value = RunProgress(
                currentCycle = 1,
                totalCycles = currentRecipe.cycleCount,
                currentPhase = RunPhase.MILLING,
                phaseElapsed = 0.seconds,
                phaseRemaining = currentRecipe.millingDuration,
                totalRemaining = currentRecipe.totalRuntime
            )
            val currentStatus = _systemStatus.value
            _systemStatus.value = currentStatus.copy(
                machineState = MachineState.RUNNING
            )
            startRunTimer()  // Start the countdown timer
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException("Start rejected: ${ack?.status}"))
        }
    }

    override suspend fun pauseRun(): Result<Unit> {
        val id = sessionId ?: return Result.failure(IllegalStateException("No session"))

        val ack = bleManager.sendCommand(
            cmdId = CommandId.PAUSE_RUN,
            payload = CommandPayloadBuilder.pauseRun(id)
        )

        return if (ack?.status == AckStatus.OK) {
            pauseRunTimer()  // Pause the countdown timer
            val currentStatus = _systemStatus.value
            _systemStatus.value = currentStatus.copy(
                machineState = MachineState.PAUSED
            )
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException("Pause rejected: ${ack?.status}"))
        }
    }

    override suspend fun resumeRun(): Result<Unit> {
        val id = sessionId ?: return Result.failure(IllegalStateException("No session"))

        val ack = bleManager.sendCommand(
            cmdId = CommandId.START_RUN,
            payload = CommandPayloadBuilder.startRun(id, RunMode.NORMAL)
        )

        return if (ack?.status == AckStatus.OK) {
            resumeRunTimer()  // Resume the countdown timer
            val currentStatus = _systemStatus.value
            _systemStatus.value = currentStatus.copy(
                machineState = MachineState.RUNNING
            )
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException("Resume rejected: ${ack?.status}"))
        }
    }

    override suspend fun stopRun(): Result<Unit> {
        val id = sessionId ?: return Result.failure(IllegalStateException("No session"))

        val ack = bleManager.sendCommand(
            cmdId = CommandId.STOP_RUN,
            payload = CommandPayloadBuilder.stopRun(id, StopMode.NORMAL_STOP)
        )

        return if (ack?.status == AckStatus.OK) {
            stopRunTimer()  // Stop the countdown timer
            _runProgress.value = null
            val currentStatus = _systemStatus.value
            _systemStatus.value = currentStatus.copy(
                machineState = MachineState.IDLE
            )
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException("Stop rejected: ${ack?.status}"))
        }
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        _recipe.value = recipe
    }

    override suspend fun enableServiceMode() {
        val currentStatus = _systemStatus.value
        _systemStatus.value = currentStatus.copy(
            isServiceModeEnabled = true
        )
    }

    override suspend fun disableServiceMode() {
        val currentStatus = _systemStatus.value
        _systemStatus.value = currentStatus.copy(
            isServiceModeEnabled = false
        )
    }

    override suspend fun setSetpoint(controllerId: Int, setpoint: Float): Result<Unit> {
        val id = sessionId ?: return Result.failure(IllegalStateException("No session"))

        // Scale setpoint by 10 (protocol uses x10 format) and convert to Short
        val svX10 = (setpoint * 10).toInt().toShort()

        val ack = bleManager.sendCommand(
            cmdId = CommandId.SET_SV,
            payload = CommandPayloadBuilder.setSv(controllerId, svX10)
        )

        return if (ack?.status == AckStatus.OK) {
            // Update local state optimistically
            val currentPidList = _pidData.value.toMutableList()
            val index = currentPidList.indexOfFirst { it.controllerId == controllerId }
            if (index >= 0) {
                currentPidList[index] = currentPidList[index].copy(setpointValue = setpoint)
                _pidData.value = currentPidList
            }
            Result.success(Unit)
        } else {
            val detail = when (ack?.detail) {
                AckDetail.CONTROLLER_OFFLINE -> "Controller offline"
                AckDetail.PARAM_OUT_OF_RANGE -> "Value out of range"
                else -> "Rejected: ${ack?.status}"
            }
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun setMode(controllerId: Int, mode: PidMode): Result<Unit> {
        val id = sessionId ?: return Result.failure(IllegalStateException("No session"))

        val controllerMode = when (mode) {
            PidMode.STOP -> ControllerMode.STOP
            PidMode.MANUAL -> ControllerMode.MANUAL
            PidMode.AUTO -> ControllerMode.AUTO
            PidMode.PROGRAM -> ControllerMode.PROGRAM
        }

        val ack = bleManager.sendCommand(
            cmdId = CommandId.SET_MODE,
            payload = CommandPayloadBuilder.setMode(controllerId, controllerMode)
        )

        return if (ack?.status == AckStatus.OK) {
            // Update local state optimistically
            val currentPidList = _pidData.value.toMutableList()
            val index = currentPidList.indexOfFirst { it.controllerId == controllerId }
            if (index >= 0) {
                currentPidList[index] = currentPidList[index].copy(
                    mode = mode,
                    isEnabled = mode != PidMode.STOP
                )
                _pidData.value = currentPidList
            }
            Result.success(Unit)
        } else {
            val detail = when (ack?.detail) {
                AckDetail.CONTROLLER_OFFLINE -> "Controller offline"
                else -> "Rejected: ${ack?.status}"
            }
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun acknowledgeAlarm(alarmId: String): Result<Unit> {
        // For now, just update local state
        // Real implementation would send ACK_ALARM command to MCU
        val currentAlarms = _alarms.value.toMutableList()
        val index = currentAlarms.indexOfFirst { it.id == alarmId }
        if (index >= 0) {
            currentAlarms[index] = currentAlarms[index].copy(isAcknowledged = true)
            _alarms.value = currentAlarms
        }
        return Result.success(Unit)
    }

    override suspend fun clearLatchedAlarms(): Result<Unit> {
        val id = sessionId ?: return Result.failure(IllegalStateException("No session"))

        val ack = bleManager.sendCommand(
            cmdId = CommandId.CLEAR_LATCHED_ALARMS,
            payload = byteArrayOf() // No payload for this command
        )

        return if (ack?.status == AckStatus.OK) {
            // Clear acknowledged alarms from local state
            val currentAlarms = _alarms.value.filter {
                it.state == AlarmState.ACTIVE && !it.isAcknowledged
            }
            _alarms.value = currentAlarms
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException("Clear alarms rejected: ${ack?.status}"))
        }
    }
}
