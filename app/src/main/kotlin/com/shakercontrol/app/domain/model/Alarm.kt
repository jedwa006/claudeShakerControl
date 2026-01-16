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
