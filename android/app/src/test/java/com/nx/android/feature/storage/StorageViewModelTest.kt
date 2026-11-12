package com.nx.android.feature.storage

import com.nx.android.domain.StorageUsage
import com.nx.android.domain.StorageUsageController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before fun setMainDispatcher() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `visible for less than gate does not start a scan`() = runTest(dispatcher) {
        val storage = FakeStorageUsageController()
        val viewModel = StorageViewModel(storage)

        viewModel.setVisible(true)
        runCurrent()
        advanceTimeBy(StorageUsage.SCAN_GATE_MS - 1)
        runCurrent()

        assertEquals(0, storage.scans)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, storage.scans)
    }

    @Test
    fun `leaving the section cancels the pending scan`() = runTest(dispatcher) {
        val storage = FakeStorageUsageController()
        val viewModel = StorageViewModel(storage)

        viewModel.setVisible(true)
        runCurrent()
        viewModel.setVisible(false)
        runCurrent()
        advanceTimeBy(StorageUsage.SCAN_GATE_MS)
        runCurrent()

        assertEquals(0, storage.scans)
        assertEquals(1, storage.preparations)
    }

    @Test
    fun `backgrounding cancels the pending scan`() = runTest(dispatcher) {
        val storage = FakeStorageUsageController()
        val viewModel = StorageViewModel(storage)

        viewModel.setVisible(true)
        runCurrent()
        viewModel.setForeground(false)
        runCurrent()
        advanceTimeBy(StorageUsage.SCAN_GATE_MS)
        runCurrent()

        assertEquals(0, storage.scans)
    }

    private class FakeStorageUsageController : StorageUsageController {
        private var state = StorageUsage.Snapshot()
        override val snapshot: StorageUsage.Snapshot get() = state
        var preparations = 0
        var scans = 0

        override fun load() = Unit
        override suspend fun prepare() { preparations++ }
        override suspend fun scan(force: Boolean) { scans++; state = state.copy(scanning = false) }
    }
}
