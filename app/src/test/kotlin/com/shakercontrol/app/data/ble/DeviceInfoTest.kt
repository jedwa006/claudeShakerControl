package com.shakercontrol.app.data.ble

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DeviceInfo parsing from the Device Info characteristic.
 */
class DeviceInfoTest {

    @Test
    fun `parse valid 12-byte device info`() {
        // Construct valid device info bytes:
        // proto_ver=1, fw_major=0, fw_minor=1, fw_patch=0
        // build_id=12345678 (little-endian: 4E 61 BC 00)
        // cap_bits=0x3F (little-endian: 3F 00 00 00)
        val data = byteArrayOf(
            0x01,                   // proto_ver
            0x00, 0x01, 0x00,       // fw: 0.1.0
            0x4E, 0x61, (0xBC).toByte(), 0x00,  // build_id = 12345678 (0x00BC614E)
            0x3F, 0x00, 0x00, 0x00  // cap_bits = 0x3F
        )

        val info = DeviceInfo.parse(data)

        assertNotNull(info)
        assertEquals(1, info!!.protocolVersion)
        assertEquals(0, info.firmwareMajor)
        assertEquals(1, info.firmwareMinor)
        assertEquals(0, info.firmwarePatch)
        assertEquals("0.1.0", info.firmwareVersionString)
        assertEquals(12345678L, info.buildId)
        assertEquals(0x3FL, info.capabilityBits)
    }

    @Test
    fun `parse firmware version 1_2_3`() {
        val data = byteArrayOf(
            0x01,                   // proto_ver
            0x01, 0x02, 0x03,       // fw: 1.2.3
            0x00, 0x00, 0x00, 0x00, // build_id = 0
            0x00, 0x00, 0x00, 0x00  // cap_bits = 0
        )

        val info = DeviceInfo.parse(data)

        assertNotNull(info)
        assertEquals(1, info!!.firmwareMajor)
        assertEquals(2, info.firmwareMinor)
        assertEquals(3, info.firmwarePatch)
        assertEquals("1.2.3", info.firmwareVersionString)
    }

    @Test
    fun `parse protocol version 2`() {
        val data = byteArrayOf(
            0x02,                   // proto_ver = 2
            0x00, 0x00, 0x00,       // fw: 0.0.0
            0x00, 0x00, 0x00, 0x00, // build_id
            0x00, 0x00, 0x00, 0x00  // cap_bits
        )

        val info = DeviceInfo.parse(data)

        assertNotNull(info)
        assertEquals(2, info!!.protocolVersion)
    }

    @Test
    fun `parse max values`() {
        val data = byteArrayOf(
            0xFF.toByte(),          // proto_ver = 255
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // fw: 255.255.255
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // build_id = MAX_UINT32
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()  // cap_bits = MAX_UINT32
        )

        val info = DeviceInfo.parse(data)

        assertNotNull(info)
        assertEquals(255, info!!.protocolVersion)
        assertEquals(255, info.firmwareMajor)
        assertEquals(255, info.firmwareMinor)
        assertEquals(255, info.firmwarePatch)
        assertEquals("255.255.255", info.firmwareVersionString)
        assertEquals(0xFFFFFFFFL, info.buildId)
        assertEquals(0xFFFFFFFFL, info.capabilityBits)
    }

    @Test
    fun `parse returns null for too short data`() {
        // Only 11 bytes, need 12
        val data = byteArrayOf(0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        val info = DeviceInfo.parse(data)

        assertNull(info)
    }

    @Test
    fun `parse returns null for empty data`() {
        val data = byteArrayOf()

        val info = DeviceInfo.parse(data)

        assertNull(info)
    }

    @Test
    fun `parse accepts extra bytes (future compatibility)`() {
        // 14 bytes - 2 extra bytes should be ignored
        val data = byteArrayOf(
            0x01,                   // proto_ver
            0x00, 0x01, 0x00,       // fw: 0.1.0
            0x00, 0x00, 0x00, 0x00, // build_id
            0x01, 0x00, 0x00, 0x00, // cap_bits = 1
            0xAA.toByte(), 0xBB.toByte() // extra bytes (ignored)
        )

        val info = DeviceInfo.parse(data)

        assertNotNull(info)
        assertEquals(1, info!!.protocolVersion)
        assertEquals("0.1.0", info.firmwareVersionString)
        assertEquals(1L, info.capabilityBits)
    }

    @Test
    fun `parse capability bits all supported`() {
        // All 6 capability bits set: 0x3F = 0011 1111
        val data = byteArrayOf(
            0x01,                   // proto_ver
            0x00, 0x01, 0x00,       // fw
            0x00, 0x00, 0x00, 0x00, // build_id
            0x3F, 0x00, 0x00, 0x00  // cap_bits = 0x3F
        )

        val info = DeviceInfo.parse(data)

        assertNotNull(info)
        val caps = info!!.capabilityBits
        assertTrue((caps and CapabilityBits.SUPPORTS_SESSION_LEASE.toLong()) != 0L)
        assertTrue((caps and CapabilityBits.SUPPORTS_EVENT_LOG.toLong()) != 0L)
        assertTrue((caps and CapabilityBits.SUPPORTS_BULK_GATEWAY.toLong()) != 0L)
        assertTrue((caps and CapabilityBits.SUPPORTS_MODBUS_TOOLS.toLong()) != 0L)
        assertTrue((caps and CapabilityBits.SUPPORTS_PID_TUNING.toLong()) != 0L)
        assertTrue((caps and CapabilityBits.SUPPORTS_OTA.toLong()) != 0L)
    }

    @Test
    fun `parse capability bits partial support`() {
        // Only SESSION_LEASE and EVENT_LOG: 0x03
        val data = byteArrayOf(
            0x01,                   // proto_ver
            0x00, 0x01, 0x00,       // fw
            0x00, 0x00, 0x00, 0x00, // build_id
            0x03, 0x00, 0x00, 0x00  // cap_bits = 0x03
        )

        val info = DeviceInfo.parse(data)

        assertNotNull(info)
        val caps = info!!.capabilityBits
        assertTrue((caps and CapabilityBits.SUPPORTS_SESSION_LEASE.toLong()) != 0L)
        assertTrue((caps and CapabilityBits.SUPPORTS_EVENT_LOG.toLong()) != 0L)
        assertEquals(0L, caps and CapabilityBits.SUPPORTS_BULK_GATEWAY.toLong())
        assertEquals(0L, caps and CapabilityBits.SUPPORTS_MODBUS_TOOLS.toLong())
        assertEquals(0L, caps and CapabilityBits.SUPPORTS_PID_TUNING.toLong())
        assertEquals(0L, caps and CapabilityBits.SUPPORTS_OTA.toLong())
    }
}
