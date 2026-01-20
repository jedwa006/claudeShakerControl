package com.shakercontrol.app.data.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire protocol frame encoder/decoder.
 *
 * Frame layout:
 * | proto_ver | msg_type | seq | payload_len | payload | crc16 |
 * |    1B     |    1B    | 2B  |     2B      |   N B   |  2B   |
 *
 * All multi-byte integers are little-endian.
 * CRC-16/CCITT-FALSE computed over header + payload.
 */
object WireProtocol {
    private const val HEADER_SIZE = 6 // proto_ver(1) + msg_type(1) + seq(2) + payload_len(2)
    private const val CRC_SIZE = 2
    private const val MIN_FRAME_SIZE = HEADER_SIZE + CRC_SIZE

    /**
     * Encode a command frame.
     */
    fun encodeCommand(
        seq: Short,
        cmdId: Short,
        flags: Short = 0,
        cmdPayload: ByteArray = ByteArray(0)
    ): ByteArray {
        // Command payload: cmd_id(2) + flags(2) + cmd_payload(N)
        val payload = ByteBuffer.allocate(4 + cmdPayload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(cmdId)
            .putShort(flags)
            .put(cmdPayload)
            .array()

        return encodeFrame(MessageType.COMMAND, seq, payload)
    }

    /**
     * Encode a generic frame with given message type and payload.
     */
    fun encodeFrame(
        msgType: Byte,
        seq: Short,
        payload: ByteArray
    ): ByteArray {
        val frame = ByteBuffer.allocate(HEADER_SIZE + payload.size + CRC_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(BleConstants.PROTOCOL_VERSION)
            .put(msgType)
            .putShort(seq)
            .putShort(payload.size.toShort())
            .put(payload)

        // Compute CRC over header + payload (excluding CRC field)
        val crcData = frame.array().copyOfRange(0, HEADER_SIZE + payload.size)
        val crc = Crc16CcittFalse.compute(crcData)
        frame.putShort(crc)

        return frame.array()
    }

    /**
     * Decode a received frame.
     * Returns null if frame is invalid (too short, bad CRC, etc.).
     */
    fun decodeFrame(data: ByteArray): Frame? {
        if (data.size < MIN_FRAME_SIZE) {
            return null
        }

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        val protoVer = buffer.get()
        val msgType = buffer.get()
        val seq = buffer.short
        val payloadLen = buffer.short.toInt() and 0xFFFF

        if (data.size < HEADER_SIZE + payloadLen + CRC_SIZE) {
            return null
        }

        val payload = ByteArray(payloadLen)
        buffer.get(payload)

        val receivedCrc = buffer.short
        val computedCrc = Crc16CcittFalse.compute(data.copyOfRange(0, HEADER_SIZE + payloadLen))

        if (receivedCrc != computedCrc) {
            return null
        }

        return Frame(
            protoVer = protoVer,
            msgType = msgType,
            seq = seq,
            payload = payload
        )
    }

    /**
     * Decoded frame structure.
     */
    data class Frame(
        val protoVer: Byte,
        val msgType: Byte,
        val seq: Short,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return protoVer == other.protoVer &&
                    msgType == other.msgType &&
                    seq == other.seq &&
                    payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = protoVer.toInt()
            result = 31 * result + msgType
            result = 31 * result + seq
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }
}

/**
 * CRC-16/CCITT-FALSE implementation.
 * - Poly: 0x1021
 * - Init: 0xFFFF
 * - RefIn/RefOut: false
 * - XorOut: 0x0000
 */
object Crc16CcittFalse {
    private const val POLYNOMIAL = 0x1021
    private const val INITIAL = 0xFFFF

    // Pre-computed lookup table for performance
    private val table = IntArray(256) { i ->
        var crc = i shl 8
        repeat(8) {
            crc = if ((crc and 0x8000) != 0) {
                (crc shl 1) xor POLYNOMIAL
            } else {
                crc shl 1
            }
        }
        crc and 0xFFFF
    }

    fun compute(data: ByteArray): Short {
        var crc = INITIAL
        for (byte in data) {
            val index = ((crc shr 8) xor (byte.toInt() and 0xFF)) and 0xFF
            crc = ((crc shl 8) xor table[index]) and 0xFFFF
        }
        return crc.toShort()
    }
}

/**
 * Helper to build command payloads.
 */
object CommandPayloadBuilder {
    /**
     * OPEN_SESSION payload: client_nonce(u32)
     */
    fun openSession(clientNonce: Int): ByteArray =
        ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(clientNonce)
            .array()

    /**
     * KEEPALIVE payload: session_id(u32)
     */
    fun keepalive(sessionId: Int): ByteArray =
        ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(sessionId)
            .array()

    /**
     * START_RUN payload: session_id(u32), run_mode(u8)
     */
    fun startRun(sessionId: Int, runMode: RunMode): ByteArray =
        ByteBuffer.allocate(5)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(sessionId)
            .put(runMode.code)
            .array()

    /**
     * STOP_RUN payload: session_id(u32), stop_mode(u8)
     */
    fun stopRun(sessionId: Int, stopMode: StopMode): ByteArray =
        ByteBuffer.allocate(5)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(sessionId)
            .put(stopMode.code)
            .array()

    /**
     * SET_RELAY payload: relay_index(u8 1..8), state(u8)
     */
    fun setRelay(relayIndex: Int, state: RelayState): ByteArray =
        byteArrayOf(relayIndex.toByte(), state.code)

    /**
     * SET_RELAY_MASK payload: mask(u8), values(u8)
     * Atomically sets multiple relays in a single operation.
     * - mask: bitmask of which channels to affect (bit 0 = RO1, ..., bit 7 = RO8)
     * - values: target state for each channel (0=OFF, 1=ON for each bit)
     * Only channels with their mask bit set will be modified.
     */
    fun setRelayMask(mask: Int, values: Int): ByteArray =
        byteArrayOf((mask and 0xFF).toByte(), (values and 0xFF).toByte())

    /**
     * SET_SV payload: controller_id(u8 1..3), sv_x10(i16)
     */
    fun setSv(controllerId: Int, svX10: Short): ByteArray =
        ByteBuffer.allocate(3)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(controllerId.toByte())
            .putShort(svX10)
            .array()

    /**
     * SET_MODE payload: controller_id(u8 1..3), mode(u8)
     */
    fun setMode(controllerId: Int, mode: ControllerMode): ByteArray =
        byteArrayOf(controllerId.toByte(), mode.code)

    /**
     * REQUEST_PV_SV_REFRESH payload: controller_id(u8 1..3)
     * Forces an immediate Modbus poll of the controller.
     */
    fun requestPvSvRefresh(controllerId: Int): ByteArray =
        byteArrayOf(controllerId.toByte())

    /**
     * SET_PID_PARAMS payload: controller_id(u8), p_gain_x10(i16), i_time(u16), d_time(u16)
     */
    fun setPidParams(controllerId: Int, pGainX10: Short, iTime: Int, dTime: Int): ByteArray =
        ByteBuffer.allocate(7)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(controllerId.toByte())
            .putShort(pGainX10)
            .putShort(iTime.toShort())
            .putShort(dTime.toShort())
            .array()

    /**
     * READ_PID_PARAMS payload: controller_id(u8)
     */
    fun readPidParams(controllerId: Int): ByteArray =
        byteArrayOf(controllerId.toByte())

    /**
     * START_AUTOTUNE payload: controller_id(u8)
     */
    fun startAutotune(controllerId: Int): ByteArray =
        byteArrayOf(controllerId.toByte())

    /**
     * STOP_AUTOTUNE payload: controller_id(u8)
     */
    fun stopAutotune(controllerId: Int): ByteArray =
        byteArrayOf(controllerId.toByte())

    /**
     * SET_ALARM_LIMITS payload: controller_id(u8), alarm1_x10(i16), alarm2_x10(i16)
     */
    fun setAlarmLimits(controllerId: Int, alarm1X10: Short, alarm2X10: Short): ByteArray =
        ByteBuffer.allocate(5)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(controllerId.toByte())
            .putShort(alarm1X10)
            .putShort(alarm2X10)
            .array()

    /**
     * READ_ALARM_LIMITS payload: controller_id(u8)
     */
    fun readAlarmLimits(controllerId: Int): ByteArray =
        byteArrayOf(controllerId.toByte())

    /**
     * PAUSE_RUN payload: session_id(u32)
     */
    fun pauseRun(sessionId: Int): ByteArray =
        ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(sessionId)
            .array()

    /**
     * RESUME_RUN payload: session_id(u32)
     * Uses START_RUN command with NORMAL mode to resume from pause
     */
    fun resumeRun(sessionId: Int): ByteArray =
        startRun(sessionId, RunMode.NORMAL)
}

/**
 * Helper to parse telemetry snapshots.
 */
object TelemetryParser {
    /**
     * Parse TELEMETRY_SNAPSHOT payload.
     *
     * Telemetry structure:
     * - wire_telemetry_header_t (13 bytes): timestamp, di, ro, alarm, controller_count
     * - controller_data (N × 10 bytes each)
     * - wire_telemetry_run_state_t (16 bytes): machine_state, timing, lazy_poll, etc.
     */
    fun parse(payload: ByteArray): TelemetrySnapshot? {
        if (payload.size < 13) return null // Minimum: header only

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        val timestampMs = buffer.int.toLong() and 0xFFFFFFFFL
        val diBits = buffer.short.toInt() and 0xFFFF
        val roBits = buffer.short.toInt() and 0xFFFF
        val alarmBits = buffer.int.toLong() and 0xFFFFFFFFL
        val controllerCount = buffer.get().toInt() and 0xFF

        val controllers = mutableListOf<ControllerData>()
        repeat(controllerCount) {
            if (buffer.remaining() < 10) return null
            controllers.add(
                ControllerData(
                    controllerId = buffer.get().toInt() and 0xFF,
                    pvX10 = buffer.short,
                    svX10 = buffer.short,
                    opX10 = buffer.short.toInt() and 0xFFFF,
                    mode = ControllerMode.fromCode(buffer.get()),
                    ageMs = buffer.short.toInt() and 0xFFFF
                )
            )
        }

        // Parse extended run state if present (16 bytes)
        var runState: RunStateData? = null
        if (buffer.remaining() >= 16) {
            val machineState = buffer.get().toInt() and 0xFF
            val runElapsedMs = buffer.int.toLong() and 0xFFFFFFFFL
            val runRemainingMs = buffer.int.toLong() and 0xFFFFFFFFL
            val targetTempX10 = buffer.short
            val recipeStep = buffer.get().toInt() and 0xFF
            val interlockBits = buffer.get().toInt() and 0xFF
            val lazyPollActive = buffer.get().toInt() and 0xFF
            val idleTimeoutMin = buffer.get().toInt() and 0xFF

            runState = RunStateData(
                machineState = machineState,
                runElapsedMs = runElapsedMs,
                runRemainingMs = runRemainingMs,
                targetTempX10 = targetTempX10,
                recipeStep = recipeStep,
                interlockBits = interlockBits,
                lazyPollActive = lazyPollActive != 0,
                idleTimeoutMin = idleTimeoutMin
            )
        }

        return TelemetrySnapshot(
            timestampMs = timestampMs,
            diBits = diBits,
            roBits = roBits,
            alarmBits = alarmBits,
            controllers = controllers,
            runState = runState
        )
    }

    data class TelemetrySnapshot(
        val timestampMs: Long,
        val diBits: Int,
        val roBits: Int,
        val alarmBits: Long,
        val controllers: List<ControllerData>,
        val runState: RunStateData? = null
    )

    /**
     * Extended run state data from wire_telemetry_run_state_t (16 bytes)
     */
    data class RunStateData(
        val machineState: Int,
        val runElapsedMs: Long,
        val runRemainingMs: Long,
        val targetTempX10: Short,
        val recipeStep: Int,
        val interlockBits: Int,
        val lazyPollActive: Boolean,
        val idleTimeoutMin: Int
    ) {
        val targetTemp: Float get() = targetTempX10 / 10f
    }

    data class ControllerData(
        val controllerId: Int,
        val pvX10: Short,
        val svX10: Short,
        val opX10: Int,
        val mode: ControllerMode,
        val ageMs: Int
    ) {
        val pv: Float get() = pvX10 / 10f
        val sv: Float get() = svX10 / 10f
        val opPercent: Float get() = opX10 / 10f
    }
}

/**
 * Helper to parse command ACKs.
 */
object CommandAckParser {
    /**
     * Parse COMMAND_ACK payload.
     */
    fun parse(payload: ByteArray): CommandAck? {
        if (payload.size < 7) return null

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        val ackedSeq = buffer.short
        val cmdId = buffer.short
        val status = AckStatus.fromCode(buffer.get())
        val detail = buffer.short

        // Optional data depends on command
        val optionalData = if (buffer.hasRemaining()) {
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } else {
            ByteArray(0)
        }

        return CommandAck(
            ackedSeq = ackedSeq,
            cmdId = cmdId,
            status = status,
            detail = detail,
            optionalData = optionalData
        )
    }

    /**
     * Parse OPEN_SESSION ACK optional data.
     */
    fun parseOpenSessionAckData(optionalData: ByteArray): OpenSessionAckData? {
        if (optionalData.size < 6) return null
        val buffer = ByteBuffer.wrap(optionalData).order(ByteOrder.LITTLE_ENDIAN)
        return OpenSessionAckData(
            sessionId = buffer.int,
            leaseMs = buffer.short.toInt() and 0xFFFF
        )
    }

    data class CommandAck(
        val ackedSeq: Short,
        val cmdId: Short,
        val status: AckStatus,
        val detail: Short,
        val optionalData: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CommandAck) return false
            return ackedSeq == other.ackedSeq &&
                    cmdId == other.cmdId &&
                    status == other.status &&
                    detail == other.detail &&
                    optionalData.contentEquals(other.optionalData)
        }

        override fun hashCode(): Int {
            var result = ackedSeq.toInt()
            result = 31 * result + cmdId
            result = 31 * result + status.hashCode()
            result = 31 * result + detail
            result = 31 * result + optionalData.contentHashCode()
            return result
        }
    }

    data class OpenSessionAckData(
        val sessionId: Int,
        val leaseMs: Int
    )
}

/**
 * Helper to parse events.
 */
object EventParser {
    /**
     * Parse EVENT payload.
     */
    fun parse(payload: ByteArray): Event? {
        if (payload.size < 4) return null

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        val eventId = buffer.short
        val severity = EventSeverity.fromCode(buffer.get())
        val source = buffer.get().toInt() and 0xFF

        val data = if (buffer.hasRemaining()) {
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } else {
            ByteArray(0)
        }

        return Event(
            eventId = eventId,
            severity = severity,
            source = source,
            data = data
        )
    }

    data class Event(
        val eventId: Short,
        val severity: EventSeverity,
        val source: Int,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Event) return false
            return eventId == other.eventId &&
                    severity == other.severity &&
                    source == other.source &&
                    data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = eventId.toInt()
            result = 31 * result + severity.hashCode()
            result = 31 * result + source
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}
