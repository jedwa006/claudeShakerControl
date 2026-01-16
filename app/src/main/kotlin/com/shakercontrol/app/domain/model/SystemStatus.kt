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
    val protocolVersion: Int?
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

    companion object {
        const val MCU_STALE_THRESHOLD_MS = 500L
        const val MCU_MISSING_THRESHOLD_MS = 3000L
        const val BLE_STALE_THRESHOLD_MS = 500L

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
            protocolVersion = null
        )
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
