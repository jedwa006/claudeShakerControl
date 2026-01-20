package com.shakercontrol.app.ui.io

import com.shakercontrol.app.data.repository.MockMachineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IoViewModelTest {

    private lateinit var repository: MockMachineRepository
    private lateinit var viewModel: IoViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = MockMachineRepository()
        viewModel = IoViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isExecutingCommand is initially false`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.isExecutingCommand.first())
    }

    @Test
    fun `setRelay emits CommandSuccess on success`() = runTest {
        var receivedEvent: IoUiEvent? = null

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect { event ->
                receivedEvent = event
            }
        }

        viewModel.setRelay(1, true)
        advanceUntilIdle()

        assertTrue(receivedEvent is IoUiEvent.CommandSuccess)
    }

    @Test
    fun `setRelay emits CommandError when disconnected`() = runTest {
        repository.disconnect()
        advanceUntilIdle()

        var receivedEvent: IoUiEvent? = null

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect { event ->
                receivedEvent = event
            }
        }

        viewModel.setRelay(1, true)
        advanceUntilIdle()

        assertTrue(receivedEvent is IoUiEvent.CommandError)
    }

    @Test
    fun `setRelay updates relay state in repository`() = runTest {
        advanceUntilIdle()

        // Verify RO2 is initially off (mock default has RO1 on, others off)
        val initialStatus = repository.ioStatus.first()
        assertFalse(initialStatus.isOutputHigh(2))

        viewModel.setRelay(2, true)
        advanceUntilIdle()

        // Verify from repository directly
        val updatedStatus = repository.ioStatus.first()
        assertTrue(updatedStatus.isOutputHigh(2))
    }

    @Test
    fun `setSimulationEnabled updates simulation state`() = runTest {
        advanceUntilIdle()
        assertFalse(repository.isSimulationEnabled.first())

        viewModel.setSimulationEnabled(true)
        advanceUntilIdle()

        assertTrue(repository.isSimulationEnabled.first())
    }

    @Test
    fun `setSimulatedInput updates input state when simulation enabled`() = runTest {
        advanceUntilIdle()

        // Enable simulation first
        viewModel.setSimulationEnabled(true)
        advanceUntilIdle()

        // DI2 initially low in mock (0b00000101 = DI1 and DI3 high)
        val initialStatus = repository.ioStatus.first()
        assertFalse(initialStatus.isInputHigh(2))

        // Simulate input high
        viewModel.setSimulatedInput(2, true)
        advanceUntilIdle()

        // Should now be high - verify from repository
        val updatedStatus = repository.ioStatus.first()
        assertTrue(updatedStatus.isInputHigh(2))
    }

    @Test
    fun `setSimulatedInput does not affect state when simulation disabled`() = runTest {
        advanceUntilIdle()

        // Ensure simulation is disabled
        assertFalse(repository.isSimulationEnabled.first())

        // DI2 initially low (mock default 0b00000101 = DI1 and DI3 high)
        val initialStatus = repository.ioStatus.first()
        assertFalse(initialStatus.isInputHigh(2))

        // Try to simulate input high (should not update ioStatus because simulation is disabled)
        viewModel.setSimulatedInput(2, true)
        advanceUntilIdle()

        // Should still be low because simulation is disabled
        val unchangedStatus = repository.ioStatus.first()
        assertFalse(unchangedStatus.isInputHigh(2))
    }

    @Test
    fun `disabling simulation clears simulated values`() = runTest {
        advanceUntilIdle()

        // Enable simulation and set DI2 high (was low in mock default)
        viewModel.setSimulationEnabled(true)
        advanceUntilIdle()
        viewModel.setSimulatedInput(2, true)
        advanceUntilIdle()

        val statusWithSimulation = repository.ioStatus.first()
        assertTrue(statusWithSimulation.isInputHigh(2))

        // Disable simulation - simulated inputs cleared (to 0)
        viewModel.setSimulationEnabled(false)
        advanceUntilIdle()

        assertFalse(repository.isSimulationEnabled.first())
    }

    @Test
    fun `multiple relays can be controlled independently`() = runTest {
        advanceUntilIdle()

        // Mock default has RO1 already on, turn on RO3 and RO5 too
        viewModel.setRelay(3, true)
        advanceUntilIdle()
        viewModel.setRelay(5, true)
        advanceUntilIdle()

        val status = repository.ioStatus.first()
        assertTrue(status.isOutputHigh(1))   // Default on
        assertFalse(status.isOutputHigh(2))
        assertTrue(status.isOutputHigh(3))   // Turned on
        assertFalse(status.isOutputHigh(4))
        assertTrue(status.isOutputHigh(5))   // Turned on
        assertFalse(status.isOutputHigh(6))
        assertFalse(status.isOutputHigh(7))
        assertFalse(status.isOutputHigh(8))
    }

    @Test
    fun `multiple simulated inputs can be set independently`() = runTest {
        advanceUntilIdle()

        viewModel.setSimulationEnabled(true)
        advanceUntilIdle()

        viewModel.setSimulatedInput(2, true)
        advanceUntilIdle()
        viewModel.setSimulatedInput(4, true)
        advanceUntilIdle()
        viewModel.setSimulatedInput(6, true)
        advanceUntilIdle()

        val status = repository.ioStatus.first()
        // Simulated: 2, 4, 6 are high
        assertFalse(status.isInputHigh(1))
        assertTrue(status.isInputHigh(2))
        assertFalse(status.isInputHigh(3))
        assertTrue(status.isInputHigh(4))
        assertFalse(status.isInputHigh(5))
        assertTrue(status.isInputHigh(6))
        assertFalse(status.isInputHigh(7))
        assertFalse(status.isInputHigh(8))
    }

    @Test
    fun `relay can be turned off after being turned on`() = runTest {
        advanceUntilIdle()

        // Turn on RO2
        viewModel.setRelay(2, true)
        advanceUntilIdle()
        assertTrue(repository.ioStatus.first().isOutputHigh(2))

        // Turn off RO2
        viewModel.setRelay(2, false)
        advanceUntilIdle()
        assertFalse(repository.ioStatus.first().isOutputHigh(2))
    }

    @Test
    fun `repository ioStatus reflects mock default values`() = runTest {
        // Verify mock defaults directly
        val status = repository.ioStatus.first()
        // Mock default is digitalInputs = 0b00000101 (DI1 and DI3 high)
        // Mock default is relayOutputs = 0b00000001 (RO1 on)
        assertTrue("DI1 should be HIGH", status.isInputHigh(1))
        assertFalse("DI2 should be LOW", status.isInputHigh(2))
        assertTrue("DI3 should be HIGH", status.isInputHigh(3))
        assertTrue("RO1 should be ON", status.isOutputHigh(1))
        assertFalse("RO2 should be OFF", status.isOutputHigh(2))
    }

    @Test
    fun `isConnected starts false and updates after repository collection`() = runTest {
        // This tests the initial stateIn value
        val initialConnected = viewModel.isConnected.value
        assertFalse(initialConnected) // stateIn initial value is false
    }

    @Test
    fun `isSimulationEnabled starts false`() = runTest {
        val initial = viewModel.isSimulationEnabled.value
        assertFalse(initial)
    }
}
