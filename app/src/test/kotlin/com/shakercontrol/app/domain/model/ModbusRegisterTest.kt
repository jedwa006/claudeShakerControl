package com.shakercontrol.app.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ModbusRegister data model and Lc108Registers catalog.
 */
class ModbusRegisterTest {

    // --- Register Catalog Tests ---

    @Test
    fun `allRegisters contains expected number of registers`() {
        // Should have a reasonable number of registers
        assertTrue(Lc108Registers.allRegisters.size >= 20)
    }

    @Test
    fun `commonRegisters is subset of allRegisters`() {
        val allAddresses = Lc108Registers.allRegisters.map { it.address }.toSet()
        val commonAddresses = Lc108Registers.commonRegisters.map { it.address }.toSet()

        assertTrue(allAddresses.containsAll(commonAddresses))
    }

    @Test
    fun `commonRegisters only contains COMMON access level`() {
        assertTrue(Lc108Registers.commonRegisters.all {
            it.accessLevel == RegisterAccessLevel.COMMON
        })
    }

    @Test
    fun `byCategory contains all registers`() {
        val countByCategory = Lc108Registers.byCategory.values.sumOf { it.size }
        assertEquals(Lc108Registers.allRegisters.size, countByCategory)
    }

    @Test
    fun `protectedRegisters contains communication registers`() {
        // RS-485 config registers should be protected
        assertTrue(Lc108Registers.protectedRegisters.contains(Lc108Registers.RS485_ADDR.address))
        assertTrue(Lc108Registers.protectedRegisters.contains(Lc108Registers.BAUD_RATE.address))
        assertTrue(Lc108Registers.protectedRegisters.contains(Lc108Registers.PARITY.address))
    }

    @Test
    fun `protectedRegisters does not contain normal registers`() {
        assertFalse(Lc108Registers.protectedRegisters.contains(Lc108Registers.SV.address))
        assertFalse(Lc108Registers.protectedRegisters.contains(Lc108Registers.P1.address))
    }

    // --- Specific Register Tests ---

    @Test
    fun `PV register has correct properties`() {
        val pv = Lc108Registers.PV
        assertEquals(0, pv.address)
        assertEquals("PV", pv.name)
        assertEquals(RegisterCategory.REALTIME, pv.category)
        assertTrue(pv.isReadOnly)
        assertTrue(pv.dataType is RegisterDataType.ScaledInt)
    }

    @Test
    fun `SV register has correct properties`() {
        val sv = Lc108Registers.SV
        assertEquals(5, sv.address)
        assertEquals("SV", sv.name)
        assertEquals(RegisterCategory.SETPOINT, sv.category)
        assertFalse(sv.isReadOnly)
    }

    @Test
    fun `MODE register has correct enum options`() {
        val mode = Lc108Registers.MODE
        assertEquals(0x000D, mode.address)
        assertTrue(mode.dataType is RegisterDataType.Enum)

        val enumType = mode.dataType as RegisterDataType.Enum
        assertEquals("PID (Auto)", enumType.options[0])
        assertEquals("Manual", enumType.options[1])
        assertEquals("Stop", enumType.options[2])
    }

    @Test
    fun `PID tuning registers are in correct category`() {
        assertEquals(RegisterCategory.PID_TUNING, Lc108Registers.P1.category)
        assertEquals(RegisterCategory.PID_TUNING, Lc108Registers.I1.category)
        assertEquals(RegisterCategory.PID_TUNING, Lc108Registers.D1.category)
    }

    @Test
    fun `alarm registers are in correct category`() {
        assertEquals(RegisterCategory.ALARMS, Lc108Registers.AL1_TYPE.category)
        assertEquals(RegisterCategory.ALARMS, Lc108Registers.AL1.category)
    }

    // --- RegisterDataType Tests ---

    @Test
    fun `ScaledInt default scale is 0_1`() {
        val scaledInt = RegisterDataType.ScaledInt()
        assertEquals(0.1f, scaledInt.scale, 0.001f)
    }

    @Test
    fun `ScaledInt default decimal places is 1`() {
        val scaledInt = RegisterDataType.ScaledInt()
        assertEquals(1, scaledInt.decimalPlaces)
    }

    @Test
    fun `RawInt can have min and max bounds`() {
        val rawInt = RegisterDataType.RawInt(unit = "s", min = 0, max = 9999)
        assertEquals(0, rawInt.min)
        assertEquals(9999, rawInt.max)
    }

    @Test
    fun `Enum options map is accessible`() {
        val enumType = RegisterDataType.Enum(
            options = mapOf(0 to "Off", 1 to "On")
        )
        assertEquals("Off", enumType.options[0])
        assertEquals("On", enumType.options[1])
    }

    @Test
    fun `Bitfield flags map is accessible`() {
        val bitfield = RegisterDataType.Bitfield(
            flags = mapOf(0 to "Flag0", 1 to "Flag1", 2 to "Flag2")
        )
        assertEquals("Flag0", bitfield.flags[0])
        assertEquals("Flag1", bitfield.flags[1])
        assertEquals("Flag2", bitfield.flags[2])
    }

    // --- RegisterValue Tests ---

    @Test
    fun `RegisterValue hasChanges is false when no pending value`() {
        val value = RegisterValue(register = Lc108Registers.SV, currentValue = 100, pendingValue = null)
        assertFalse(value.hasChanges)
    }

    @Test
    fun `RegisterValue hasChanges is true when pending differs from current`() {
        val value = RegisterValue(register = Lc108Registers.SV, currentValue = 100, pendingValue = 200)
        assertTrue(value.hasChanges)
    }

    @Test
    fun `RegisterValue hasChanges is false when pending equals current`() {
        val value = RegisterValue(register = Lc108Registers.SV, currentValue = 100, pendingValue = 100)
        assertFalse(value.hasChanges)
    }

    @Test
    fun `RegisterValue displayValue returns pending when available`() {
        val value = RegisterValue(register = Lc108Registers.SV, currentValue = 100, pendingValue = 200)
        assertEquals(200, value.displayValue)
    }

    @Test
    fun `RegisterValue displayValue returns current when no pending`() {
        val value = RegisterValue(register = Lc108Registers.SV, currentValue = 100, pendingValue = null)
        assertEquals(100, value.displayValue)
    }

    @Test
    fun `RegisterValue displayValue returns null when both null`() {
        val value = RegisterValue(register = Lc108Registers.SV, currentValue = null, pendingValue = null)
        assertNull(value.displayValue)
    }

    // --- RegisterCategory Tests ---

    @Test
    fun `RegisterCategory displayName is human readable`() {
        assertEquals("Real-time Values", RegisterCategory.REALTIME.displayName)
        assertEquals("Setpoint", RegisterCategory.SETPOINT.displayName)
        assertEquals("PID Tuning", RegisterCategory.PID_TUNING.displayName)
        assertEquals("Alarms", RegisterCategory.ALARMS.displayName)
        assertEquals("Input/Output", RegisterCategory.INPUT_OUTPUT.displayName)
        assertEquals("Control Settings", RegisterCategory.CONTROL.displayName)
        assertEquals("Communication", RegisterCategory.COMMUNICATION.displayName)
        assertEquals("System", RegisterCategory.SYSTEM.displayName)
    }

    // --- RegisterAccessLevel Tests ---

    @Test
    fun `RegisterAccessLevel values exist`() {
        assertEquals(2, RegisterAccessLevel.entries.size)
        assertNotNull(RegisterAccessLevel.COMMON)
        assertNotNull(RegisterAccessLevel.SERVICE)
    }

    // --- findByAddress Tests ---

    @Test
    fun `findByAddress returns correct register`() {
        val sv = Lc108Registers.findByAddress(5)
        assertNotNull(sv)
        assertEquals("SV", sv!!.name)
    }

    @Test
    fun `findByAddress returns null for unknown address`() {
        val unknown = Lc108Registers.findByAddress(9999)
        assertNull(unknown)
    }
}
