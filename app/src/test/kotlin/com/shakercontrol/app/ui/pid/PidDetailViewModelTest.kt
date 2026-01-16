package com.shakercontrol.app.ui.pid

import com.shakercontrol.app.data.repository.MockMachineRepository
import com.shakercontrol.app.domain.model.PidMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PidDetailViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PidDetailViewModelTest {

    private lateinit var repository: MockMachineRepository
    private lateinit var viewModel: PidDetailViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = MockMachineRepository()
        viewModel = PidDetailViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `getPidData returns correct PID for ID 1`() = runTest {
        val pidData = viewModel.getPidData(1).first()

        assertNotNull(pidData)
        assertEquals(1, pidData!!.controllerId)
        assertEquals("Axle bearings", pidData.name)
    }

    @Test
    fun `getPidData returns correct PID for ID 2`() = runTest {
        val pidData = viewModel.getPidData(2).first()

        assertNotNull(pidData)
        assertEquals(2, pidData!!.controllerId)
        assertEquals("Orbital bearings", pidData.name)
    }

    @Test
    fun `getPidData returns correct PID for ID 3`() = runTest {
        val pidData = viewModel.getPidData(3).first()

        assertNotNull(pidData)
        assertEquals(3, pidData!!.controllerId)
        assertEquals("LN2 line", pidData.name)
    }

    @Test
    fun `getPidData returns null for invalid ID`() = runTest {
        val pidData = viewModel.getPidData(999).first()

        assertNull(pidData)
    }

    @Test
    fun `isExecutingCommand is initially false`() = runTest {
        val isExecuting = viewModel.isExecutingCommand.first()

        assertFalse(isExecuting)
    }

    @Test
    fun `setSetpoint updates PID value`() = runTest {
        viewModel.setSetpoint(1, 50.0f)

        // Advance coroutines to complete the command
        advanceUntilIdle()

        val pidData = viewModel.getPidData(1).first()
        assertEquals(50.0f, pidData!!.setpointValue, 0.01f)
    }

    @Test
    fun `setMode updates PID mode`() = runTest {
        viewModel.setMode(1, PidMode.MANUAL)

        advanceUntilIdle()

        val pidData = viewModel.getPidData(1).first()
        assertEquals(PidMode.MANUAL, pidData!!.mode)
    }

    @Test
    fun `setMode to STOP disables PID`() = runTest {
        viewModel.setMode(1, PidMode.STOP)

        advanceUntilIdle()

        val pidData = viewModel.getPidData(1).first()
        assertEquals(PidMode.STOP, pidData!!.mode)
        assertFalse(pidData.isEnabled)
    }

    @Test
    fun `setSetpoint for different PIDs is independent`() = runTest {
        viewModel.setSetpoint(1, 40.0f)
        viewModel.setSetpoint(2, 45.0f)
        viewModel.setSetpoint(3, -180.0f)

        advanceUntilIdle()

        val pid1 = viewModel.getPidData(1).first()
        val pid2 = viewModel.getPidData(2).first()
        val pid3 = viewModel.getPidData(3).first()

        assertEquals(40.0f, pid1!!.setpointValue, 0.01f)
        assertEquals(45.0f, pid2!!.setpointValue, 0.01f)
        assertEquals(-180.0f, pid3!!.setpointValue, 0.01f)
    }

    @Test
    fun `setMode for different PIDs is independent`() = runTest {
        viewModel.setMode(1, PidMode.STOP)
        viewModel.setMode(2, PidMode.MANUAL)
        viewModel.setMode(3, PidMode.AUTO)

        advanceUntilIdle()

        val pid1 = viewModel.getPidData(1).first()
        val pid2 = viewModel.getPidData(2).first()
        val pid3 = viewModel.getPidData(3).first()

        assertEquals(PidMode.STOP, pid1!!.mode)
        assertEquals(PidMode.MANUAL, pid2!!.mode)
        assertEquals(PidMode.AUTO, pid3!!.mode)
    }

    @Test
    fun `connectionState initial value is DISCONNECTED before flow collects`() = runTest {
        // The stateIn flow has initialValue = DISCONNECTED
        // It will become LIVE once the flow starts collecting
        // This test verifies the initial state is set correctly
        val connectionState = viewModel.connectionState.value

        // Initial value before viewModelScope starts collecting
        assertEquals(
            com.shakercontrol.app.domain.model.ConnectionState.DISCONNECTED,
            connectionState
        )
    }
}
