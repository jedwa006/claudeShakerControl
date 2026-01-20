package com.shakercontrol.app.data.ble

import java.util.UUID

/**
 * BLE GATT UUIDs for the System Control Service.
 * These are pinned and must not change (see docs/MCU_docs/80-gatt-uuids.md).
 */
object BleConstants {
    // Service UUID
    val SERVICE_UUID: UUID = UUID.fromString("F0C5B4D2-3D1E-4A27-9B8A-2F0B3C4D5E60")

    // Characteristic UUIDs
    val CHAR_DEVICE_INFO: UUID = UUID.fromString("F0C5B4D2-3D1E-4A27-9B8A-2F0B3C4D5E61")
    val CHAR_TELEMETRY_STREAM: UUID = UUID.fromString("F0C5B4D2-3D1E-4A27-9B8A-2F0B3C4D5E62")
    val CHAR_COMMAND_RX: UUID = UUID.fromString("F0C5B4D2-3D1E-4A27-9B8A-2F0B3C4D5E63")
    val CHAR_EVENTS_ACKS: UUID = UUID.fromString("F0C5B4D2-3D1E-4A27-9B8A-2F0B3C4D5E64")
    val CHAR_BULK_GATEWAY: UUID = UUID.fromString("F0C5B4D2-3D1E-4A27-9B8A-2F0B3C4D5E65")
    val CHAR_DIAGNOSTIC_LOG: UUID = UUID.fromString("F0C5B4D2-3D1E-4A27-9B8A-2F0B3C4D5E66")

    // Standard CCCD UUID for enabling notifications/indications
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Device name prefix for filtering during scan
    const val DEVICE_NAME_PREFIX = "SYS-CTRL-"

    // Protocol version
    const val PROTOCOL_VERSION: Byte = 0x01

    // Heartbeat timing
    const val KEEPALIVE_INTERVAL_MS = 1000L
    const val DEFAULT_LEASE_MS = 3000
}

/**
 * Message types for the wire protocol.
 */
object MessageType {
    const val TELEMETRY_SNAPSHOT: Byte = 0x01
    const val COMMAND: Byte = 0x10
    const val COMMAND_ACK: Byte = 0x11
    const val EVENT: Byte = 0x20
}

/**
 * Command IDs for COMMAND messages.
 */
object CommandId {
    // Session + lease (heartbeat)
    const val OPEN_SESSION: Short = 0x0100
    const val KEEPALIVE: Short = 0x0101
    const val START_RUN: Short = 0x0102
    const val STOP_RUN: Short = 0x0103
    const val PAUSE_RUN: Short = 0x0104

    // I/O control
    const val SET_RELAY: Short = 0x0001
    const val SET_RELAY_MASK: Short = 0x0002
    const val PULSE_RELAY: Short = 0x0003

    // PID / controller interaction
    const val SET_SV: Short = 0x0020
    const val SET_MODE: Short = 0x0021
    const val REQUEST_PV_SV_REFRESH: Short = 0x0022
    const val SET_PID_PARAMS: Short = 0x0023
    const val READ_PID_PARAMS: Short = 0x0024
    const val START_AUTOTUNE: Short = 0x0025
    const val STOP_AUTOTUNE: Short = 0x0026
    const val SET_ALARM_LIMITS: Short = 0x0027
    const val READ_ALARM_LIMITS: Short = 0x0028

    // Generic Modbus register access (for register editor)
    const val READ_REGISTERS: Short = 0x0030
    const val WRITE_REGISTER: Short = 0x0031
    const val WRITE_REGISTERS: Short = 0x0032

    // Lazy polling configuration
    // Firmware v0.3.7 uses 0x0060/0x0061 with 2-byte payload [enable, timeout_min]
    // Fallback to 0x0040/0x0041 with 1-byte payload if needed
    const val SET_LAZY_POLL: Short = 0x0060
    const val GET_LAZY_POLL: Short = 0x0061
    // Legacy commands (pre-v0.3.7)
    const val SET_IDLE_TIMEOUT_LEGACY: Short = 0x0040
    const val GET_IDLE_TIMEOUT_LEGACY: Short = 0x0041

    // Safety gate commands (v0.4.0+)
    const val GET_CAPABILITIES: Short = 0x0070
    const val SET_CAPABILITY: Short = 0x0071
    const val GET_SAFETY_GATES: Short = 0x0072
    const val SET_SAFETY_GATE: Short = 0x0073

    // Maintenance / diagnostics
    const val REQUEST_SNAPSHOT_NOW: Short = 0x00F0.toShort()
    const val CLEAR_WARNINGS: Short = 0x00F1.toShort()
    const val CLEAR_LATCHED_ALARMS: Short = 0x00F2.toShort()
}

/**
 * Command ACK status codes.
 */
enum class AckStatus(val code: Byte) {
    OK(0),
    REJECTED_POLICY(1),
    INVALID_ARGS(2),
    BUSY(3),
    HW_FAULT(4),
    NOT_READY(5),
    TIMEOUT_DOWNSTREAM(6);

    companion object {
        fun fromCode(code: Byte): AckStatus =
            entries.find { it.code == code } ?: OK
    }
}

/**
 * ACK detail subcodes.
 */
object AckDetail {
    const val NONE: Short = 0x0000
    const val SESSION_INVALID: Short = 0x0001
    const val INTERLOCK_OPEN: Short = 0x0002
    const val ESTOP_ACTIVE: Short = 0x0003
    const val CONTROLLER_OFFLINE: Short = 0x0004
    const val PARAM_OUT_OF_RANGE: Short = 0x0005
}

/**
 * Event IDs for EVENT messages.
 */
object EventId {
    const val ESTOP_ASSERTED: Short = 0x1001
    const val ESTOP_CLEARED: Short = 0x1002
    const val HMI_CONNECTED: Short = 0x1100
    const val HMI_DISCONNECTED: Short = 0x1101
    const val RUN_STARTED: Short = 0x1200
    const val RUN_STOPPED: Short = 0x1201
    const val RS485_DEVICE_ONLINE: Short = 0x1300
    const val RS485_DEVICE_OFFLINE: Short = 0x1301
    const val ALARM_LATCHED: Short = 0x1400
    const val ALARM_CLEARED: Short = 0x1401
}

/**
 * Event severity levels.
 */
enum class EventSeverity(val code: Byte) {
    INFO(0),
    WARN(1),
    ALARM(2),
    CRITICAL(3);

    companion object {
        fun fromCode(code: Byte): EventSeverity =
            entries.find { it.code == code } ?: INFO
    }
}

/**
 * Run modes for START_RUN command.
 */
enum class RunMode(val code: Byte) {
    NORMAL(0),
    DRY_RUN(1),
    SERVICE(2);

    companion object {
        fun fromCode(code: Byte): RunMode =
            entries.find { it.code == code } ?: NORMAL
    }
}

/**
 * Stop modes for STOP_RUN command.
 */
enum class StopMode(val code: Byte) {
    NORMAL_STOP(0),
    ABORT(1);

    companion object {
        fun fromCode(code: Byte): StopMode =
            entries.find { it.code == code } ?: NORMAL_STOP
    }
}

/**
 * Relay states for SET_RELAY command.
 */
enum class RelayState(val code: Byte) {
    OFF(0),
    ON(1),
    TOGGLE(2)
}

/**
 * Controller modes for SET_MODE command.
 * Values per LC108/FT200 register 000Dh (AM.RS ControlMode):
 *   0 = PID (automatic control)
 *   1 = MANUAL (manual output)
 *   2 = STOP (output disabled)
 *   5 = END (program end)
 */
enum class ControllerMode(val code: Byte) {
    AUTO(0),      // LC108: 0 = PID (automatic control)
    MANUAL(1),    // LC108: 1 = Manual output
    STOP(2),      // LC108: 2 = Stop (output disabled)
    PROGRAM(5);   // LC108: 5 = END (program end)

    companion object {
        fun fromCode(code: Byte): ControllerMode =
            entries.find { it.code == code } ?: STOP
    }
}

/**
 * Alarm bits (alarm_bits u32) - see docs/MCU_docs/90-command-catalog.md section 7.
 */
object AlarmBits {
    // Original bits (0-8)
    const val ESTOP_ACTIVE = 1 shl 0
    const val DOOR_INTERLOCK_OPEN = 1 shl 1
    const val OVER_TEMP = 1 shl 2
    const val RS485_FAULT = 1 shl 3
    const val POWER_FAULT = 1 shl 4
    const val HMI_NOT_LIVE = 1 shl 5
    const val PID1_FAULT = 1 shl 6
    const val PID2_FAULT = 1 shl 7
    const val PID3_FAULT = 1 shl 8

    // New bits (9-14) - Safety gate bypasses and probe errors (v0.4.0+)
    const val GATE_DOOR_BYPASSED = 1 shl 9
    const val GATE_HMI_BYPASSED = 1 shl 10
    const val GATE_PID_BYPASSED = 1 shl 11
    const val PID1_PROBE_ERROR = 1 shl 12
    const val PID2_PROBE_ERROR = 1 shl 13
    const val PID3_PROBE_ERROR = 1 shl 14

    // Convenience masks
    const val ANY_GATE_BYPASSED = GATE_DOOR_BYPASSED or GATE_HMI_BYPASSED or GATE_PID_BYPASSED
    const val ANY_PROBE_ERROR = PID1_PROBE_ERROR or PID2_PROBE_ERROR or PID3_PROBE_ERROR
}

/**
 * Capability flags (cap_bits u32) from Device Info.
 */
object CapabilityBits {
    const val SUPPORTS_SESSION_LEASE = 1 shl 0
    const val SUPPORTS_EVENT_LOG = 1 shl 1
    const val SUPPORTS_BULK_GATEWAY = 1 shl 2
    const val SUPPORTS_MODBUS_TOOLS = 1 shl 3
    const val SUPPORTS_PID_TUNING = 1 shl 4
    const val SUPPORTS_OTA = 1 shl 5
}

/**
 * Device info parsed from the Device Info characteristic.
 * Layout (12 bytes, little-endian):
 * - proto_ver (u8)
 * - fw_major (u8), fw_minor (u8), fw_patch (u8)
 * - build_id (u32)
 * - cap_bits (u32)
 */
data class DeviceInfo(
    val protocolVersion: Int,
    val firmwareMajor: Int,
    val firmwareMinor: Int,
    val firmwarePatch: Int,
    val buildId: Long,
    val capabilityBits: Long
) {
    val firmwareVersionString: String
        get() = "$firmwareMajor.$firmwareMinor.$firmwarePatch"

    val buildIdHex: String
        get() = buildId.toString(16).padStart(8, '0')

    val fullVersionString: String
        get() = "$firmwareVersionString+$buildIdHex"

    companion object {
        fun parse(data: ByteArray): DeviceInfo? {
            if (data.size < 12) return null

            val protoVer = data[0].toInt() and 0xFF
            val fwMajor = data[1].toInt() and 0xFF
            val fwMinor = data[2].toInt() and 0xFF
            val fwPatch = data[3].toInt() and 0xFF

            // Little-endian u32 for build_id (bytes 4-7)
            val buildId = ((data[4].toLong() and 0xFF)) or
                    ((data[5].toLong() and 0xFF) shl 8) or
                    ((data[6].toLong() and 0xFF) shl 16) or
                    ((data[7].toLong() and 0xFF) shl 24)

            // Little-endian u32 for cap_bits (bytes 8-11)
            val capBits = ((data[8].toLong() and 0xFF)) or
                    ((data[9].toLong() and 0xFF) shl 8) or
                    ((data[10].toLong() and 0xFF) shl 16) or
                    ((data[11].toLong() and 0xFF) shl 24)

            return DeviceInfo(
                protocolVersion = protoVer,
                firmwareMajor = fwMajor,
                firmwareMinor = fwMinor,
                firmwarePatch = fwPatch,
                buildId = buildId,
                capabilityBits = capBits
            )
        }
    }
}

/**
 * Subsystem IDs for capability configuration.
 * Used with CMD_SET_CAPABILITY (0x0071).
 */
object SubsystemId {
    const val PID1: Byte = 0          // LN2 Cold
    const val PID2: Byte = 1          // Axle bearings
    const val PID3: Byte = 2          // Orbital bearings
    const val ESTOP: Byte = 3         // E-Stop (immutable - always REQUIRED)
    const val DOOR: Byte = 4          // Door interlock
    const val LN2: Byte = 5           // LN2 presence sensor
    const val MOTOR: Byte = 6         // Motor subsystem
}

/**
 * Capability levels for subsystems.
 * Used with CMD_SET_CAPABILITY (0x0071).
 */
object CapabilityLevelCode {
    const val NOT_PRESENT: Byte = 0   // Subsystem not installed
    const val OPTIONAL: Byte = 1      // Subsystem optional for start
    const val REQUIRED: Byte = 2      // Subsystem required for start
}

/**
 * Safety gate IDs for gate control.
 * Used with CMD_SET_SAFETY_GATE (0x0073).
 */
object SafetyGateId {
    const val ESTOP: Byte = 0         // E-Stop gate (CANNOT be bypassed)
    const val DOOR: Byte = 1          // Door interlock gate
    const val HMI: Byte = 2           // HMI connection gate
    const val PID1_ONLINE: Byte = 3   // PID1 online gate
    const val PID2_ONLINE: Byte = 4   // PID2 online gate
    const val PID3_ONLINE: Byte = 5   // PID3 online gate
    const val PID1_NO_PROBE_ERR: Byte = 6  // PID1 no probe error gate
    const val PID2_NO_PROBE_ERR: Byte = 7  // PID2 no probe error gate
    const val PID3_NO_PROBE_ERR: Byte = 8  // PID3 no probe error gate
}

/**
 * Relay output channel assignments.
 * Maps physical relay channels to their functions.
 */
object RelayChannel {
    const val MOTOR_CONTACTOR = 1     // CH1 = Motor contactor (state machine controlled)
    const val SOFT_START = 2          // CH2 = Soft starter START (state machine controlled)
    const val HEATER_1 = 3            // CH3 = Heater 1 / Axle (PID2 controlled)
    const val HEATER_2 = 4            // CH4 = Heater 2 / Orbital (PID3 controlled)
    const val LN2_VALVE = 5           // CH5 = LN2 solenoid valve (PID1 controlled / chilldown)
    const val DOOR_LOCK = 6           // CH6 = Door lock solenoid
    const val CHAMBER_LIGHT = 7       // CH7 = Chamber light
    // CH8 = Reserved
}

/**
 * Digital input channel assignments.
 * Maps physical DI channels to their functions.
 */
object DigitalInputChannel {
    const val ESTOP = 1               // DI1 = E-Stop (LOW = active)
    const val DOOR_CLOSED = 2         // DI2 = Door closed sensor (HIGH = closed)
    const val LN2_PRESENT = 3         // DI3 = LN2 present sensor (HIGH = present)
}
