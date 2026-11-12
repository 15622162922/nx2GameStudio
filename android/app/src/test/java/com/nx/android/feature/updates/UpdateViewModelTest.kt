package com.nx.android.feature.updates

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.domain.UpdateController
import com.nx.android.domain.UpdatePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before fun setMainDispatcher() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `fake update controller state is projected and actions are forwarded`() = runTest(dispatcher) {
        val controller = FakeUpdateController()
        controller.phase = UpdatePhase.AVAILABLE
        controller.latest = "0.4.0-alpha01"
        controller.mine = "0.3.0-alpha06"
        controller.notes = "Release notes"
        val viewModel = UpdateViewModel(controller)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(UpdatePhase.AVAILABLE, viewModel.uiState.value.phase)
        assertEquals("0.4.0-alpha01", viewModel.uiState.value.latest)
        assertEquals("0.3.0-alpha06", viewModel.uiState.value.mine)
        assertEquals("Release notes", viewModel.uiState.value.notes)

        viewModel.checkNow()
        viewModel.postpone()
        assertEquals(1, controller.checkRequests)
        assertEquals(true, controller.later)
    }

    private class FakeUpdateController : UpdateController {
        override var phase by mutableStateOf(UpdatePhase.IDLE)
        override var latest by mutableStateOf("")
        override var mine by mutableStateOf("")
        override var notes by mutableStateOf("")
        override var later by mutableStateOf(false)
        var checkRequests = 0

        override fun checkNow() { checkRequests++ }
        override fun postpone() { later = true }
    }
}
