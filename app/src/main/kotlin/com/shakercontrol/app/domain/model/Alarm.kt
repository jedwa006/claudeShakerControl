package com.shakercontrol.app.domain.model

import java.time.Instant

/**
 * Alarm model as defined in docs/MCU_docs/90-command-catalog.md.
 */
data class Alarm(
    val id: String,
    val eventId: Int,
    val severity: AlarmSeverity,
    val source: AlarmSource,
    val message: String,
    val timestamp: Instant,
    val state: AlarmState,
    val isAcknowledged: Boolean
)

enum class AlarmSeverity(val value: Int) {
    INFO(0),
    WARNING(1),
    ALARM(2),
    CRITICAL(3);

    val displayName: String
        get() = when (this) {
            INFO -> "Info"
            WARNING -> "Warning"
            ALARM -> "Alarm"
            CRITICAL -> "Critical"
        }

    companion object {
        fun fromValue(value: Int): AlarmSeverity =
            entries.find { it.value == value } ?: INFO
    }
}

enum class AlarmSource(val value: Int) {
    SYSTEM(0),
    PID_1(1),
    PID_2(2),
    PID_3(3);

    val displayName: String
        get() = when (this) {
            SYSTEM -> "System"
            PID_1 -> "PID 1"
            PID_2 -> "PID 2"
            PID_3 -> "PID 3"
        }

    companion object {
        fun fromValue(value: Int): AlarmSource =
            entries.find { it.value == value } ?: SYSTEM
    }
}

enum class AlarmState {
    ACTIVE,
    CLEARED;

    val displayName: String
        get() = when (this) {
            ACTIVE -> "Active"
            CLEARED -> "Cleared"
        }
}

/**
 * Summary of current alarm state for the status strip.
 */
data class AlarmSummary(
    val totalCount: Int,
    val highCount: Int,  // CRITICAL + ALARM severity
    val highestSeverity: AlarmSeverity?
) {
    val hasAlarms: Boolean
        get() = totalCount > 0

    val hasHighPriorityAlarms: Boolean
        get() = highCount > 0
}

/**
 * Alarm history entry recording when an alarm bit transitioned.
 * Used to track transient alarms that may clear before user notices.
 *
 * Supports both MCU alarm bits and app-detected conditions (like probe errors).
 */
data class AlarmHistoryEntry(
    val timestamp: Instant,
    val alarmBit: Int,  // MCU alarm bit, or 0 for app-detected
    val alarmName: String,
    val severity: AlarmSeverity,
    val wasAsserted: Boolean,  // true = alarm set, false = alarm cleared
    val source: AlarmHistorySource = AlarmHistorySource.MCU  // Where the alarm came from
) {
    val transitionText: String
        get() = if (wasAsserted) "ASSERTED" else "CLEARED"

    val sourceText: String
        get() = when (source) {
            AlarmHistorySource.MCU -> "MCU"
            AlarmHistorySource.APP_PROBE_ERROR -> "App"
        }
}

/**
 * Source of alarm history entry - MCU telemetry or app-side detection.
 */
enum class AlarmHistorySource {
    MCU,              // From alarm_bits in telemetry
    APP_PROBE_ERROR   // App-detected probe error (HHHH/LLLL)
}

/**
 * Alarm bit definitions matching firmware ALARM_BIT_* constants.
 */
object AlarmBitDefinitions {
    const val ESTOP_ACTIVE = 0x0001
    const val DOOR_INTERLOCK = 0x0002
    const val OVER_TEMP = 0x0004
    const val RS485_FAULT = 0x0008
    const val POWER_FAULT = 0x0010
    const val HMI_NOT_LIVE = 0x0020
    const val PID1_FAULT = 0x0040
    const val PID2_FAULT = 0x0080
    const val PID3_FAULT = 0x0100

    fun getName(bit: Int): String = when (bit) {
        ESTOP_ACTIVE -> "E-Stop Active"
        DOOR_INTERLOCK -> "Door Interlock"
        OVER_TEMP -> "Over Temperature"
        RS485_FAULT -> "RS-485 Fault"
        POWER_FAULT -> "Power Fault"
        HMI_NOT_LIVE -> "HMI Not Live"
        PID1_FAULT -> "PID 1 Fault"
        PID2_FAULT -> "PID 2 Fault"
        PID3_FAULT -> "PID 3 Fault"
        else -> "Unknown (0x${bit.toString(16)})"
    }

    fun getSeverity(bit: Int): AlarmSeverity = when (bit) {
        ESTOP_ACTIVE -> AlarmSeverity.CRITICAL
        OVER_TEMP -> AlarmSeverity.CRITICAL
        PID1_FAULT, PID2_FAULT, PID3_FAULT -> AlarmSeverity.CRITICAL
        POWER_FAULT -> AlarmSeverity.ALARM
        DOOR_INTERLOCK -> AlarmSeverity.WARNING
        RS485_FAULT -> AlarmSeverity.WARNING
        HMI_NOT_LIVE -> AlarmSeverity.WARNING
        else -> AlarmSeverity.INFO
    }

    val ALL_BITS = listOf(
        ESTOP_ACTIVE, DOOR_INTERLOCK, OVER_TEMP, RS485_FAULT,
        POWER_FAULT, HMI_NOT_LIVE, PID1_FAULT, PID2_FAULT, PID3_FAULT
    )
}
