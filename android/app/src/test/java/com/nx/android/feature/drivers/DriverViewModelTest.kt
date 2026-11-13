package com.nx.android.feature.drivers

import androidx.lifecycle.ViewModelStore
import com.nx.android.domain.drivers.Driver
import com.nx.android.domain.drivers.DriverRelease
import com.nx.android.domain.drivers.DriverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DriverViewModelTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `download failure is exposed as a recoverable page message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeDriverRepository()
        val viewModel = DriverViewModel(repository)
        val viewModelStore = ViewModelStore().apply { put("drivers", viewModel) }
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        try {
            runCurrent()

            viewModel.download(release)
            advanceUntilIdle()

            val message = viewModel.uiState.value.message
            assertTrue(message is DriverOperationMessage.Failed)
            assertEquals("download failed", (message as DriverOperationMessage.Failed).cause.message)
            assertEquals(null, viewModel.uiState.value.workingFile)
        } finally {
            collector.cancel()
            runCurrent()
            viewModelStore.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private class FakeDriverRepository : DriverRepository {
        private val mutableDrivers = MutableStateFlow(emptyList<Driver>())
        override val drivers: StateFlow<List<Driver>> = mutableDrivers
        override val releases = listOf(release)
        override val shippedRelease = release
        override suspend fun refresh(): List<Driver> = drivers.value
        override suspend fun ensureShipped(): Boolean = true
        override suspend fun builtIn(): Driver? = null
        override suspend fun install(file: File): Result<Driver> = Result.failure(UnsupportedOperationException())
        override suspend fun download(release: DriverRelease, onProgress: (Float) -> Unit): Result<Driver> {
            onProgress(0.5f)
            return Result.failure(IllegalStateException("download failed"))
        }
        override suspend fun remove(id: String): Boolean = false
        override suspend fun shippedArchivePresent(): Boolean = false
    }

    private companion object {
        val release = DriverRelease("gpu.zip", "GPU", "GPU", com.nx.android.domain.drivers.DriverNote.GPU_8XX, "https://example.invalid/gpu.zip")
    }
}
