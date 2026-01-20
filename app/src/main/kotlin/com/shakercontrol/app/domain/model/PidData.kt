package com.shakercontrol.app.domain.model

/**
 * PID controller data model.
 * Values from MCU are scaled x10 (e.g., 123.4°C -> 1234)
 */
data class PidData(
    val controllerId: Int,  // 1, 2, or 3
    val name: String,
    val processValue: Float,  // PV in °C (already descaled)
    val setpointValue: Float,  // SV in °C (already descaled)
    val outputPercent: Float,  // OP in % (already descaled)
    val mode: PidMode,
    val isEnabled: Boolean,
    val isOutputActive: Boolean,
    val hasFault: Boolean,
    val ageMs: Int,  // How old the RS-485 reading is
    val capabilityLevel: CapabilityLevel,
    val alarmRelays: AlarmRelays = AlarmRelays.NONE  // AL1/AL2 relay outputs
) {
    val isStale: Boolean
        get() = ageMs > STALE_THRESHOLD_MS

    val isOffline: Boolean
        get() = ageMs > OFFLINE_THRESHOLD_MS

    /**
     * True if any alarm relay (AL1 or AL2) is active.
     */
    val hasActiveAlarm: Boolean
        get() = alarmRelays.al1 || alarmRelays.al2

    companion object {
        const val STALE_THRESHOLD_MS = 500
        const val OFFLINE_THRESHOLD_MS = 3000
    }
}

/**
 * Alarm relay outputs from PID controller.
 * These are relay outputs on the PID controller itself (not the ESP32 relays).
 * AL1/AL2 can be configured in the PID controller for various alarm conditions.
 */
data class AlarmRelays(
    val al1: Boolean,  // Alarm 1 relay state
    val al2: Boolean   // Alarm 2 relay state
) {
    companion object {
        val NONE = AlarmRelays(al1 = false, al2 = false)
    }
}

enum class PidMode {
    STOP,
    MANUAL,
    AUTO,
    PROGRAM;

    val displayName: String
        get() = when (this) {
            STOP -> "Stop"
            MANUAL -> "Manual"
            AUTO -> "Auto"
            PROGRAM -> "Program"
        }
}

/**
 * Capability levels as defined in docs/dashboard-sec-v1.md section 8.
 */
enum class CapabilityLevel(val value: Int) {
    NOT_PRESENT(0),      // Not equipped / not present
    OPTIONAL(1),         // Expected but not required (warning allowed)
    REQUIRED(2),         // Expected and required (missing/fault prevents operation)
    SIMULATED(3);        // Simulated (future)

    val displayName: String
        get() = when (this) {
            NOT_PRESENT -> "Not installed"
            OPTIONAL -> "Optional"
            REQUIRED -> "Required"
            SIMULATED -> "Simulated"
        }

    companion object {
        fun fromValue(value: Int): CapabilityLevel =
            entries.find { it.value == value } ?: NOT_PRESENT
    }
}
