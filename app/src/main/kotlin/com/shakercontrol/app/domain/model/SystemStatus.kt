package com.shakercontrol.app.domain.model

/**
 * Overall system status combining all state information.
 */
data class SystemStatus(
    val connectionState: ConnectionState,
    val machineState: MachineState,
    val mcuHeartbeatAgeMs: Long,
    val bleHeartbeatAgeMs: Long,
    val alarmSummary: AlarmSummary,
    val isServiceModeEnabled: Boolean,
    val deviceName: String?,
    val rssiDbm: Int?,
    val firmwareVersion: String?,
    val firmwareBuildId: String? = null,
    val protocolVersion: Int?,
    val capabilities: SubsystemCapabilities = SubsystemCapabilities.DEFAULT,
    val sessionLeaseMs: Int = DEFAULT_LEASE_MS,
    val sessionLeaseAgeMs: Long = 0L,
    val rssiHistory: List<Int> = emptyList(),
    // Lazy polling state (from telemetry run state)
    val lazyPollActive: Boolean = false,
    val idleTimeoutMinutes: Int = 0
) {
    val isMcuHeartbeatStale: Boolean
        get() = mcuHeartbeatAgeMs > MCU_STALE_THRESHOLD_MS

    val isMcuHeartbeatMissing: Boolean
        get() = mcuHeartbeatAgeMs > MCU_MISSING_THRESHOLD_MS

    val isBleHeartbeatStale: Boolean
        get() = bleHeartbeatAgeMs > BLE_STALE_THRESHOLD_MS

    val mcuHeartbeatStatus: HeartbeatStatus
        get() = when {
            isMcuHeartbeatMissing -> HeartbeatStatus.MISSING
            isMcuHeartbeatStale -> HeartbeatStatus.STALE
            else -> HeartbeatStatus.OK
        }

    /**
     * Session lease health status based on how close we are to expiry.
     */
    val sessionLeaseStatus: SessionLeaseStatus
        get() = when {
            connectionState == ConnectionState.DISCONNECTED -> SessionLeaseStatus.NO_SESSION
            sessionLeaseAgeMs > sessionLeaseMs -> SessionLeaseStatus.EXPIRED
            sessionLeaseAgeMs > sessionLeaseMs * LEASE_WARNING_THRESHOLD -> SessionLeaseStatus.WARNING
            else -> SessionLeaseStatus.OK
        }

    /**
     * Average RSSI from recent history for smoothed signal quality.
     */
    val averageRssi: Int?
        get() = if (rssiHistory.isNotEmpty()) rssiHistory.average().toInt() else rssiDbm

    /**
     * Signal quality level based on RSSI.
     */
    val signalQuality: SignalQuality
        get() {
            val rssi = averageRssi ?: return SignalQuality.UNKNOWN
            return when {
                rssi >= RSSI_EXCELLENT -> SignalQuality.EXCELLENT
                rssi >= RSSI_GOOD -> SignalQuality.GOOD
                rssi >= RSSI_FAIR -> SignalQuality.FAIR
                else -> SignalQuality.POOR
            }
        }

    /**
     * Full firmware version including build ID (e.g., "0.2.0+26011901").
     */
    val fullFirmwareVersion: String?
        get() = when {
            firmwareVersion == null -> null
            firmwareBuildId != null -> "$firmwareVersion+$firmwareBuildId"
            else -> firmwareVersion
        }

    companion object {
        const val MCU_STALE_THRESHOLD_MS = 500L
        const val MCU_MISSING_THRESHOLD_MS = 3000L
        const val BLE_STALE_THRESHOLD_MS = 500L
        const val DEFAULT_LEASE_MS = 3000
        const val LEASE_WARNING_THRESHOLD = 0.7 // Warn at 70% of lease time
        const val RSSI_HISTORY_SIZE = 10

        // RSSI thresholds (dBm)
        const val RSSI_EXCELLENT = -50
        const val RSSI_GOOD = -65
        const val RSSI_FAIR = -80

        val DISCONNECTED = SystemStatus(
            connectionState = ConnectionState.DISCONNECTED,
            machineState = MachineState.IDLE,
            mcuHeartbeatAgeMs = Long.MAX_VALUE,
            bleHeartbeatAgeMs = Long.MAX_VALUE,
            alarmSummary = AlarmSummary(0, 0, null),
            isServiceModeEnabled = false,
            deviceName = null,
            rssiDbm = null,
            firmwareVersion = null,
            protocolVersion = null,
            capabilities = SubsystemCapabilities.DEFAULT,
            sessionLeaseMs = DEFAULT_LEASE_MS,
            sessionLeaseAgeMs = 0L,
            rssiHistory = emptyList(),
            lazyPollActive = false,
            idleTimeoutMinutes = 0
        )
    }
}

/**
 * Session lease health status.
 */
enum class SessionLeaseStatus {
    NO_SESSION,  // Not connected
    OK,          // Lease recently refreshed
    WARNING,     // Approaching lease expiry
    EXPIRED;     // Lease has expired

    val displayName: String
        get() = when (this) {
            NO_SESSION -> "No Session"
            OK -> "OK"
            WARNING -> "Warning"
            EXPIRED -> "Expired"
        }
}

/**
 * Signal quality based on RSSI.
 */
enum class SignalQuality {
    UNKNOWN,
    POOR,
    FAIR,
    GOOD,
    EXCELLENT;

    val displayName: String
        get() = when (this) {
            UNKNOWN -> "Unknown"
            POOR -> "Poor"
            FAIR -> "Fair"
            GOOD -> "Good"
            EXCELLENT -> "Excellent"
        }

    val bars: Int
        get() = when (this) {
            UNKNOWN -> 0
            POOR -> 1
            FAIR -> 2
            GOOD -> 3
            EXCELLENT -> 4
        }
}

enum class HeartbeatStatus {
    OK,
    STALE,
    MISSING;

    val displayName: String
        get() = when (this) {
            OK -> "OK"
            STALE -> "Stale"
            MISSING -> "Missing"
        }
}

/**
 * Digital I/O status from telemetry.
 */
data class IoStatus(
    val digitalInputs: Int,   // di_bits (bits 0..7)
    val relayOutputs: Int     // ro_bits (bits 0..7)
) {
    fun isInputHigh(channel: Int): Boolean {
        require(channel in 1..8) { "Channel must be 1-8" }
        return (digitalInputs shr (channel - 1)) and 1 == 1
    }

    fun isOutputHigh(channel: Int): Boolean {
        require(channel in 1..8) { "Channel must be 1-8" }
        return (relayOutputs shr (channel - 1)) and 1 == 1
    }
}

/**
 * Interlock/status indicators for the home screen card.
 */
data class InterlockStatus(
    val isEStopActive: Boolean,
    val isDoorLocked: Boolean,
    val isLn2Present: Boolean,
    val isPowerEnabled: Boolean,
    val isHeatersEnabled: Boolean,
    val isMotorEnabled: Boolean
)
