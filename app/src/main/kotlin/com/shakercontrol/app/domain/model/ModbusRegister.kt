package com.shakercontrol.app.domain.model

/**
 * Modbus register definition for LC108/FT200 PID controllers.
 * Used by the Register Editor screen to display and edit register values.
 */
data class ModbusRegister(
    val address: Int,
    val name: String,
    val description: String,
    val category: RegisterCategory,
    val dataType: RegisterDataType,
    val accessLevel: RegisterAccessLevel,
    val isReadOnly: Boolean = false,
    val helpText: String? = null
)

/**
 * Register categories for organizing the UI.
 */
enum class RegisterCategory(val displayName: String, val sortOrder: Int) {
    REALTIME("Real-time Values", 0),
    SETPOINT("Setpoint", 1),
    PID_TUNING("PID Tuning", 2),
    ALARMS("Alarms", 3),
    INPUT_OUTPUT("Input/Output", 4),
    CONTROL("Control Settings", 5),
    COMMUNICATION("Communication", 6),
    SYSTEM("System", 7);
}

/**
 * Access level determines visibility in the UI.
 */
enum class RegisterAccessLevel {
    COMMON,      // Always visible (SV limits, alarm setpoints)
    SERVICE      // Only visible in service mode (most registers)
}

/**
 * Data types with their display/edit behavior.
 */
sealed class RegisterDataType {
    /**
     * Scaled integer (value × scale factor).
     * Display as float, edit with number input or slider.
     */
    data class ScaledInt(
        val scale: Float = 0.1f,  // e.g., 0.1 means value is ×10
        val unit: String = "",
        val min: Float? = null,
        val max: Float? = null,
        val decimalPlaces: Int = 1
    ) : RegisterDataType()

    /**
     * Raw integer value.
     * Edit with number input or slider.
     */
    data class RawInt(
        val unit: String = "",
        val min: Int? = null,
        val max: Int? = null
    ) : RegisterDataType()

    /**
     * Enumerated value with named options.
     * Edit with dropdown.
     */
    data class Enum(
        val options: Map<Int, String>  // value -> display name
    ) : RegisterDataType()

    /**
     * Boolean (0/1).
     * Edit with toggle switch.
     */
    data object Boolean : RegisterDataType()

    /**
     * Bitfield with named flags.
     * Display as list of flags, typically read-only.
     */
    data class Bitfield(
        val flags: Map<Int, String>  // bit position -> flag name
    ) : RegisterDataType()
}

/**
 * Represents a register value that may have been modified.
 */
data class RegisterValue(
    val register: ModbusRegister,
    val currentValue: Int?,       // Value read from controller (null if not yet read)
    val pendingValue: Int? = null, // Staged value to write (null if unchanged)
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val hasChanges: Boolean
        get() = pendingValue != null && pendingValue != currentValue

    val displayValue: Int?
        get() = pendingValue ?: currentValue
}

/**
 * LC108/FT200 register catalog.
 * Based on docs/lc108_register_reference.md
 */
object Lc108Registers {

    // ==================== Real-time Values (Read-only) ====================

    val PV = ModbusRegister(
        address = 0x0000,
        name = "PV",
        description = "Process Value",
        category = RegisterCategory.REALTIME,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "°C", decimalPlaces = 1),
        accessLevel = RegisterAccessLevel.SERVICE,
        isReadOnly = true,
        helpText = "Current temperature reading from the sensor"
    )

    val MV1 = ModbusRegister(
        address = 0x0001,
        name = "MV1",
        description = "Output 1 %",
        category = RegisterCategory.REALTIME,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "%", min = 0f, max = 100f),
        accessLevel = RegisterAccessLevel.SERVICE,
        isReadOnly = true,
        helpText = "Current output percentage for heating/cooling"
    )

    val MV2 = ModbusRegister(
        address = 0x0002,
        name = "MV2",
        description = "Output 2 %",
        category = RegisterCategory.REALTIME,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "%", min = 0f, max = 100f),
        accessLevel = RegisterAccessLevel.SERVICE,
        isReadOnly = true,
        helpText = "Current output percentage for secondary output"
    )

    val STATUS = ModbusRegister(
        address = 0x0004,
        name = "Status",
        description = "Status Flags",
        category = RegisterCategory.REALTIME,
        dataType = RegisterDataType.Bitfield(
            flags = mapOf(
                0 to "Alarm 1",
                1 to "Alarm 2",
                2 to "Burnout",
                3 to "Manual mode",
                4 to "Auto-tune active",
                5 to "Output 1 active",
                6 to "Output 2 active"
            )
        ),
        accessLevel = RegisterAccessLevel.SERVICE,
        isReadOnly = true,
        helpText = "Controller status bit flags"
    )

    // ==================== Setpoint ====================

    val SV = ModbusRegister(
        address = 0x0005,
        name = "SV",
        description = "Setpoint Value",
        category = RegisterCategory.SETPOINT,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "°C", decimalPlaces = 1),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Target temperature setpoint"
    )

    val LSPL = ModbusRegister(
        address = 0x0044,
        name = "LSPL",
        description = "SV Lower Limit",
        category = RegisterCategory.SETPOINT,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "°C", min = -200f, max = 850f),
        accessLevel = RegisterAccessLevel.COMMON,
        helpText = "Minimum allowed setpoint value. Prevents setting SV below this limit."
    )

    val USPL = ModbusRegister(
        address = 0x0045,
        name = "USPL",
        description = "SV Upper Limit",
        category = RegisterCategory.SETPOINT,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "°C", min = -200f, max = 850f),
        accessLevel = RegisterAccessLevel.COMMON,
        helpText = "Maximum allowed setpoint value. Prevents setting SV above this limit."
    )

    // ==================== Control Mode ====================

    val MODE = ModbusRegister(
        address = 0x000D,
        name = "Mode",
        description = "Control Mode",
        category = RegisterCategory.CONTROL,
        dataType = RegisterDataType.Enum(
            options = mapOf(
                0 to "PID (Auto)",
                1 to "Manual",
                2 to "Stop",
                5 to "Program End"
            )
        ),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Operating mode: PID=automatic control, Manual=fixed output, Stop=output disabled"
    )

    val AT = ModbusRegister(
        address = 0x000C,
        name = "AT",
        description = "Auto-Tune",
        category = RegisterCategory.CONTROL,
        dataType = RegisterDataType.Enum(
            options = mapOf(
                0 to "Off",
                1 to "Start/Running"
            )
        ),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Start auto-tuning to optimize PID parameters. Controller will oscillate around setpoint during tuning."
    )

    val MANUAL_OUTPUT = ModbusRegister(
        address = 0x0008,
        name = "MV",
        description = "Manual Output",
        category = RegisterCategory.CONTROL,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "%", min = 0f, max = 100f),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Output percentage when in Manual mode"
    )

    // ==================== PID Tuning ====================

    val P1 = ModbusRegister(
        address = 0x0018,
        name = "P",
        description = "Proportional Band",
        category = RegisterCategory.PID_TUNING,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "%", min = 0f, max = 999.9f),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Proportional band (P). Higher values = less aggressive response. 0 = ON/OFF control."
    )

    val I1 = ModbusRegister(
        address = 0x0019,
        name = "I",
        description = "Integral Time",
        category = RegisterCategory.PID_TUNING,
        dataType = RegisterDataType.RawInt(unit = "sec", min = 0, max = 9999),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Integral time (I) in seconds. 0 = disabled. Lower values = faster integral action."
    )

    val D1 = ModbusRegister(
        address = 0x001A,
        name = "D",
        description = "Derivative Time",
        category = RegisterCategory.PID_TUNING,
        dataType = RegisterDataType.RawInt(unit = "sec", min = 0, max = 9999),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Derivative time (D) in seconds. 0 = disabled. Helps reduce overshoot."
    )

    val ARW = ModbusRegister(
        address = 0x001B,
        name = "ARW",
        description = "Anti-Reset Windup",
        category = RegisterCategory.PID_TUNING,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "%", min = 0f, max = 100f),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Anti-reset windup limit. Prevents integral term from growing excessively."
    )

    val CTRL_TYPE = ModbusRegister(
        address = 0x0010,
        name = "Control Type",
        description = "Control Action",
        category = RegisterCategory.PID_TUNING,
        dataType = RegisterDataType.Enum(
            options = mapOf(
                0 to "Reverse (Heating)",
                1 to "Direct (Cooling)"
            )
        ),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Reverse=increase output when PV<SV (heating). Direct=increase output when PV>SV (cooling)."
    )

    // ==================== Alarms ====================

    val AL1 = ModbusRegister(
        address = 0x000E,
        name = "AL1",
        description = "Alarm 1 Setpoint",
        category = RegisterCategory.ALARMS,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "°C", decimalPlaces = 1),
        accessLevel = RegisterAccessLevel.COMMON,
        helpText = "Alarm 1 trigger temperature"
    )

    val AL2 = ModbusRegister(
        address = 0x000F,
        name = "AL2",
        description = "Alarm 2 Setpoint",
        category = RegisterCategory.ALARMS,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "°C", decimalPlaces = 1),
        accessLevel = RegisterAccessLevel.COMMON,
        helpText = "Alarm 2 trigger temperature"
    )

    val AL1_TYPE = ModbusRegister(
        address = 0x0046,
        name = "AL1 Type",
        description = "Alarm 1 Function",
        category = RegisterCategory.ALARMS,
        dataType = RegisterDataType.Enum(
            options = mapOf(
                0 to "Off",
                1 to "High limit",
                2 to "Low limit",
                3 to "High deviation",
                4 to "Low deviation",
                5 to "Band (in range)",
                6 to "Band (out of range)"
            )
        ),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Alarm 1 trigger condition type"
    )

    val AL2_TYPE = ModbusRegister(
        address = 0x0047,
        name = "AL2 Type",
        description = "Alarm 2 Function",
        category = RegisterCategory.ALARMS,
        dataType = RegisterDataType.Enum(
            options = mapOf(
                0 to "Off",
                1 to "High limit",
                2 to "Low limit",
                3 to "High deviation",
                4 to "Low deviation",
                5 to "Band (in range)",
                6 to "Band (out of range)"
            )
        ),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Alarm 2 trigger condition type"
    )

    val AL1_HYS = ModbusRegister(
        address = 0x0048,
        name = "AL1 Hys",
        description = "Alarm 1 Hysteresis",
        category = RegisterCategory.ALARMS,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "°C", min = 0f, max = 100f),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Hysteresis for alarm 1 to prevent rapid on/off cycling"
    )

    val AL2_HYS = ModbusRegister(
        address = 0x0049,
        name = "AL2 Hys",
        description = "Alarm 2 Hysteresis",
        category = RegisterCategory.ALARMS,
        dataType = RegisterDataType.ScaledInt(scale = 0.1f, unit = "°C", min = 0f, max = 100f),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Hysteresis for alarm 2 to prevent rapid on/off cycling"
    )

    // ==================== Input/Output ====================

    val INPUT_TYPE = ModbusRegister(
        address = 0x0020,
        name = "Input Type",
        description = "Sensor Type",
        category = RegisterCategory.INPUT_OUTPUT,
        dataType = RegisterDataType.Enum(
            options = mapOf(
                0 to "K thermocouple",
                1 to "J thermocouple",
                2 to "T thermocouple",
                3 to "E thermocouple",
                4 to "N thermocouple",
                5 to "R thermocouple",
                6 to "S thermocouple",
                7 to "B thermocouple",
                8 to "PT100 RTD",
                9 to "JPT100 RTD",
                10 to "0-20mA",
                11 to "4-20mA",
                12 to "0-5V",
                13 to "1-5V",
                14 to "0-10V"
            )
        ),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Temperature sensor or analog input type"
    )

    val INPUT_FILTER = ModbusRegister(
        address = 0x0022,
        name = "Filter",
        description = "Input Filter",
        category = RegisterCategory.INPUT_OUTPUT,
        dataType = RegisterDataType.RawInt(unit = "", min = 0, max = 100),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Input signal filter time constant. Higher = smoother but slower response."
    )

    val DECIMAL_POINT = ModbusRegister(
        address = 0x0021,
        name = "Decimal",
        description = "Decimal Point",
        category = RegisterCategory.INPUT_OUTPUT,
        dataType = RegisterDataType.Enum(
            options = mapOf(
                0 to "0 (integer)",
                1 to "1 decimal",
                2 to "2 decimals"
            )
        ),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Display decimal point position"
    )

    // ==================== Communication ====================

    val RS485_ADDR = ModbusRegister(
        address = 0x0050,
        name = "Address",
        description = "RS-485 Address",
        category = RegisterCategory.COMMUNICATION,
        dataType = RegisterDataType.RawInt(unit = "", min = 1, max = 247),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Modbus slave address. WARNING: Changing this will lose communication until app is updated."
    )

    val BAUD_RATE = ModbusRegister(
        address = 0x0051,
        name = "Baud Rate",
        description = "RS-485 Baud Rate",
        category = RegisterCategory.COMMUNICATION,
        dataType = RegisterDataType.Enum(
            options = mapOf(
                0 to "1200",
                1 to "2400",
                2 to "4800",
                3 to "9600",
                4 to "19200",
                5 to "38400"
            )
        ),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Serial communication speed. Must match ESP32 configuration."
    )

    val PARITY = ModbusRegister(
        address = 0x0052,
        name = "Parity",
        description = "RS-485 Parity",
        category = RegisterCategory.COMMUNICATION,
        dataType = RegisterDataType.Enum(
            options = mapOf(
                0 to "None",
                1 to "Even",
                2 to "Odd"
            )
        ),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Serial parity setting. Must match ESP32 configuration."
    )

    // ==================== System ====================

    val LOCK = ModbusRegister(
        address = 0x0060,
        name = "Lock",
        description = "Parameter Lock",
        category = RegisterCategory.SYSTEM,
        dataType = RegisterDataType.Enum(
            options = mapOf(
                0 to "Unlocked",
                1 to "SV locked",
                2 to "All locked"
            )
        ),
        accessLevel = RegisterAccessLevel.SERVICE,
        helpText = "Prevents front panel parameter changes"
    )

    // ==================== Catalog ====================

    /**
     * All registers organized by category.
     */
    val allRegisters: List<ModbusRegister> = listOf(
        // Real-time
        PV, MV1, MV2, STATUS,
        // Setpoint
        SV, LSPL, USPL,
        // Control
        MODE, AT, MANUAL_OUTPUT,
        // PID Tuning
        P1, I1, D1, ARW, CTRL_TYPE,
        // Alarms
        AL1, AL2, AL1_TYPE, AL2_TYPE, AL1_HYS, AL2_HYS,
        // Input/Output
        INPUT_TYPE, INPUT_FILTER, DECIMAL_POINT,
        // Communication
        RS485_ADDR, BAUD_RATE, PARITY,
        // System
        LOCK
    )

    /**
     * Common registers visible without service mode.
     */
    val commonRegisters: List<ModbusRegister> =
        allRegisters.filter { it.accessLevel == RegisterAccessLevel.COMMON }

    /**
     * Registers grouped by category.
     */
    val byCategory: Map<RegisterCategory, List<ModbusRegister>> =
        allRegisters.groupBy { it.category }
            .toSortedMap(compareBy { it.sortOrder })

    /**
     * Protected registers that require extra confirmation.
     * Changing these could break communication or cause operational issues.
     */
    val protectedRegisters: Set<Int> = setOf(
        RS485_ADDR.address,
        BAUD_RATE.address,
        PARITY.address
    )

    /**
     * Find register by address.
     */
    fun findByAddress(address: Int): ModbusRegister? =
        allRegisters.find { it.address == address }
}