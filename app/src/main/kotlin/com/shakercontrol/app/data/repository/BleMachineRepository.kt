package com.shakercontrol.app.data.repository

import android.util.Log
import com.shakercontrol.app.data.ble.*
import com.shakercontrol.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
        private const val RSSI_POLL_INTERVAL_MS = 2000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Session state
    private var sessionId: Int? = null
    private var leaseMs: Int = BleConstants.DEFAULT_LEASE_MS
    private var heartbeatJob: Job? = null
    private var lastKeepaliveTime: Long = 0L
    private var rssiPollJob: Job? = null

    // Run progress timer
    private var runTimerJob: Job? = null
    private var runStartTime: Long = 0L
    private var pausedElapsedMs: Long = 0L

    // RSSI history for signal quality tracking
    private val rssiHistory = mutableListOf<Int>()

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
        val now = System.currentTimeMillis()
        val sessionLeaseAge = if (lastKeepaliveTime > 0) now - lastKeepaliveTime else 0L

        val connectionState = when (bleState) {
            BleConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
            BleConnectionState.CONNECTING -> ConnectionState.CONNECTING
            BleConnectionState.DISCOVERING_SERVICES -> ConnectionState.CONNECTING
            BleConnectionState.ENABLING_NOTIFICATIONS -> ConnectionState.CONNECTING
            BleConnectionState.CONNECTED -> {
                if (sessionId != null) {
                    if (sessionLeaseAge < leaseMs) {
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
            bleHeartbeatAgeMs = now - lastBleHeartbeatTime,
            sessionLeaseMs = leaseMs,
            sessionLeaseAgeMs = sessionLeaseAge,
            rssiHistory = rssiHistory.toList()
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

    // Alarm history tracking
    private val _alarmHistory = MutableStateFlow<List<AlarmHistoryEntry>>(emptyList())
    override val alarmHistory: StateFlow<List<AlarmHistoryEntry>> = _alarmHistory.asStateFlow()
    private val _unacknowledgedAlarmBits = MutableStateFlow(0)
    override val unacknowledgedAlarmBits: StateFlow<Int> = _unacknowledgedAlarmBits.asStateFlow()
    private var previousAlarmBits: Long = 0L
    // Track previous probe error states per controller (controllerId -> ProbeError)
    private val previousProbeErrors = mutableMapOf<Int, ProbeError>()

    // Simulation mode (for testing DI in service mode)
    private val _isSimulationEnabled = MutableStateFlow(false)
    override val isSimulationEnabled: StateFlow<Boolean> = _isSimulationEnabled.asStateFlow()
    private var simulatedInputs = 0
    private var realInputs = 0

    // Capability overrides (service mode only)
    private val _capabilityOverrides = MutableStateFlow<SubsystemCapabilities?>(null)
    override val capabilityOverrides: StateFlow<SubsystemCapabilities?> = _capabilityOverrides.asStateFlow()

    // MCU idle timeout (synced on connection)
    private val _mcuIdleTimeoutMinutes = MutableStateFlow<Int?>(null)
    override val mcuIdleTimeoutMinutes: StateFlow<Int?> = _mcuIdleTimeoutMinutes.asStateFlow()

    init {
        // Monitor BLE connection state
        scope.launch {
            bleManager.connectionState.collect { state ->
                when (state) {
                    BleConnectionState.CONNECTED -> {
                        // Start session after connection (small delay to ensure GATT is stable)
                        delay(200)
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

        // Process device info updates
        scope.launch {
            bleManager.deviceInfo.collect { info ->
                if (info != null) {
                    val currentStatus = _systemStatus.value
                    _systemStatus.value = currentStatus.copy(
                        firmwareVersion = info.firmwareVersionString,
                        firmwareBuildId = info.buildIdHex,
                        protocolVersion = info.protocolVersion
                    )
                }
            }
        }

        // Update device name when connected
        scope.launch {
            bleManager.connectedDevice.collect { device ->
                val currentStatus = _systemStatus.value
                _systemStatus.value = currentStatus.copy(
                    deviceName = device?.name
                )
            }
        }

        // Collect RSSI readings
        scope.launch {
            bleManager.rssi.collect { rssi ->
                if (rssi != null) {
                    updateRssiHistory(rssi)
                }
            }
        }
    }

    private fun updateRssiHistory(rssi: Int) {
        rssiHistory.add(rssi)
        // Keep only the last N readings
        while (rssiHistory.size > SystemStatus.RSSI_HISTORY_SIZE) {
            rssiHistory.removeAt(0)
        }
        // Update the current RSSI in system status
        val currentStatus = _systemStatus.value
        _systemStatus.value = currentStatus.copy(rssiDbm = rssi)
    }

    private suspend fun openSession() {
        val nonce = Random.nextInt()
        Log.w(TAG, ">>> Opening session with nonce: $nonce")

        val ack = bleManager.sendCommand(
            cmdId = CommandId.OPEN_SESSION,
            payload = CommandPayloadBuilder.openSession(nonce)
        )
        Log.w(TAG, "<<< OPEN_SESSION ACK received: ${ack?.status}, optionalData size=${ack?.optionalData?.size ?: 0}")

        if (ack != null && ack.status == AckStatus.OK) {
            val sessionData = CommandAckParser.parseOpenSessionAckData(ack.optionalData)
            if (sessionData != null) {
                sessionId = sessionData.sessionId
                leaseMs = sessionData.leaseMs
                val now = System.currentTimeMillis()
                lastBleHeartbeatTime = now
                lastKeepaliveTime = now
                rssiHistory.clear() // Clear RSSI history on new session
                Log.w(TAG, ">>> Session opened: id=${sessionData.sessionId}, lease=${sessionData.leaseMs}ms")
                startHeartbeat()
                startRssiPolling()
                // Query MCU's current idle timeout on connect (MCU is source of truth)
                queryMcuIdleTimeout()
            }
        } else {
            Log.e(TAG, ">>> Failed to open session: ${ack?.status}, detail=${ack?.detail}")
        }
    }

    private fun startRssiPolling() {
        rssiPollJob?.cancel()
        rssiPollJob = scope.launch {
            while (isActive) {
                bleManager.readRssi()
                delay(RSSI_POLL_INTERVAL_MS)
            }
        }
    }

    private fun closeSession() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        rssiPollJob?.cancel()
        rssiPollJob = null
        sessionId = null
        lastKeepaliveTime = 0L
        rssiHistory.clear()
        _mcuIdleTimeoutMinutes.value = null // Clear synced value on disconnect
        // Reset alarm tracking state (but keep history)
        previousAlarmBits = 0L
        previousProbeErrors.clear()
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
        val now = System.currentTimeMillis()
        lastBleHeartbeatTime = now
        lastKeepaliveTime = now
    }

    /**
     * Query the MCU's current lazy polling setting.
     * MCU is the source of truth - this is called on connect to sync the app's display.
     * Uses CMD_GET_LAZY_POLL (0x0061) which returns [enabled, timeout_min].
     */
    private fun queryMcuIdleTimeout() {
        scope.launch {
            val result = getIdleTimeout()
            result.onSuccess { minutes ->
                Log.d(TAG, "MCU lazy poll queried: timeout=$minutes minutes (0=disabled)")
                _mcuIdleTimeoutMinutes.value = minutes
                // Also update SystemStatus so Diagnostics shows correct value immediately
                val currentStatus = _systemStatus.value
                _systemStatus.value = currentStatus.copy(
                    idleTimeoutMinutes = minutes,
                    lazyPollActive = false // Reset to false on connect, MCU will report actual state via telemetry
                )
            }
            result.onFailure { e ->
                Log.w(TAG, "Failed to query MCU lazy poll settings: ${e.message}")
            }
        }
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

        // Get current capabilities for proper capability levels
        val capabilities = _systemStatus.value.capabilities

        // Extract lazy polling state from run state (needed for PidData thresholds)
        // Use current system status as fallback if run state not in telemetry
        val runState = snapshot.runState
        val lazyPollActive = runState?.lazyPollActive ?: _systemStatus.value.lazyPollActive

        // Map controller data to PidData
        val pidList = snapshot.controllers.map { controller ->
            val isLn2Controller = controller.controllerId == 1
            val probeError = PidData.detectProbeError(controller.pv, isLn2Controller)

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
                capabilityLevel = capabilities.getPidCapability(controller.controllerId),
                probeError = probeError,
                lazyPollActive = lazyPollActive  // Pass to PidData for dynamic thresholds
            )
        }
        _pidData.value = pidList

        // Track probe error transitions for history
        trackProbeErrorTransitions(pidList)

        // Map alarm bits to alarms
        updateAlarmsFromBits(snapshot.alarmBits)

        // Update IO status
        // Store real inputs for when simulation is disabled
        realInputs = snapshot.diBits
        // Use simulated inputs if simulation is enabled
        val effectiveDiBits = if (_isSimulationEnabled.value) simulatedInputs else snapshot.diBits
        _ioStatus.value = IoStatus(
            digitalInputs = effectiveDiBits,
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

        // Extract idle timeout from run state (lazyPollActive already extracted above)
        val idleTimeoutMin = runState?.idleTimeoutMin ?: 0

        // Debug: log run state presence periodically
        if (runState == null && System.currentTimeMillis() % 5000 < 100) {
            Log.w(TAG, "Telemetry: No run state data (firmware may not be sending extended telemetry)")
        }

        // Update mcuIdleTimeoutMinutes from telemetry (MCU is source of truth)
        if (runState != null && _mcuIdleTimeoutMinutes.value != idleTimeoutMin) {
            Log.d(TAG, "Idle timeout synced from telemetry: $idleTimeoutMin min, lazyPollActive=$lazyPollActive")
            _mcuIdleTimeoutMinutes.value = idleTimeoutMin
        }

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
            ),
            // Only update lazy polling state if firmware provides run state data
            // Otherwise preserve existing values (set via SET_IDLE_TIMEOUT command)
            lazyPollActive = if (runState != null) lazyPollActive else currentStatus.lazyPollActive,
            idleTimeoutMinutes = if (runState != null) idleTimeoutMin else currentStatus.idleTimeoutMinutes
        )
    }

    private fun getPidName(controllerId: Int): String = when (controllerId) {
        1 -> "LN2 (Cold)"
        2 -> "Axle bearings"
        3 -> "Orbital bearings"
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

        // Track alarm transitions for history
        val changed = bits.toLong() xor previousAlarmBits
        if (changed != 0L) {
            trackAlarmTransitions(bits.toLong(), changed, now)
        }
        previousAlarmBits = bits.toLong()

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

    /**
     * Track alarm bit transitions for history.
     * Records both assertions and clears for debugging transient alarms.
     */
    private fun trackAlarmTransitions(currentBits: Long, changedBits: Long, timestamp: Instant) {
        val newEntries = mutableListOf<AlarmHistoryEntry>()
        var newUnackBits = _unacknowledgedAlarmBits.value

        for (bit in AlarmBitDefinitions.ALL_BITS) {
            if ((changedBits and bit.toLong()) != 0L) {
                val isNowSet = (currentBits and bit.toLong()) != 0L

                newEntries.add(
                    AlarmHistoryEntry(
                        timestamp = timestamp,
                        alarmBit = bit,
                        alarmName = AlarmBitDefinitions.getName(bit),
                        severity = AlarmBitDefinitions.getSeverity(bit),
                        wasAsserted = isNowSet
                    )
                )

                if (isNowSet) {
                    // Add to unacknowledged
                    newUnackBits = newUnackBits or bit
                    Log.d(TAG, "Alarm ASSERTED: ${AlarmBitDefinitions.getName(bit)}")
                } else {
                    Log.d(TAG, "Alarm CLEARED: ${AlarmBitDefinitions.getName(bit)}")
                }
            }
        }

        if (newEntries.isNotEmpty()) {
            // Prepend new entries to history (most recent first), keep last 100
            val currentHistory = _alarmHistory.value
            _alarmHistory.value = (newEntries + currentHistory).take(100)
            _unacknowledgedAlarmBits.value = newUnackBits
        }
    }

    /**
     * Track probe error transitions for history.
     * App-detected probe errors (HHHH/LLLL) are logged separately from MCU alarms
     * to help debug transient probe issues.
     */
    private fun trackProbeErrorTransitions(pidList: List<PidData>) {
        val newEntries = mutableListOf<AlarmHistoryEntry>()
        val now = Instant.now()

        for (pid in pidList) {
            val previousError = previousProbeErrors[pid.controllerId] ?: ProbeError.NONE
            val currentError = pid.probeError

            if (previousError != currentError) {
                // Transition detected
                if (currentError != ProbeError.NONE) {
                    // Probe error appeared
                    val errorName = "${pid.name} Probe ${currentError.shortName}"
                    newEntries.add(
                        AlarmHistoryEntry(
                            timestamp = now,
                            alarmBit = 0,  // Not an MCU alarm bit
                            alarmName = errorName,
                            severity = AlarmSeverity.WARNING,  // Probe errors are warnings, not critical
                            wasAsserted = true,
                            source = AlarmHistorySource.APP_PROBE_ERROR
                        )
                    )
                    Log.d(TAG, "Probe error DETECTED: $errorName (PV=${pid.processValue}°C)")
                } else {
                    // Probe error cleared
                    val errorName = "${pid.name} Probe ${previousError.shortName}"
                    newEntries.add(
                        AlarmHistoryEntry(
                            timestamp = now,
                            alarmBit = 0,
                            alarmName = errorName,
                            severity = AlarmSeverity.WARNING,
                            wasAsserted = false,
                            source = AlarmHistorySource.APP_PROBE_ERROR
                        )
                    )
                    Log.d(TAG, "Probe error CLEARED: $errorName")
                }

                // Update tracked state
                previousProbeErrors[pid.controllerId] = currentError
            }
        }

        if (newEntries.isNotEmpty()) {
            // Prepend new entries to history (most recent first), keep last 100
            val currentHistory = _alarmHistory.value
            _alarmHistory.value = (newEntries + currentHistory).take(100)
        }
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

    override suspend fun requestPvSvRefresh(controllerId: Int): Result<Unit> {
        require(controllerId in 1..3) { "Controller ID must be 1-3" }

        val ack = bleManager.sendCommand(
            cmdId = CommandId.REQUEST_PV_SV_REFRESH,
            payload = CommandPayloadBuilder.requestPvSvRefresh(controllerId)
        )

        return if (ack?.status == AckStatus.OK) {
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException("Refresh rejected: ${ack?.status}"))
        }
    }

    override suspend fun setPidParams(
        controllerId: Int,
        pGain: Float,
        iTime: Int,
        dTime: Int
    ): Result<Unit> {
        require(controllerId in 1..3) { "Controller ID must be 1-3" }

        val pGainX10 = (pGain * 10).toInt().toShort()
        val ack = bleManager.sendCommand(
            cmdId = CommandId.SET_PID_PARAMS,
            payload = CommandPayloadBuilder.setPidParams(controllerId, pGainX10, iTime, dTime)
        )

        return if (ack?.status == AckStatus.OK) {
            Result.success(Unit)
        } else {
            val detail = when (ack?.detail) {
                AckDetail.CONTROLLER_OFFLINE -> "Controller offline"
                AckDetail.PARAM_OUT_OF_RANGE -> "Parameter out of range"
                else -> "Rejected: ${ack?.status}"
            }
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun readPidParams(controllerId: Int): Result<PidParams> {
        require(controllerId in 1..3) { "Controller ID must be 1-3" }

        val ack = bleManager.sendCommand(
            cmdId = CommandId.READ_PID_PARAMS,
            payload = CommandPayloadBuilder.readPidParams(controllerId)
        )

        return if (ack?.status == AckStatus.OK && ack.optionalData.size >= 7) {
            val buffer = ByteBuffer.wrap(ack.optionalData).order(ByteOrder.LITTLE_ENDIAN)
            val echoId = buffer.get().toInt() and 0xFF
            val pGainX10 = buffer.short
            val iTimeVal = buffer.short.toInt() and 0xFFFF
            val dTimeVal = buffer.short.toInt() and 0xFFFF

            Result.success(PidParams(
                controllerId = echoId,
                pGain = pGainX10 / 10f,
                iTime = iTimeVal,
                dTime = dTimeVal
            ))
        } else {
            val detail = when (ack?.detail) {
                AckDetail.CONTROLLER_OFFLINE -> "Controller offline"
                else -> "Read failed: ${ack?.status}"
            }
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun startAutotune(controllerId: Int): Result<Unit> {
        require(controllerId in 1..3) { "Controller ID must be 1-3" }

        val ack = bleManager.sendCommand(
            cmdId = CommandId.START_AUTOTUNE,
            payload = CommandPayloadBuilder.startAutotune(controllerId)
        )

        return if (ack?.status == AckStatus.OK) {
            Result.success(Unit)
        } else {
            val detail = when (ack?.detail) {
                AckDetail.CONTROLLER_OFFLINE -> "Controller offline"
                else -> "Start autotune rejected: ${ack?.status}"
            }
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun stopAutotune(controllerId: Int): Result<Unit> {
        require(controllerId in 1..3) { "Controller ID must be 1-3" }

        val ack = bleManager.sendCommand(
            cmdId = CommandId.STOP_AUTOTUNE,
            payload = CommandPayloadBuilder.stopAutotune(controllerId)
        )

        return if (ack?.status == AckStatus.OK) {
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException("Stop autotune rejected: ${ack?.status}"))
        }
    }

    override suspend fun setAlarmLimits(
        controllerId: Int,
        alarm1: Float,
        alarm2: Float
    ): Result<Unit> {
        require(controllerId in 1..3) { "Controller ID must be 1-3" }

        val alarm1X10 = (alarm1 * 10).toInt().toShort()
        val alarm2X10 = (alarm2 * 10).toInt().toShort()
        val ack = bleManager.sendCommand(
            cmdId = CommandId.SET_ALARM_LIMITS,
            payload = CommandPayloadBuilder.setAlarmLimits(controllerId, alarm1X10, alarm2X10)
        )

        return if (ack?.status == AckStatus.OK) {
            Result.success(Unit)
        } else {
            val detail = when (ack?.detail) {
                AckDetail.CONTROLLER_OFFLINE -> "Controller offline"
                AckDetail.PARAM_OUT_OF_RANGE -> "Limit out of range"
                else -> "Rejected: ${ack?.status}"
            }
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun readAlarmLimits(controllerId: Int): Result<AlarmLimits> {
        require(controllerId in 1..3) { "Controller ID must be 1-3" }

        val ack = bleManager.sendCommand(
            cmdId = CommandId.READ_ALARM_LIMITS,
            payload = CommandPayloadBuilder.readAlarmLimits(controllerId)
        )

        return if (ack?.status == AckStatus.OK && ack.optionalData.size >= 5) {
            val buffer = ByteBuffer.wrap(ack.optionalData).order(ByteOrder.LITTLE_ENDIAN)
            val echoId = buffer.get().toInt() and 0xFF
            val alarm1X10 = buffer.short
            val alarm2X10 = buffer.short

            Result.success(AlarmLimits(
                controllerId = echoId,
                alarm1 = alarm1X10 / 10f,
                alarm2 = alarm2X10 / 10f
            ))
        } else {
            val detail = when (ack?.detail) {
                AckDetail.CONTROLLER_OFFLINE -> "Controller offline"
                else -> "Read failed: ${ack?.status}"
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

    override suspend fun acknowledgeAlarmBits(bits: Int) {
        // Remove acknowledged bits from unacknowledged set
        _unacknowledgedAlarmBits.value = _unacknowledgedAlarmBits.value and bits.inv()
        Log.d(TAG, "Acknowledged alarm bits: 0x${bits.toString(16)}, remaining unack: 0x${_unacknowledgedAlarmBits.value.toString(16)}")
    }

    override suspend fun clearAlarmHistory() {
        _alarmHistory.value = emptyList()
        _unacknowledgedAlarmBits.value = 0
        Log.d(TAG, "Cleared alarm history")
    }

    override suspend fun setRelay(channel: Int, on: Boolean): Result<Unit> {
        require(channel in 1..8) { "Channel must be 1-8" }

        val relayState = if (on) RelayState.ON else RelayState.OFF
        val ack = bleManager.sendCommand(
            cmdId = CommandId.SET_RELAY,
            payload = CommandPayloadBuilder.setRelay(channel, relayState)
        )

        return if (ack?.status == AckStatus.OK) {
            // Update local state optimistically
            val current = _ioStatus.value
            val newBits = if (on) {
                current.relayOutputs or (1 shl (channel - 1))
            } else {
                current.relayOutputs and (1 shl (channel - 1)).inv()
            }
            _ioStatus.value = current.copy(relayOutputs = newBits)
            Log.d(TAG, "Relay CH$channel set to ${if (on) "ON" else "OFF"}")
            Result.success(Unit)
        } else {
            val detail = when (ack?.detail) {
                AckDetail.SESSION_INVALID -> "Session invalid"
                AckDetail.INTERLOCK_OPEN -> "Interlock open"
                else -> "Rejected: ${ack?.status}"
            }
            Log.w(TAG, "setRelay failed: $detail")
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun setRelayMask(mask: Int, values: Int): Result<Unit> {
        require(mask in 0..0xFF) { "Mask must be 0-255" }
        require(values in 0..0xFF) { "Values must be 0-255" }

        val ack = bleManager.sendCommand(
            cmdId = CommandId.SET_RELAY_MASK,
            payload = CommandPayloadBuilder.setRelayMask(mask, values)
        )

        return if (ack?.status == AckStatus.OK) {
            // Update local state optimistically for all channels in the mask
            val current = _ioStatus.value
            // Clear bits where mask is set, then set new values
            val clearedBits = current.relayOutputs and mask.inv()
            val newBits = clearedBits or (values and mask)
            _ioStatus.value = current.copy(relayOutputs = newBits)
            Log.d(TAG, "Relay mask applied: mask=0x${mask.toString(16)}, values=0x${values.toString(16)}")
            Result.success(Unit)
        } else {
            val detail = when (ack?.detail) {
                AckDetail.SESSION_INVALID -> "Session invalid"
                AckDetail.INTERLOCK_OPEN -> "Interlock open"
                else -> "Rejected: ${ack?.status}"
            }
            Log.w(TAG, "setRelayMask failed: $detail")
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun setSimulationEnabled(enabled: Boolean) {
        _isSimulationEnabled.value = enabled
        if (!enabled) {
            // Restore real inputs when simulation is disabled
            simulatedInputs = 0
            _ioStatus.value = _ioStatus.value.copy(digitalInputs = realInputs)
        }
        Log.d(TAG, "Simulation mode ${if (enabled) "enabled" else "disabled"}")
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
            _ioStatus.value = _ioStatus.value.copy(digitalInputs = simulatedInputs)
        }
        Log.d(TAG, "Simulated DI$channel set to ${if (high) "HIGH" else "LOW"}")
    }

    override suspend fun setCapabilityOverride(subsystem: String, level: CapabilityLevel) {
        // Get current overrides or start from default capabilities
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
            else -> {
                Log.w(TAG, "Unknown subsystem: $subsystem")
                return
            }
        }

        _capabilityOverrides.value = updated

        // Update system status with new capabilities
        _systemStatus.value = _systemStatus.value.copy(capabilities = updated)
        Log.d(TAG, "Capability override: $subsystem -> ${level.displayName}")
    }

    override suspend fun clearCapabilityOverrides() {
        _capabilityOverrides.value = null
        // Reset to default capabilities
        _systemStatus.value = _systemStatus.value.copy(capabilities = SubsystemCapabilities.DEFAULT)
        Log.d(TAG, "Capability overrides cleared")
    }

    // ==================== Generic Modbus Register Access ====================

    override suspend fun readRegisters(controllerId: Int, startAddress: Int, count: Int): Result<List<Int>> {
        require(controllerId in 1..3) { "Controller ID must be 1-3" }
        require(count in 1..16) { "Count must be 1-16" }

        val payload = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(controllerId.toByte())
            .putShort(startAddress.toShort())
            .put(count.toByte())
            .array()

        val ack = bleManager.sendCommand(
            cmdId = CommandId.READ_REGISTERS,
            payload = payload
        )

        return if (ack?.status == AckStatus.OK && ack.optionalData.size >= 4 + count * 2) {
            val buffer = ByteBuffer.wrap(ack.optionalData).order(ByteOrder.LITTLE_ENDIAN)
            val echoId = buffer.get().toInt() and 0xFF
            val echoAddr = buffer.short.toInt() and 0xFFFF
            val echoCount = buffer.get().toInt() and 0xFF

            val values = (0 until echoCount).map {
                buffer.short.toInt() and 0xFFFF
            }

            Log.d(TAG, "Read registers: controller=$echoId, addr=0x${echoAddr.toString(16)}, count=$echoCount, values=$values")
            Result.success(values)
        } else {
            val detail = when (ack?.detail) {
                AckDetail.CONTROLLER_OFFLINE -> "Controller offline"
                else -> "Read failed: ${ack?.status}"
            }
            Log.e(TAG, "readRegisters failed: $detail")
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun writeRegister(controllerId: Int, address: Int, value: Int): Result<Unit> {
        require(controllerId in 1..3) { "Controller ID must be 1-3" }
        require(value in 0..0xFFFF) { "Value must be 0-65535" }

        val payload = ByteBuffer.allocate(5)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(controllerId.toByte())
            .putShort(address.toShort())
            .putShort(value.toShort())
            .array()

        val ack = bleManager.sendCommand(
            cmdId = CommandId.WRITE_REGISTER,
            payload = payload
        )

        return if (ack?.status == AckStatus.OK) {
            Log.d(TAG, "Write register: controller=$controllerId, addr=0x${address.toString(16)}, value=$value")
            Result.success(Unit)
        } else {
            val detail = when (ack?.detail) {
                AckDetail.CONTROLLER_OFFLINE -> "Controller offline"
                AckDetail.PARAM_OUT_OF_RANGE -> "Value out of range"
                else -> "Write failed: ${ack?.status}"
            }
            Log.e(TAG, "writeRegister failed: $detail")
            Result.failure(RuntimeException(detail))
        }
    }

    // ==================== Lazy Polling Configuration ====================
    // Firmware v0.3.7+ uses CMD_SET_LAZY_POLL (0x0060) and CMD_GET_LAZY_POLL (0x0061)
    // Payload format: [enable (u8), timeout_min (u8)]

    override suspend fun setIdleTimeout(minutes: Int): Result<Unit> {
        require(minutes in 0..255) { "Timeout must be 0-255 minutes" }

        // Try new firmware format first: [enable (0/1), timeout_min]
        val enable: Byte = if (minutes > 0) 1 else 0
        val newPayload = byteArrayOf(enable, minutes.toByte())

        Log.w(TAG, ">>> SET_LAZY_POLL (0x0060): enable=$enable, timeout=$minutes minutes")

        var ack = bleManager.sendCommand(
            cmdId = CommandId.SET_LAZY_POLL,
            payload = newPayload
        )

        Log.w(TAG, "<<< SET_LAZY_POLL ACK: status=${ack?.status}, detail=${ack?.detail}")

        // If new command fails, try legacy format: [timeout_min] (0=disabled)
        if (ack?.status != AckStatus.OK) {
            Log.w(TAG, "SET_LAZY_POLL failed, trying legacy SET_IDLE_TIMEOUT (0x0040)")
            val legacyPayload = byteArrayOf(minutes.toByte())
            ack = bleManager.sendCommand(
                cmdId = CommandId.SET_IDLE_TIMEOUT_LEGACY,
                payload = legacyPayload
            )
            Log.w(TAG, "<<< SET_IDLE_TIMEOUT (legacy) ACK: status=${ack?.status}, detail=${ack?.detail}")
        }

        return if (ack?.status == AckStatus.OK) {
            Log.w(TAG, "Set idle timeout SUCCESS: $minutes minutes")
            _mcuIdleTimeoutMinutes.value = minutes // Update local state to match MCU
            // Also update SystemStatus directly (fallback when firmware doesn't send extended telemetry)
            val currentStatus = _systemStatus.value
            _systemStatus.value = currentStatus.copy(
                idleTimeoutMinutes = minutes
            )
            Result.success(Unit)
        } else {
            val detail = when (ack?.detail) {
                AckDetail.PARAM_OUT_OF_RANGE -> "Value out of range"
                else -> "Set idle timeout failed: ${ack?.status}"
            }
            Log.e(TAG, "setIdleTimeout FAILED: $detail")
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun getIdleTimeout(): Result<Int> {
        // Try new command format first (0x0061)
        Log.d(TAG, ">>> GET_LAZY_POLL (0x0061)")
        var ack = bleManager.sendCommand(
            cmdId = CommandId.GET_LAZY_POLL,
            payload = byteArrayOf()
        )

        // If new command fails, try legacy command (0x0041)
        if (ack?.status != AckStatus.OK) {
            Log.d(TAG, "GET_LAZY_POLL failed (${ack?.status}), trying legacy GET_IDLE_TIMEOUT (0x0041)")
            ack = bleManager.sendCommand(
                cmdId = CommandId.GET_IDLE_TIMEOUT_LEGACY,
                payload = byteArrayOf()
            )
        }

        // New firmware format: [enabled (u8), timeout_min (u8)]
        return if (ack?.status == AckStatus.OK && ack.optionalData.size >= 2) {
            val enabled = ack.optionalData[0].toInt() and 0xFF
            val minutes = ack.optionalData[1].toInt() and 0xFF
            Log.d(TAG, "Get lazy poll: enabled=$enabled, timeout=$minutes minutes")
            // Return 0 if disabled, otherwise return the timeout
            val effectiveMinutes = if (enabled == 1) minutes else 0
            Result.success(effectiveMinutes)
        } else if (ack?.status == AckStatus.OK && ack.optionalData.size == 1) {
            // Legacy firmware format (single byte = timeout minutes, 0=disabled)
            val minutes = ack.optionalData[0].toInt() and 0xFF
            Log.d(TAG, "Get lazy poll (legacy format): timeout=$minutes minutes")
            Result.success(minutes)
        } else {
            val detail = "Get lazy poll failed: ${ack?.status}"
            Log.e(TAG, detail)
            Result.failure(RuntimeException(detail))
        }
    }

    // ==========================================
    // Safety Gate Commands (v0.4.0+)
    // ==========================================

    override suspend fun getCapabilities(): Result<Map<Int, Int>> {
        Log.d(TAG, ">>> GET_CAPABILITIES (0x0070)")

        val ack = bleManager.sendCommand(
            cmdId = CommandId.GET_CAPABILITIES,
            payload = byteArrayOf()
        )

        return if (ack?.status == AckStatus.OK && ack.optionalData.size >= 7) {
            // Response: 7 bytes, one per subsystem (PID1, PID2, PID3, ESTOP, DOOR, LN2, MOTOR)
            val capabilities = mutableMapOf<Int, Int>()
            for (i in 0 until minOf(7, ack.optionalData.size)) {
                capabilities[i] = ack.optionalData[i].toInt() and 0xFF
            }
            Log.d(TAG, "Get capabilities: $capabilities")
            Result.success(capabilities)
        } else {
            val detail = "Get capabilities failed: ${ack?.status}"
            Log.e(TAG, detail)
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun setCapability(subsystemId: Int, level: Int): Result<Unit> {
        Log.d(TAG, ">>> SET_CAPABILITY (0x0071): subsystem=$subsystemId, level=$level")

        // E-Stop capability cannot be changed
        if (subsystemId == SubsystemId.ESTOP.toInt()) {
            return Result.failure(IllegalArgumentException("E-Stop capability cannot be changed"))
        }

        val payload = byteArrayOf(
            subsystemId.toByte(),
            level.toByte()
        )

        val ack = bleManager.sendCommand(
            cmdId = CommandId.SET_CAPABILITY,
            payload = payload
        )

        return if (ack?.status == AckStatus.OK) {
            Log.d(TAG, "Set capability success")
            Result.success(Unit)
        } else {
            val detail = "Set capability failed: ${ack?.status}"
            Log.e(TAG, detail)
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun getSafetyGates(): Result<Pair<Int, Int>> {
        Log.d(TAG, ">>> GET_SAFETY_GATES (0x0072)")

        val ack = bleManager.sendCommand(
            cmdId = CommandId.GET_SAFETY_GATES,
            payload = byteArrayOf()
        )

        return if (ack?.status == AckStatus.OK && ack.optionalData.size >= 4) {
            // Response: 4 bytes = gate_enable (u16 LE), gate_status (u16 LE)
            val enableMask = (ack.optionalData[0].toInt() and 0xFF) or
                    ((ack.optionalData[1].toInt() and 0xFF) shl 8)
            val statusMask = (ack.optionalData[2].toInt() and 0xFF) or
                    ((ack.optionalData[3].toInt() and 0xFF) shl 8)

            Log.d(TAG, "Get safety gates: enable=0x${enableMask.toString(16)}, status=0x${statusMask.toString(16)}")
            Result.success(Pair(enableMask, statusMask))
        } else {
            val detail = "Get safety gates failed: ${ack?.status}"
            Log.e(TAG, detail)
            Result.failure(RuntimeException(detail))
        }
    }

    override suspend fun setSafetyGate(gateId: Int, enabled: Boolean): Result<Unit> {
        Log.d(TAG, ">>> SET_SAFETY_GATE (0x0073): gate=$gateId, enabled=$enabled")

        // E-Stop gate cannot be bypassed
        if (gateId == SafetyGateId.ESTOP.toInt() && !enabled) {
            return Result.failure(IllegalArgumentException("E-Stop gate cannot be bypassed"))
        }

        val payload = byteArrayOf(
            gateId.toByte(),
            if (enabled) 1 else 0
        )

        val ack = bleManager.sendCommand(
            cmdId = CommandId.SET_SAFETY_GATE,
            payload = payload
        )

        return if (ack?.status == AckStatus.OK) {
            Log.d(TAG, "Set safety gate success")
            Result.success(Unit)
        } else {
            val detail = "Set safety gate failed: ${ack?.status}"
            Log.e(TAG, detail)
            Result.failure(RuntimeException(detail))
        }
    }
}
