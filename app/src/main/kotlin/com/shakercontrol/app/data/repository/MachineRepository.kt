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

    // Alarm commands
    suspend fun acknowledgeAlarm(alarmId: String): Result<Unit>
    suspend fun clearLatchedAlarms(): Result<Unit>
}
