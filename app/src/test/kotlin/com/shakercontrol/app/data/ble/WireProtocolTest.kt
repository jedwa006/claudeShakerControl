package com.shakercontrol.app.data.ble

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for WireProtocol frame encoding/decoding and CRC-16.
 */
class WireProtocolTest {

    // ==================== CRC-16 Tests ====================

    @Test
    fun `crc16 empty data returns initial value XOR processed`() {
        val crc = Crc16CcittFalse.compute(byteArrayOf())
        // CRC-16/CCITT-FALSE of empty data is 0xFFFF
        assertEquals(0xFFFF.toShort(), crc)
    }

    @Test
    fun `crc16 known test vector - ASCII 123456789`() {
        // Standard test vector: "123456789" -> CRC = 0x29B1
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        val crc = Crc16CcittFalse.compute(data)
        assertEquals(0x29B1.toShort(), crc)
    }

    @Test
    fun `crc16 single byte`() {
        val crc = Crc16CcittFalse.compute(byteArrayOf(0x00))
        // Pre-computed CRC of single 0x00 byte
        assertEquals(0xE1F0.toShort(), crc)
    }

    @Test
    fun `crc16 all zeros`() {
        val data = ByteArray(10) { 0x00 }
        val crc = Crc16CcittFalse.compute(data)
        // Just verify it computes without error and returns a value
        assertNotNull(crc)
    }

    @Test
    fun `crc16 all ones`() {
        val data = ByteArray(10) { 0xFF.toByte() }
        val crc = Crc16CcittFalse.compute(data)
        assertNotNull(crc)
    }

    // ==================== Frame Encoding Tests ====================

    @Test
    fun `encodeFrame creates valid frame structure`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val frame = WireProtocol.encodeFrame(
            msgType = MessageType.TELEMETRY_SNAPSHOT,
            seq = 0x1234,
            payload = payload
        )

        // Frame should be: header(6) + payload(3) + crc(2) = 11 bytes
        assertEquals(11, frame.size)

        // Check header fields
        assertEquals(BleConstants.PROTOCOL_VERSION, frame[0])
        assertEquals(MessageType.TELEMETRY_SNAPSHOT, frame[1])

        // Seq (little-endian)
        val seq = ByteBuffer.wrap(frame, 2, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(0x1234.toShort(), seq)

        // Payload length (little-endian)
        val payloadLen = ByteBuffer.wrap(frame, 4, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(3.toShort(), payloadLen)

        // Payload content
        assertEquals(0x01.toByte(), frame[6])
        assertEquals(0x02.toByte(), frame[7])
        assertEquals(0x03.toByte(), frame[8])
    }

    @Test
    fun `encodeFrame with empty payload`() {
        val frame = WireProtocol.encodeFrame(
            msgType = MessageType.COMMAND,
            seq = 1,
            payload = byteArrayOf()
        )

        // Frame should be: header(6) + payload(0) + crc(2) = 8 bytes
        assertEquals(8, frame.size)

        val payloadLen = ByteBuffer.wrap(frame, 4, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(0.toShort(), payloadLen)
    }

    @Test
    fun `encodeCommand creates valid command frame`() {
        val cmdPayload = byteArrayOf(0xAA.toByte())
        val frame = WireProtocol.encodeCommand(
            seq = 100,
            cmdId = CommandId.OPEN_SESSION,
            flags = 0,
            cmdPayload = cmdPayload
        )

        // Frame should be: header(6) + cmd_id(2) + flags(2) + cmdPayload(1) + crc(2) = 13 bytes
        assertEquals(13, frame.size)

        // Decode and verify
        val decoded = WireProtocol.decodeFrame(frame)
        assertNotNull(decoded)
        assertEquals(MessageType.COMMAND, decoded!!.msgType)
        assertEquals(100.toShort(), decoded.seq)

        // Check command payload structure
        val payloadBuffer = ByteBuffer.wrap(decoded.payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(CommandId.OPEN_SESSION, payloadBuffer.short)
        assertEquals(0.toShort(), payloadBuffer.short) // flags
        assertEquals(0xAA.toByte(), payloadBuffer.get())
    }

    // ==================== Frame Decoding Tests ====================

    @Test
    fun `decodeFrame valid frame round-trip`() {
        val originalPayload = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val encoded = WireProtocol.encodeFrame(
            msgType = MessageType.EVENT,
            seq = 0x5678,
            payload = originalPayload
        )

        val decoded = WireProtocol.decodeFrame(encoded)

        assertNotNull(decoded)
        assertEquals(BleConstants.PROTOCOL_VERSION, decoded!!.protoVer)
        assertEquals(MessageType.EVENT, decoded.msgType)
        assertEquals(0x5678.toShort(), decoded.seq)
        assertArrayEquals(originalPayload, decoded.payload)
    }

    @Test
    fun `decodeFrame rejects too short data`() {
        // Minimum frame is 8 bytes (header + crc)
        val tooShort = byteArrayOf(0x01, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00)

        val decoded = WireProtocol.decodeFrame(tooShort)

        assertNull(decoded)
    }

    @Test
    fun `decodeFrame rejects bad CRC`() {
        val payload = byteArrayOf(0x01, 0x02)
        val validFrame = WireProtocol.encodeFrame(
            msgType = MessageType.TELEMETRY_SNAPSHOT,
            seq = 1,
            payload = payload
        )

        // Corrupt the CRC (last 2 bytes)
        validFrame[validFrame.size - 1] = (validFrame[validFrame.size - 1] + 1).toByte()

        val decoded = WireProtocol.decodeFrame(validFrame)

        assertNull(decoded)
    }

    @Test
    fun `decodeFrame rejects truncated payload`() {
        // Create a frame header claiming 10 bytes payload but only providing 5
        val badFrame = byteArrayOf(
            0x01,                   // proto_ver
            0x01,                   // msg_type
            0x00, 0x00,             // seq
            0x0A, 0x00,             // payload_len = 10
            0x01, 0x02, 0x03, 0x04, 0x05,  // only 5 bytes
            0x00, 0x00              // CRC (invalid anyway)
        )

        val decoded = WireProtocol.decodeFrame(badFrame)

        assertNull(decoded)
    }

    @Test
    fun `decodeFrame with zero length payload`() {
        val frame = WireProtocol.encodeFrame(
            msgType = MessageType.COMMAND_ACK,
            seq = 0,
            payload = byteArrayOf()
        )

        val decoded = WireProtocol.decodeFrame(frame)

        assertNotNull(decoded)
        assertEquals(0, decoded!!.payload.size)
    }

    @Test
    fun `decodeFrame with max seq number`() {
        val frame = WireProtocol.encodeFrame(
            msgType = MessageType.TELEMETRY_SNAPSHOT,
            seq = Short.MAX_VALUE,
            payload = byteArrayOf(0x01)
        )

        val decoded = WireProtocol.decodeFrame(frame)

        assertNotNull(decoded)
        assertEquals(Short.MAX_VALUE, decoded!!.seq)
    }

    @Test
    fun `decodeFrame with negative seq (unsigned interpretation)`() {
        val frame = WireProtocol.encodeFrame(
            msgType = MessageType.TELEMETRY_SNAPSHOT,
            seq = (-1).toShort(), // 0xFFFF
            payload = byteArrayOf(0x01)
        )

        val decoded = WireProtocol.decodeFrame(frame)

        assertNotNull(decoded)
        assertEquals((-1).toShort(), decoded!!.seq) // Should preserve the bits
    }

    // ==================== Frame Equality Tests ====================

    @Test
    fun `frame equality same content`() {
        val frame1 = WireProtocol.Frame(
            protoVer = 1,
            msgType = MessageType.COMMAND,
            seq = 100,
            payload = byteArrayOf(0x01, 0x02)
        )
        val frame2 = WireProtocol.Frame(
            protoVer = 1,
            msgType = MessageType.COMMAND,
            seq = 100,
            payload = byteArrayOf(0x01, 0x02)
        )

        assertEquals(frame1, frame2)
        assertEquals(frame1.hashCode(), frame2.hashCode())
    }

    @Test
    fun `frame inequality different payload`() {
        val frame1 = WireProtocol.Frame(
            protoVer = 1,
            msgType = MessageType.COMMAND,
            seq = 100,
            payload = byteArrayOf(0x01, 0x02)
        )
        val frame2 = WireProtocol.Frame(
            protoVer = 1,
            msgType = MessageType.COMMAND,
            seq = 100,
            payload = byteArrayOf(0x01, 0x03)  // Different!
        )

        assertNotEquals(frame1, frame2)
    }
}
