package com.nx.android.feature.gamedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.nx.android.domain.playback.RunOptionsRepository
import com.nx.android.domain.playback.RunOptionsSnapshot
import com.nx.android.domain.saves.SaveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class GameDetailViewModelTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `title keyed details keep per game options separate and clear on close`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val options = FakeRunOptionsRepository()
        val store = ViewModelStore()
        try {
            val first = GameDetailViewModel(SavedStateHandle(), FakeSaveRepository(), options)
            val second = GameDetailViewModel(SavedStateHandle(), FakeSaveRepository(), options)
            store.put("detail-one", first)
            store.put("detail-two", second)
            val firstPack = File("/games/one")
            val secondPack = File("/games/two")

            first.open(TITLE_ONE, firstPack)
            second.open(TITLE_TWO, secondPack)
            advanceUntilIdle()

            assertEquals(TITLE_ONE, first.uiState.value.titleId)
            assertEquals("/games/one", options.readPacks[0].path)
            assertEquals(TITLE_TWO, second.uiState.value.titleId)
            assertEquals("/games/two", options.readPacks[1].path)

            first.selectLanguage("ja")
            advanceUntilIdle()
            assertEquals("ja", first.uiState.value.runOptions?.language)
            assertNull(second.uiState.value.runOptions?.language)

            first.close(TITLE_ONE)
            assertNull(first.uiState.value.titleId)
            assertEquals(TITLE_TWO, second.uiState.value.titleId)
        } finally {
            store.clear()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    private class FakeRunOptionsRepository : RunOptionsRepository {
        val readPacks = mutableListOf<File>()
        private val values = mutableMapOf<String, RunOptionsSnapshot>()
        override suspend fun read(pack: File): RunOptionsSnapshot {
            readPacks += pack
            return values.getOrPut(pack.path) { RunOptionsSnapshot(language = null, driver = null) }
        }
        override suspend fun update(pack: File, language: String?, driver: String?): RunOptionsSnapshot =
            RunOptionsSnapshot(language = language, driver = driver).also { values[pack.path] = it }
    }

    private class FakeSaveRepository : SaveRepository {
        override fun titleIdInPackage(name: String): String? = null
        override fun packagesDirectory(): File = File("/packages")
        override suspend fun listPackages(): List<File> = emptyList()
        override suspend fun listBackups(packSaves: File): List<File> = emptyList()
        override suspend fun treeSize(file: File): Long = 0
        override suspend fun export(titleId: String, gameName: String, packSaves: File): Result<File> = Result.failure(UnsupportedOperationException())
        override suspend fun import(titleId: String, packageFile: File, packSaves: File): Result<String?> = Result.failure(UnsupportedOperationException())
        override suspend fun restore(titleId: String, backup: File, packSaves: File): Result<String?> = Result.failure(UnsupportedOperationException())
        override suspend fun prepareBackupShare(titleId: String, gameName: String, backup: File, packSaves: File): Result<File> = Result.failure(UnsupportedOperationException())
        override suspend fun delete(file: File, packSaves: File): Boolean = false
    }

    private companion object {
        const val TITLE_ONE = "0100000000010000"
        const val TITLE_TWO = "0100000000018000"
    }
}
