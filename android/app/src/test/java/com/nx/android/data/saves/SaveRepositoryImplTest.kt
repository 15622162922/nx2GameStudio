package com.nx.android.data.saves

import com.nx.android.data.SaveBackup
import com.nx.android.domain.saves.SaveOperationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SaveRepositoryImplTest {
    @Test
    fun `import backs up current save before installing package`() = runBlocking {
        val fixture = Fixture()
        try {
            val live = SaveBackup.liveDir(fixture.platform.filesDir, TITLE_ID)
            live.mkdirs()
            File(live, "slot.dat").writeText("current")
            val packageFile = fixture.packageFor(TITLE_ID, "slot.dat" to "imported")

            val result = fixture.repository.import(TITLE_ID, packageFile, fixture.packSaves)

            assertTrue(result.isSuccess)
            assertEquals("imported", File(live, "slot.dat").readText())
            assertEquals("current", File(fixture.packSaves, result.getOrThrow()!!).resolve("slot.dat").readText())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `wrong title and traversal packages are rejected without changing live save`() = runBlocking {
        val fixture = Fixture()
        try {
            val live = SaveBackup.liveDir(fixture.platform.filesDir, TITLE_ID)
            live.mkdirs()
            File(live, "slot.dat").writeText("keep")
            val wrongTitle = fixture.packageFor(OTHER_TITLE_ID, "slot.dat" to "wrong")
            val wrongResult = fixture.repository.import(TITLE_ID, wrongTitle, fixture.packSaves)
            assertTrue(wrongResult.exceptionOrNull() is SaveOperationException)
            assertEquals(SaveOperationException.Reason.TITLE_MISMATCH,
                (wrongResult.exceptionOrNull() as SaveOperationException).reason)

            val traversal = fixture.root.resolve("Downloads/escape.zip")
            traversal.parentFile?.mkdirs()
            ZipOutputStream(traversal.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("tree/../../escape.txt"))
                zip.write("bad".toByteArray())
                zip.closeEntry()
            }
            val traversalResult = fixture.repository.import(TITLE_ID, traversal, fixture.packSaves)
            assertTrue(traversalResult.exceptionOrNull() is SaveBackup.Refused)
            assertFalse(fixture.root.resolve("Downloads/escape.txt").exists())
            assertEquals("keep", File(live, "slot.dat").readText())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `running game and invalid title id prevent save access`() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.platform.running = true
            val running = fixture.repository.export(TITLE_ID, "game", fixture.packSaves)
            assertEquals(SaveOperationException.Reason.GAME_RUNNING,
                (running.exceptionOrNull() as SaveOperationException).reason)
            fixture.platform.running = false
            val invalid = fixture.repository.export("../escape", "game", fixture.packSaves)
            assertEquals(SaveOperationException.Reason.INVALID_TITLE_ID,
                (invalid.exceptionOrNull() as SaveOperationException).reason)
            assertTrue(fixture.repository.listPackages().isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `restore only accepts a backup inside this pack save directory`() = runBlocking {
        val fixture = Fixture()
        try {
            val external = fixture.root.resolve("outside/backup")
            external.mkdirs()
            File(external, "slot.dat").writeText("outside")

            val result = fixture.repository.restore(TITLE_ID, external, fixture.packSaves)

            assertEquals(SaveOperationException.Reason.INVALID_BACKUP,
                (result.exceptionOrNull() as SaveOperationException).reason)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `cancellation during replacement waits for the save swap to finish`() = runBlocking {
        val fixture = Fixture()
        try {
            val live = SaveBackup.liveDir(fixture.platform.filesDir, TITLE_ID)
            live.mkdirs()
            File(live, "slot.dat").writeText("current")
            val packageFile = fixture.packageFor(TITLE_ID, "slot.dat" to "imported")
            val enteredSwap = CompletableDeferred<Unit>()
            val finishSwap = CompletableDeferred<Unit>()
            val swap = object : SaveSwap {
                override suspend fun install(tree: File, device: File?, live: File, liveDevice: File) {
                    enteredSwap.complete(Unit)
                    finishSwap.await()
                    SaveBackup.swapIn(tree, device, live, liveDevice)
                }
            }
            val repository = SaveRepositoryImpl(fixture.platform, Dispatchers.Unconfined, swap)
            val job = async { repository.import(TITLE_ID, packageFile, fixture.packSaves) }

            enteredSwap.await()
            job.cancel()
            finishSwap.complete(Unit)
            job.join()

            assertTrue(job.isCancelled)
            assertEquals("imported", File(live, "slot.dat").readText())
            assertTrue(fixture.packSaves.listFiles().orEmpty().any { it.name.endsWith("-before-import") })
        } finally {
            fixture.close()
        }
    }

    private class Fixture : AutoCloseable {
        val root = Files.createTempDirectory("nx2a-save-repo").toFile()
        val platform = FakeSavePlatform(root)
        val packSaves = root.resolve("pack/saves")
        val repository = SaveRepositoryImpl(platform, Dispatchers.Unconfined, FileSaveSwap())

        fun packageFor(titleId: String, entry: Pair<String, String>): File {
            val tree = root.resolve("source-${titleId}")
            tree.mkdirs()
            File(tree, entry.first).writeText(entry.second)
            val dest = File(platform.packagesDirectory(), "game-$titleId-20261112-120000.zip")
            SaveBackup.writePackage(tree, null, tree.name, dest)
            return dest
        }

        override fun close() { root.deleteRecursively() }
    }

    private class FakeSavePlatform(private val root: File) : SavePlatform {
        override val filesDir: File = File(root, "files")
        override val cacheDir: File = File(root, "cache")
        private val packages = File(root, "Downloads/nx2android")
        var running = false
        override fun packagesDirectory(): File = packages.apply { mkdirs() }
        override fun isGameRunning(): Boolean = running
    }

    private companion object {
        const val TITLE_ID = "0100000000010000"
        const val OTHER_TITLE_ID = "0100000000018000"
    }
}
