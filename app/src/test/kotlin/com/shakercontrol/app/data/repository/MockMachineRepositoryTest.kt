package com.shakercontrol.app.data.repository

import com.shakercontrol.app.domain.model.ConnectionState
import com.shakercontrol.app.domain.model.PidMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MockMachineRepository PID functionality.
 */
class MockMachineRepositoryTest {

    private lateinit var repository: MockMachineRepository

    @Before
    fun setup() {
        repository = MockMachineRepository()
    }

    @Test
    fun `initial state has three PID controllers`() = runTest {
        val pidData = repository.pidData.first()

        assertEquals(3, pidData.size)
        assertEquals(1, pidData[0].controllerId)
        assertEquals(2, pidData[1].controllerId)
        assertEquals(3, pidData[2].controllerId)
    }

    @Test
    fun `initial PID 1 has correct name and values`() = runTest {
        val pidData = repository.pidData.first()
        val pid1 = pidData.find { it.controllerId == 1 }

        assertNotNull(pid1)
        assertEquals("Axle bearings", pid1!!.name)
        assertEquals(30.0f, pid1.setpointValue, 0.01f)
        assertEquals(PidMode.AUTO, pid1.mode)
        assertTrue(pid1.isEnabled)
    }

    @Test
    fun `setSetpoint updates PID setpoint value`() = runTest {
        val newSetpoint = 45.0f

        val result = repository.setSetpoint(1, newSetpoint)

        assertTrue(result.isSuccess)

        val pidData = repository.pidData.first()
        val pid1 = pidData.find { it.controllerId == 1 }
        assertEquals(newSetpoint, pid1!!.setpointValue, 0.01f)
    }

    @Test
    fun `setSetpoint for PID 2 updates correct controller`() = runTest {
        val newSetpoint = 35.5f

        val result = repository.setSetpoint(2, newSetpoint)

        assertTrue(result.isSuccess)

        val pidData = repository.pidData.first()
        val pid1 = pidData.find { it.controllerId == 1 }
        val pid2 = pidData.find { it.controllerId == 2 }

        // PID 1 should be unchanged
        assertEquals(30.0f, pid1!!.setpointValue, 0.01f)
        // PID 2 should be updated
        assertEquals(newSetpoint, pid2!!.setpointValue, 0.01f)
    }

    @Test
    fun `setSetpoint for PID 3 (LN2) works`() = runTest {
        val newSetpoint = -190.0f

        val result = repository.setSetpoint(3, newSetpoint)

        assertTrue(result.isSuccess)

        val pidData = repository.pidData.first()
        val pid3 = pidData.find { it.controllerId == 3 }
        assertEquals(newSetpoint, pid3!!.setpointValue, 0.01f)
    }

    @Test
    fun `setMode to STOP disables controller`() = runTest {
        val result = repository.setMode(1, PidMode.STOP)

        assertTrue(result.isSuccess)

        val pidData = repository.pidData.first()
        val pid1 = pidData.find { it.controllerId == 1 }
        assertEquals(PidMode.STOP, pid1!!.mode)
        assertFalse(pid1.isEnabled)
    }

    @Test
    fun `setMode to MANUAL enables controller`() = runTest {
        // First set to STOP
        repository.setMode(1, PidMode.STOP)

        // Then set to MANUAL
        val result = repository.setMode(1, PidMode.MANUAL)

        assertTrue(result.isSuccess)

        val pidData = repository.pidData.first()
        val pid1 = pidData.find { it.controllerId == 1 }
        assertEquals(PidMode.MANUAL, pid1!!.mode)
        assertTrue(pid1.isEnabled)
    }

    @Test
    fun `setMode to AUTO enables controller`() = runTest {
        // First set to STOP
        repository.setMode(1, PidMode.STOP)

        // Then set to AUTO
        val result = repository.setMode(1, PidMode.AUTO)

        assertTrue(result.isSuccess)

        val pidData = repository.pidData.first()
        val pid1 = pidData.find { it.controllerId == 1 }
        assertEquals(PidMode.AUTO, pid1!!.mode)
        assertTrue(pid1.isEnabled)
    }

    @Test
    fun `setMode for different controllers is independent`() = runTest {
        repository.setMode(1, PidMode.STOP)
        repository.setMode(2, PidMode.MANUAL)
        repository.setMode(3, PidMode.AUTO)

        val pidData = repository.pidData.first()

        assertEquals(PidMode.STOP, pidData.find { it.controllerId == 1 }!!.mode)
        assertEquals(PidMode.MANUAL, pidData.find { it.controllerId == 2 }!!.mode)
        assertEquals(PidMode.AUTO, pidData.find { it.controllerId == 3 }!!.mode)
    }

    @Test
    fun `setSetpoint with negative value works (LN2 temperatures)`() = runTest {
        val newSetpoint = -195.5f

        val result = repository.setSetpoint(3, newSetpoint)

        assertTrue(result.isSuccess)

        val pidData = repository.pidData.first()
        val pid3 = pidData.find { it.controllerId == 3 }
        assertEquals(newSetpoint, pid3!!.setpointValue, 0.01f)
    }

    @Test
    fun `setSetpoint with zero value works`() = runTest {
        val result = repository.setSetpoint(1, 0.0f)

        assertTrue(result.isSuccess)

        val pidData = repository.pidData.first()
        val pid1 = pidData.find { it.controllerId == 1 }
        assertEquals(0.0f, pid1!!.setpointValue, 0.01f)
    }

    @Test
    fun `multiple setSetpoint calls accumulate correctly`() = runTest {
        repository.setSetpoint(1, 25.0f)
        repository.setSetpoint(1, 30.0f)
        repository.setSetpoint(1, 35.0f)

        val pidData = repository.pidData.first()
        val pid1 = pidData.find { it.controllerId == 1 }
        assertEquals(35.0f, pid1!!.setpointValue, 0.01f)
    }

    @Test
    fun `connection state is initially LIVE`() = runTest {
        val status = repository.systemStatus.first()
        assertEquals(ConnectionState.LIVE, status.connectionState)
    }

    @Test
    fun `setSetpoint fails when disconnected`() = runTest {
        // Disconnect
        repository.disconnect()

        val status = repository.systemStatus.first()
        assertEquals(ConnectionState.DISCONNECTED, status.connectionState)

        val result = repository.setSetpoint(1, 50.0f)

        assertTrue(result.isFailure)
    }

    @Test
    fun `setMode fails when disconnected`() = runTest {
        // Disconnect
        repository.disconnect()

        val result = repository.setMode(1, PidMode.MANUAL)

        assertTrue(result.isFailure)
    }
}
