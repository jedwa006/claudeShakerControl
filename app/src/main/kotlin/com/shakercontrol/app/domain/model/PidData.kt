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
    val alarmRelays: AlarmRelays = AlarmRelays.NONE,  // AL1/AL2 relay outputs
    val probeError: ProbeError = ProbeError.NONE,  // Probe connection errors
    val lazyPollActive: Boolean = false  // Whether lazy polling is active (affects thresholds)
) {
    /**
     * Stale threshold depends on polling mode:
     * - Fast mode (300ms/controller): 1500ms threshold
     * - Lazy mode (2000ms/controller): 7000ms threshold (3 controllers × 2000ms + margin)
     */
    private val effectiveStaleThreshold: Int
        get() = if (lazyPollActive) STALE_THRESHOLD_LAZY_MS else STALE_THRESHOLD_MS

    private val effectiveOfflineThreshold: Int
        get() = if (lazyPollActive) OFFLINE_THRESHOLD_LAZY_MS else OFFLINE_THRESHOLD_MS

    val isStale: Boolean
        get() = ageMs > effectiveStaleThreshold

    val isOffline: Boolean
        get() = ageMs > effectiveOfflineThreshold

    /**
     * True if any alarm relay (AL1 or AL2) is active.
     */
    val hasActiveAlarm: Boolean
        get() = alarmRelays.al1 || alarmRelays.al2

    /**
     * True if the probe has an error (disconnected, over-range, under-range).
     */
    val hasProbeError: Boolean
        get() = probeError != ProbeError.NONE

    /**
     * True if this controller has any issue that should show a warning/error state.
     */
    val hasAnyIssue: Boolean
        get() = isOffline || isStale || hasFault || hasProbeError

    companion object {
        // Fast mode thresholds (300ms per controller polling)
        // NOTE: Stale threshold increased from 500ms to 1500ms on 2026-01-19.
        // Firmware polls 3 controllers at 300ms intervals (~900ms round-trip per controller).
        // 500ms caused constant Online→Stale cycling. 1500ms provides margin for jitter.
        const val STALE_THRESHOLD_MS = 1500
        const val OFFLINE_THRESHOLD_MS = 3000

        // Lazy mode thresholds (2000ms per controller polling)
        // In lazy mode, firmware polls at 2000ms intervals (~6000ms round-trip for 3 controllers).
        // We need much higher thresholds to avoid constant stale/offline flickering.
        const val STALE_THRESHOLD_LAZY_MS = 7000   // 6000ms round-trip + 1000ms margin
        const val OFFLINE_THRESHOLD_LAZY_MS = 12000 // Double the stale threshold

        // Sentinel values from Omron E5CC for probe errors
        // These are the descaled values in °C - firmware sends scaled x10
        // When probe is disconnected, E5CC shows HHHH and sends max value
        //
        // NOTE: Threshold lowered from 3000°C to 500°C on 2026-01-19 to catch
        // actual E5CC probe error readings (~800°C observed). If this causes
        // false positives with legitimate high-temp processes, consider raising
        // back to 3000°C or implementing a per-controller threshold.
        const val PROBE_ERROR_THRESHOLD_HIGH = 500.0f   // Lowered to catch E5CC errors (~800°C)
        const val PROBE_ERROR_THRESHOLD_LOW = -300.0f   // -300°C is clearly an error (except LN2)

        /**
         * Detect probe error from process value.
         * For LN2 controller, very low temps are valid, so only check high range.
         */
        fun detectProbeError(pv: Float, isLn2Controller: Boolean): ProbeError {
            return when {
                pv >= PROBE_ERROR_THRESHOLD_HIGH -> ProbeError.OVER_RANGE
                !isLn2Controller && pv <= PROBE_ERROR_THRESHOLD_LOW -> ProbeError.UNDER_RANGE
                else -> ProbeError.NONE
            }
        }
    }
}

/**
 * Probe error states for temperature sensors.
 */
enum class ProbeError {
    NONE,           // No error, probe is connected and reading normally
    OVER_RANGE,     // HHHH - probe reading above measurement range (often disconnected)
    UNDER_RANGE;    // LLLL - probe reading below measurement range

    val displayName: String
        get() = when (this) {
            NONE -> "OK"
            OVER_RANGE -> "Over range (HHHH)"
            UNDER_RANGE -> "Under range (LLLL)"
        }

    val shortName: String
        get() = when (this) {
            NONE -> "OK"
            OVER_RANGE -> "HHHH"
            UNDER_RANGE -> "LLLL"
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
 * PID tuning parameters from READ_PID_PARAMS response.
 */
data class PidParams(
    val controllerId: Int,
    val pGain: Float,      // Proportional gain (descaled from x10)
    val iTime: Int,        // Integral time in seconds (0 = disabled)
    val dTime: Int         // Derivative time in seconds (0 = disabled)
)

/**
 * Alarm limit setpoints from READ_ALARM_LIMITS response.
 */
data class AlarmLimits(
    val controllerId: Int,
    val alarm1: Float,     // Alarm 1 setpoint in °C (descaled from x10)
    val alarm2: Float      // Alarm 2 setpoint in °C (descaled from x10)
)

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
