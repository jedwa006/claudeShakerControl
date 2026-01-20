package com.shakercontrol.app.data.repository

import com.shakercontrol.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for machine state and control.
 * Abstracts BLE communication for testability.
 */
interface MachineRepository {
    /**
     * Current system status including connection state, machine state, heartbeats.
     */
    val systemStatus: StateFlow<SystemStatus>

    /**
     * PID controller data for all 3 controllers.
     */
    val pidData: StateFlow<List<PidData>>

    /**
     * Current recipe configuration.
     */
    val recipe: StateFlow<Recipe>

    /**
     * Run progress when machine is running/paused.
     */
    val runProgress: StateFlow<RunProgress?>

    /**
     * Current alarms.
     */
    val alarms: StateFlow<List<Alarm>>

    /**
     * I/O status (digital inputs and relay outputs).
     */
    val ioStatus: StateFlow<IoStatus>

    /**
     * Interlock status for display.
     */
    val interlockStatus: StateFlow<InterlockStatus>

    // Connection commands
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(deviceAddress: String)
    suspend fun disconnect()
    suspend fun forgetDevice()

    // Run control commands
    suspend fun startRun(): Result<Unit>
    suspend fun pauseRun(): Result<Unit>
    suspend fun resumeRun(): Result<Unit>
    suspend fun stopRun(): Result<Unit>

    // Recipe commands
    suspend fun updateRecipe(recipe: Recipe)

    // Service mode
    suspend fun enableServiceMode()
    suspend fun disableServiceMode()

    // PID control commands
    suspend fun setSetpoint(controllerId: Int, setpoint: Float): Result<Unit>
    suspend fun setMode(controllerId: Int, mode: PidMode): Result<Unit>
    suspend fun requestPvSvRefresh(controllerId: Int): Result<Unit>
    suspend fun setPidParams(controllerId: Int, pGain: Float, iTime: Int, dTime: Int): Result<Unit>
    suspend fun readPidParams(controllerId: Int): Result<PidParams>
    suspend fun startAutotune(controllerId: Int): Result<Unit>
    suspend fun stopAutotune(controllerId: Int): Result<Unit>
    suspend fun setAlarmLimits(controllerId: Int, alarm1: Float, alarm2: Float): Result<Unit>
    suspend fun readAlarmLimits(controllerId: Int): Result<AlarmLimits>

    // Alarm commands
    suspend fun acknowledgeAlarm(alarmId: String): Result<Unit>
    suspend fun clearLatchedAlarms(): Result<Unit>

    // I/O commands
    suspend fun setRelay(channel: Int, on: Boolean): Result<Unit>

    /**
     * Atomically set multiple relays in a single operation.
     * @param mask Bitmask of which channels to affect (bit 0 = RO1, ..., bit 7 = RO8)
     * @param values Target state for each channel (0=OFF, 1=ON for each bit)
     * Only channels with their mask bit set will be modified.
     */
    suspend fun setRelayMask(mask: Int, values: Int): Result<Unit>

    // Simulation mode for testing (service mode only)
    val isSimulationEnabled: StateFlow<Boolean>
    suspend fun setSimulationEnabled(enabled: Boolean)
    suspend fun setSimulatedInput(channel: Int, high: Boolean)

    // Capability overrides (service mode only)
    // When non-null, overrides the default capability configuration
    val capabilityOverrides: StateFlow<SubsystemCapabilities?>
    suspend fun setCapabilityOverride(subsystem: String, level: CapabilityLevel)
    suspend fun clearCapabilityOverrides()

    // Generic Modbus register access (for register editor)
    /**
     * Read one or more consecutive registers from a PID controller.
     * @param controllerId Controller address (1-3)
     * @param startAddress Starting register address
     * @param count Number of registers to read (1-16)
     * @return List of register values (as raw u16)
     */
    suspend fun readRegisters(controllerId: Int, startAddress: Int, count: Int): Result<List<Int>>

    /**
     * Write a single register to a PID controller.
     * @param controllerId Controller address (1-3)
     * @param address Register address
     * @param value Value to write (as raw u16)
     */
    suspend fun writeRegister(controllerId: Int, address: Int, value: Int): Result<Unit>

    // Lazy polling configuration
    /**
     * MCU's current idle timeout setting.
     * Synced from MCU on connection. null if not yet synced or disconnected.
     * UI should observe this to display the actual MCU value.
     */
    val mcuIdleTimeoutMinutes: StateFlow<Int?>

    /**
     * Set the idle timeout for lazy polling on the MCU.
     * @param minutes Timeout in minutes (0=disabled, 1-255=enabled)
     * When idle for this duration, the MCU reduces RS-485 polling rate.
     */
    suspend fun setIdleTimeout(minutes: Int): Result<Unit>

    /**
     * Get the current idle timeout setting from the MCU.
     * @return Current timeout in minutes (0=disabled)
     */
    suspend fun getIdleTimeout(): Result<Int>
}
