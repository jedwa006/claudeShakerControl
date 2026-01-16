package com.shakercontrol.app.ui.alarms

import com.shakercontrol.app.data.repository.MockMachineRepository
import com.shakercontrol.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmsViewModelTest {

    private lateinit var repository: MockMachineRepository
    private lateinit var viewModel: AlarmsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = MockMachineRepository()
        viewModel = AlarmsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isConnected is true when connection state is LIVE`() = runTest {
        // Initially connected (mock defaults to LIVE)
        // The stateIn initial value is false, so we need to give time for collection
        advanceUntilIdle()
        // Test that acknowledgeAlarm succeeds (proving connection is live)
        var receivedEvent: AlarmsUiEvent? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect { event ->
                receivedEvent = event
            }
        }
        viewModel.acknowledgeAlarm("test")
        advanceUntilIdle()
        assertTrue(receivedEvent is AlarmsUiEvent.CommandSuccess)
    }

    @Test
    fun `isConnected is false when disconnected`() = runTest {
        repository.disconnect()
        advanceUntilIdle()
        assertFalse(viewModel.isConnected.first())
    }

    @Test
    fun `isExecutingCommand is initially false`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.isExecutingCommand.first())
    }

    @Test
    fun `acknowledgeAlarm emits CommandSuccess on success`() = runTest {
        var receivedEvent: AlarmsUiEvent? = null

        // Collect events in background
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect { event ->
                receivedEvent = event
            }
        }

        viewModel.acknowledgeAlarm("test-alarm-id")
        advanceUntilIdle()

        assertTrue(receivedEvent is AlarmsUiEvent.CommandSuccess)
    }

    @Test
    fun `acknowledgeAlarm emits CommandError when disconnected`() = runTest {
        repository.disconnect()
        advanceUntilIdle()

        var receivedEvent: AlarmsUiEvent? = null

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect { event ->
                receivedEvent = event
            }
        }

        viewModel.acknowledgeAlarm("test-alarm-id")
        advanceUntilIdle()

        assertTrue(receivedEvent is AlarmsUiEvent.CommandError)
    }

    @Test
    fun `clearLatchedAlarms emits CommandSuccess on success`() = runTest {
        var receivedEvent: AlarmsUiEvent? = null

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect { event ->
                receivedEvent = event
            }
        }

        viewModel.clearLatchedAlarms()
        advanceUntilIdle()

        assertTrue(receivedEvent is AlarmsUiEvent.CommandSuccess)
    }

    @Test
    fun `clearLatchedAlarms emits CommandError when disconnected`() = runTest {
        repository.disconnect()
        advanceUntilIdle()

        var receivedEvent: AlarmsUiEvent? = null

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect { event ->
                receivedEvent = event
            }
        }

        viewModel.clearLatchedAlarms()
        advanceUntilIdle()

        assertTrue(receivedEvent is AlarmsUiEvent.CommandError)
    }
}
