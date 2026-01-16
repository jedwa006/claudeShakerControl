package com.shakercontrol.app.domain.model

/**
 * Subsystem capabilities as defined in docs/system-inventory.md section 2.
 *
 * These represent the physical equipment present in the system and their
 * importance for operation. MCU is authoritative for enforcement; app uses
 * these for UI gating.
 */
data class SubsystemCapabilities(
    val pid1: CapabilityLevel,       // Bit 0: Axle bearings PID
    val pid2: CapabilityLevel,       // Bit 1: Orbital bearings PID
    val pid3: CapabilityLevel,       // Bit 2: LN2 line PID
    val ln2Valve: CapabilityLevel,   // Bit 3: LN2 solenoid valve
    val doorActuator: CapabilityLevel, // Bit 4: Door locking bar
    val doorSwitch: CapabilityLevel,   // Bit 5: Door position sensor
    val relayInternal: CapabilityLevel, // Bit 6: Onboard 8-ch relays
    val relayExternal: CapabilityLevel  // Bit 7: External RS-485 relay bank
) {
    /**
     * Check if all required subsystems are operational.
     * Returns a list of missing required subsystems, or empty if all OK.
     */
    fun getMissingRequiredSubsystems(
        pid1Status: SubsystemStatus = SubsystemStatus.OK,
        pid2Status: SubsystemStatus = SubsystemStatus.OK,
        pid3Status: SubsystemStatus = SubsystemStatus.OK
    ): List<String> {
        val missing = mutableListOf<String>()

        if (pid1 == CapabilityLevel.REQUIRED && pid1Status != SubsystemStatus.OK) {
            missing.add("PID 1 (Axle bearings)")
        }
        if (pid2 == CapabilityLevel.REQUIRED && pid2Status != SubsystemStatus.OK) {
            missing.add("PID 2 (Orbital bearings)")
        }
        if (pid3 == CapabilityLevel.REQUIRED && pid3Status != SubsystemStatus.OK) {
            missing.add("PID 3 (LN2 line)")
        }

        return missing
    }

    /**
     * Check if any required capability is missing or faulted.
     */
    fun hasBlockingIssue(pidStatuses: Map<Int, SubsystemStatus>): Boolean {
        if (pid1 == CapabilityLevel.REQUIRED && pidStatuses[1] != SubsystemStatus.OK) return true
        if (pid2 == CapabilityLevel.REQUIRED && pidStatuses[2] != SubsystemStatus.OK) return true
        if (pid3 == CapabilityLevel.REQUIRED && pidStatuses[3] != SubsystemStatus.OK) return true
        return false
    }

    /**
     * Get the capability level for a specific PID.
     */
    fun getPidCapability(controllerId: Int): CapabilityLevel = when (controllerId) {
        1 -> pid1
        2 -> pid2
        3 -> pid3
        else -> CapabilityLevel.NOT_PRESENT
    }

    companion object {
        /**
         * Default capabilities: PID 1 & 2 required, PID 3 optional, others as equipped.
         */
        val DEFAULT = SubsystemCapabilities(
            pid1 = CapabilityLevel.REQUIRED,
            pid2 = CapabilityLevel.REQUIRED,
            pid3 = CapabilityLevel.OPTIONAL,
            ln2Valve = CapabilityLevel.OPTIONAL,
            doorActuator = CapabilityLevel.REQUIRED,
            doorSwitch = CapabilityLevel.REQUIRED,
            relayInternal = CapabilityLevel.REQUIRED,
            relayExternal = CapabilityLevel.NOT_PRESENT
        )

        /**
         * Parse from capability bits (8-bit value).
         * Each 2-bit pair represents one subsystem (bits 0-1 = PID1, etc.)
         * For simplicity, this version uses single bits with default levels.
         */
        fun fromBits(bits: Int): SubsystemCapabilities {
            // Simple bit presence -> REQUIRED, absence -> NOT_PRESENT
            // In real implementation, MCU would send full capability levels
            return SubsystemCapabilities(
                pid1 = if (bits and (1 shl 0) != 0) CapabilityLevel.REQUIRED else CapabilityLevel.NOT_PRESENT,
                pid2 = if (bits and (1 shl 1) != 0) CapabilityLevel.REQUIRED else CapabilityLevel.NOT_PRESENT,
                pid3 = if (bits and (1 shl 2) != 0) CapabilityLevel.OPTIONAL else CapabilityLevel.NOT_PRESENT,
                ln2Valve = if (bits and (1 shl 3) != 0) CapabilityLevel.OPTIONAL else CapabilityLevel.NOT_PRESENT,
                doorActuator = if (bits and (1 shl 4) != 0) CapabilityLevel.REQUIRED else CapabilityLevel.NOT_PRESENT,
                doorSwitch = if (bits and (1 shl 5) != 0) CapabilityLevel.REQUIRED else CapabilityLevel.NOT_PRESENT,
                relayInternal = if (bits and (1 shl 6) != 0) CapabilityLevel.REQUIRED else CapabilityLevel.NOT_PRESENT,
                relayExternal = if (bits and (1 shl 7) != 0) CapabilityLevel.OPTIONAL else CapabilityLevel.NOT_PRESENT
            )
        }
    }
}

/**
 * Runtime status of a subsystem (separate from its capability level).
 */
enum class SubsystemStatus {
    OK,           // Subsystem is present and communicating
    OFFLINE,      // Subsystem is expected but not responding
    FAULT;        // Subsystem has reported a fault

    val displayName: String
        get() = when (this) {
            OK -> "OK"
            OFFLINE -> "Offline"
            FAULT -> "Fault"
        }
}

/**
 * Result of capability check for start gating.
 */
data class StartGatingResult(
    val canStart: Boolean,
    val reason: String?
) {
    companion object {
        val OK = StartGatingResult(canStart = true, reason = null)

        fun blocked(reason: String) = StartGatingResult(canStart = false, reason = reason)
    }
}
