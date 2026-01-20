package com.shakercontrol.app.data.ble

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for TelemetryParser, CommandAckParser, and EventParser.
 */
class ParserTest {

    // ==================== TelemetryParser Tests ====================

    @Test
    fun `telemetry parse valid snapshot with no controllers`() {
        // Build minimal telemetry: timestamp(4) + di(2) + ro(2) + alarm(4) + count(1) = 13 bytes
        val payload = ByteBuffer.allocate(13)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(12345)      // timestamp_ms
            .putShort(0x00FF.toShort())   // di_bits
            .putShort(0x0F0F.toShort())   // ro_bits
            .putInt(0x00000001) // alarm_bits
            .put(0)             // controller_count
            .array()

        val snapshot = TelemetryParser.parse(payload)

        assertNotNull(snapshot)
        assertEquals(12345L, snapshot!!.timestampMs)
        assertEquals(0x00FF, snapshot.diBits)
        assertEquals(0x0F0F, snapshot.roBits)
        assertEquals(1L, snapshot.alarmBits)
        assertEquals(0, snapshot.controllers.size)
    }

    @Test
    fun `telemetry parse snapshot with one controller`() {
        // 13 bytes base + 10 bytes per controller = 23 bytes
        val payload = ByteBuffer.allocate(23)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(1000)       // timestamp_ms
            .putShort(0)        // di_bits
            .putShort(0)        // ro_bits
            .putInt(0)          // alarm_bits
            .put(1)             // controller_count = 1
            // Controller 1:
            .put(1)             // controller_id
            .putShort(300)      // pv_x10 = 30.0°C
            .putShort(350)      // sv_x10 = 35.0°C
            .putShort(500)      // op_x10 = 50.0%
            .put(ControllerMode.AUTO.code)  // mode
            .putShort(100)      // age_ms
            .array()

        val snapshot = TelemetryParser.parse(payload)

        assertNotNull(snapshot)
        assertEquals(1, snapshot!!.controllers.size)

        val ctrl = snapshot.controllers[0]
        assertEquals(1, ctrl.controllerId)
        assertEquals(30.0f, ctrl.pv, 0.01f)
        assertEquals(35.0f, ctrl.sv, 0.01f)
        assertEquals(50.0f, ctrl.opPercent, 0.01f)
        assertEquals(ControllerMode.AUTO, ctrl.mode)
        assertEquals(100, ctrl.ageMs)
    }

    @Test
    fun `telemetry parse snapshot with three controllers`() {
        // 13 bytes base + 3 * 10 bytes = 43 bytes
        val payload = ByteBuffer.allocate(43)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(5000)
            .putShort(0xFF.toShort())
            .putShort(0xAA.toShort())
            .putInt(0)
            .put(3)             // controller_count = 3
            // Controller 1:
            .put(1).putShort(200).putShort(250).putShort(100).put(ControllerMode.AUTO.code).putShort(50)
            // Controller 2:
            .put(2).putShort(250).putShort(300).putShort(200).put(ControllerMode.MANUAL.code).putShort(75)
            // Controller 3 (LN2):
            .put(3).putShort((-1960).toShort()).putShort((-1950).toShort()).putShort(1000).put(ControllerMode.STOP.code).putShort(200)
            .array()

        val snapshot = TelemetryParser.parse(payload)

        assertNotNull(snapshot)
        assertEquals(3, snapshot!!.controllers.size)

        // Check controller 3 with negative temperature
        val ctrl3 = snapshot.controllers[2]
        assertEquals(3, ctrl3.controllerId)
        assertEquals(-196.0f, ctrl3.pv, 0.01f)  // LN2 temperature
        assertEquals(-195.0f, ctrl3.sv, 0.01f)
        assertEquals(ControllerMode.STOP, ctrl3.mode)
    }

    @Test
    fun `telemetry parse returns null for too short data`() {
        val tooShort = ByteArray(12)  // Need at least 13

        val snapshot = TelemetryParser.parse(tooShort)

        assertNull(snapshot)
    }

    @Test
    fun `telemetry parse returns null for truncated controller data`() {
        // Claim 1 controller but don't provide the full 10 bytes
        val payload = ByteBuffer.allocate(18)  // 13 base + only 5 bytes for controller
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(1000)
            .putShort(0)
            .putShort(0)
            .putInt(0)
            .put(1)             // Says 1 controller
            .put(1)             // Only partial controller data
            .putShort(100)
            .array()

        val snapshot = TelemetryParser.parse(payload)

        assertNull(snapshot)
    }

    @Test
    fun `telemetry parse max timestamp`() {
        val payload = ByteBuffer.allocate(13)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(-1)         // 0xFFFFFFFF as unsigned
            .putShort(0)
            .putShort(0)
            .putInt(0)
            .put(0)
            .array()

        val snapshot = TelemetryParser.parse(payload)

        assertNotNull(snapshot)
        assertEquals(0xFFFFFFFFL, snapshot!!.timestampMs)
    }

    // ==================== CommandAckParser Tests ====================

    @Test
    fun `commandAck parse valid ack`() {
        val payload = ByteBuffer.allocate(7)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(100)      // acked_seq
            .putShort(CommandId.OPEN_SESSION)  // cmd_id
            .put(AckStatus.OK.code)  // status
            .putShort(0)        // detail
            .array()

        val ack = CommandAckParser.parse(payload)

        assertNotNull(ack)
        assertEquals(100.toShort(), ack!!.ackedSeq)
        assertEquals(CommandId.OPEN_SESSION, ack.cmdId)
        assertEquals(AckStatus.OK, ack.status)
        assertEquals(0.toShort(), ack.detail)
        assertEquals(0, ack.optionalData.size)
    }

    @Test
    fun `commandAck parse with optional data`() {
        // OPEN_SESSION ACK includes session_id(4) + lease_ms(2)
        val payload = ByteBuffer.allocate(13)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(50)       // acked_seq
            .putShort(CommandId.OPEN_SESSION)
            .put(AckStatus.OK.code)
            .putShort(0)        // detail
            // Optional data:
            .putInt(0x12345678) // session_id
            .putShort(3000)     // lease_ms
            .array()

        val ack = CommandAckParser.parse(payload)

        assertNotNull(ack)
        assertEquals(6, ack!!.optionalData.size)

        val sessionData = CommandAckParser.parseOpenSessionAckData(ack.optionalData)
        assertNotNull(sessionData)
        assertEquals(0x12345678, sessionData!!.sessionId)
        assertEquals(3000, sessionData.leaseMs)
    }

    @Test
    fun `commandAck parse rejected status`() {
        val payload = ByteBuffer.allocate(7)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(200)
            .putShort(CommandId.START_RUN)
            .put(AckStatus.REJECTED_POLICY.code)
            .putShort(AckDetail.SESSION_INVALID)
            .array()

        val ack = CommandAckParser.parse(payload)

        assertNotNull(ack)
        assertEquals(AckStatus.REJECTED_POLICY, ack!!.status)
        assertEquals(AckDetail.SESSION_INVALID, ack.detail)
    }

    @Test
    fun `commandAck parse all status codes`() {
        for (status in AckStatus.entries) {
            val payload = ByteBuffer.allocate(7)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(1)
                .putShort(CommandId.KEEPALIVE)
                .put(status.code)
                .putShort(0)
                .array()

            val ack = CommandAckParser.parse(payload)

            assertNotNull(ack)
            assertEquals(status, ack!!.status)
        }
    }

    @Test
    fun `commandAck parse returns null for too short data`() {
        val tooShort = ByteArray(6)  // Need at least 7

        val ack = CommandAckParser.parse(tooShort)

        assertNull(ack)
    }

    @Test
    fun `openSessionAckData parse returns null for too short data`() {
        val tooShort = ByteArray(5)  // Need at least 6

        val data = CommandAckParser.parseOpenSessionAckData(tooShort)

        assertNull(data)
    }

    @Test
    fun `commandAck equality`() {
        val ack1 = CommandAckParser.CommandAck(
            ackedSeq = 1,
            cmdId = 0x0100,
            status = AckStatus.OK,
            detail = 0,
            optionalData = byteArrayOf(0x01, 0x02)
        )
        val ack2 = CommandAckParser.CommandAck(
            ackedSeq = 1,
            cmdId = 0x0100,
            status = AckStatus.OK,
            detail = 0,
            optionalData = byteArrayOf(0x01, 0x02)
        )

        assertEquals(ack1, ack2)
        assertEquals(ack1.hashCode(), ack2.hashCode())
    }

    // ==================== EventParser Tests ====================

    @Test
    fun `event parse valid event`() {
        val payload = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(EventId.ESTOP_ASSERTED)
            .put(EventSeverity.CRITICAL.code)
            .put(0)             // source
            .array()

        val event = EventParser.parse(payload)

        assertNotNull(event)
        assertEquals(EventId.ESTOP_ASSERTED, event!!.eventId)
        assertEquals(EventSeverity.CRITICAL, event.severity)
        assertEquals(0, event.source)
        assertEquals(0, event.data.size)
    }

    @Test
    fun `event parse with extra data`() {
        val payload = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(EventId.ALARM_LATCHED)
            .put(EventSeverity.ALARM.code)
            .put(3)             // source = controller 3
            .putInt(0x00000004) // data: alarm bit 2 (OVER_TEMP)
            .array()

        val event = EventParser.parse(payload)

        assertNotNull(event)
        assertEquals(EventId.ALARM_LATCHED, event!!.eventId)
        assertEquals(EventSeverity.ALARM, event.severity)
        assertEquals(3, event.source)
        assertEquals(4, event.data.size)
    }

    @Test
    fun `event parse all severity levels`() {
        for (severity in EventSeverity.entries) {
            val payload = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(EventId.HMI_CONNECTED)
                .put(severity.code)
                .put(0)
                .array()

            val event = EventParser.parse(payload)

            assertNotNull(event)
            assertEquals(severity, event!!.severity)
        }
    }

    @Test
    fun `event parse returns null for too short data`() {
        val tooShort = ByteArray(3)  // Need at least 4

        val event = EventParser.parse(tooShort)

        assertNull(event)
    }

    @Test
    fun `event equality`() {
        val event1 = EventParser.Event(
            eventId = EventId.RUN_STARTED,
            severity = EventSeverity.INFO,
            source = 0,
            data = byteArrayOf(0x01)
        )
        val event2 = EventParser.Event(
            eventId = EventId.RUN_STARTED,
            severity = EventSeverity.INFO,
            source = 0,
            data = byteArrayOf(0x01)
        )

        assertEquals(event1, event2)
        assertEquals(event1.hashCode(), event2.hashCode())
    }

    @Test
    fun `event inequality different data`() {
        val event1 = EventParser.Event(
            eventId = EventId.RUN_STARTED,
            severity = EventSeverity.INFO,
            source = 0,
            data = byteArrayOf(0x01)
        )
        val event2 = EventParser.Event(
            eventId = EventId.RUN_STARTED,
            severity = EventSeverity.INFO,
            source = 0,
            data = byteArrayOf(0x02)  // Different!
        )

        assertNotEquals(event1, event2)
    }

    // ==================== CommandPayloadBuilder Tests ====================

    @Test
    fun `commandPayload openSession`() {
        val nonce = 0x12345678
        val payload = CommandPayloadBuilder.openSession(nonce)

        assertEquals(4, payload.size)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(nonce, buffer.int)
    }

    @Test
    fun `commandPayload keepalive`() {
        val sessionId = 0xABCDEF01.toInt()
        val payload = CommandPayloadBuilder.keepalive(sessionId)

        assertEquals(4, payload.size)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(sessionId, buffer.int)
    }

    @Test
    fun `commandPayload startRun`() {
        val sessionId = 12345
        val payload = CommandPayloadBuilder.startRun(sessionId, RunMode.DRY_RUN)

        assertEquals(5, payload.size)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(sessionId, buffer.int)
        assertEquals(RunMode.DRY_RUN.code, buffer.get())
    }

    @Test
    fun `commandPayload stopRun`() {
        val sessionId = 67890
        val payload = CommandPayloadBuilder.stopRun(sessionId, StopMode.ABORT)

        assertEquals(5, payload.size)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(sessionId, buffer.int)
        assertEquals(StopMode.ABORT.code, buffer.get())
    }

    @Test
    fun `commandPayload setRelay`() {
        val payload = CommandPayloadBuilder.setRelay(3, RelayState.ON)

        assertEquals(2, payload.size)
        assertEquals(3.toByte(), payload[0])
        assertEquals(RelayState.ON.code, payload[1])
    }

    @Test
    fun `commandPayload setSv`() {
        val payload = CommandPayloadBuilder.setSv(2, 350)  // 35.0°C

        assertEquals(3, payload.size)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2.toByte(), buffer.get())
        assertEquals(350.toShort(), buffer.short)
    }

    @Test
    fun `commandPayload setMode`() {
        val payload = CommandPayloadBuilder.setMode(1, ControllerMode.MANUAL)

        assertEquals(2, payload.size)
        assertEquals(1.toByte(), payload[0])
        assertEquals(ControllerMode.MANUAL.code, payload[1])
    }

    @Test
    fun `commandPayload pauseRun`() {
        val sessionId = 99999
        val payload = CommandPayloadBuilder.pauseRun(sessionId)

        assertEquals(4, payload.size)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(sessionId, buffer.int)
    }

    @Test
    fun `commandPayload resumeRun is startRun with NORMAL mode`() {
        val sessionId = 11111
        val payload = CommandPayloadBuilder.resumeRun(sessionId)

        assertEquals(5, payload.size)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(sessionId, buffer.int)
        assertEquals(RunMode.NORMAL.code, buffer.get())
    }
}
