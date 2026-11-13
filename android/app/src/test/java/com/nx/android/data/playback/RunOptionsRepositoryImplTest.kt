package com.nx.android.data.playback

import com.nx.android.domain.drivers.Driver
import com.nx.android.domain.drivers.DriverRelease
import com.nx.android.domain.drivers.DriverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RunOptionsRepositoryImplTest {
    @Test
    fun `updates retain unrelated env rows and become visible on the next read`() = runBlocking {
        val root = Files.createTempDirectory("nx2a-run-options").toFile()
        try {
            val pack = File(root, "pack").apply { mkdirs() }
            File(pack, "languages.txt").writeText("en-US\tEnglish\nja\tJapanese\n")
            File(pack, "drivers.txt").writeText("included.so\nmissing.so\n")
            File(File(pack, "drivers"), "included.so").apply { parentFile?.mkdirs(); writeText("driver") }
            File(pack, "env.txt").writeText("# profile row\nDM_CUSTOM=kept\nDM_LANG=en-US\n")
            val repository = RunOptionsRepositoryImpl(Dispatchers.Unconfined, FakeDrivers(null))

            val loaded = repository.read(pack)
            val updated = repository.update(pack, "ja", "included.so")
            val after = repository.read(pack)

            assertEquals(listOf("en-US" to "English", "ja" to "Japanese"), loaded.languages)
            assertEquals(listOf("included.so"), loaded.packDrivers)
            assertEquals("ja", updated.language)
            assertEquals("included.so", updated.driver)
            assertEquals(updated, after)
            val env = File(pack, "env.txt").readText()
            assertTrue(env.contains("# profile row"))
            assertTrue(env.contains("DM_CUSTOM=kept"))
            assertTrue(env.contains("DM_LANG=ja"))
            assertTrue(env.contains("DM_VK_DRIVER=included.so"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a game whose driver was never picked runs on the built-in one`() = runBlocking {
        val root = Files.createTempDirectory("nx2a-run-options").toFile()
        try {
            val pack = File(root, "pack").apply { mkdirs() }
            File(pack, "env.txt").writeText("DM_ROOT=/sdcard/x\n")
            val builtIn = File(root, "drivers/Turnip_Gen8_V39/libvulkan_freedreno.so")
            val repository = RunOptionsRepositoryImpl(Dispatchers.Unconfined, FakeDrivers(builtIn))

            val snapshot = repository.read(pack)

            assertEquals(builtIn.absolutePath, snapshot.driver)
            val env = File(pack, "env.txt").readText()
            assertTrue(env.contains("DM_VK_DRIVER=" + builtIn.absolutePath))
            assertTrue(env.contains("DM_ROOT=/sdcard/x"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `the player's own choice is kept, the device's own driver included`() = runBlocking {
        val root = Files.createTempDirectory("nx2a-run-options").toFile()
        try {
            val pack = File(root, "pack").apply { mkdirs() }
            val builtIn = File(root, "drivers/Turnip_Gen8_V39/libvulkan_freedreno.so")
            val repository = RunOptionsRepositoryImpl(Dispatchers.Unconfined, FakeDrivers(builtIn))

            // the player picked the device's own driver: an empty row, not the absence of the row
            File(pack, "env.txt").writeText("DM_VK_DRIVER=\n")
            val kept = repository.read(pack)
            assertEquals("", kept.driver)
            assertTrue(File(pack, "env.txt").readText().contains("DM_VK_DRIVER="))
            assertTrue(!File(pack, "env.txt").readText().contains(builtIn.absolutePath))

            // the player picked another driver: that one is what the row says
            File(pack, "env.txt").writeText("DM_VK_DRIVER=/other/driver.so\n")
            val other = repository.read(pack)
            assertEquals("/other/driver.so", other.driver)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `no built-in driver leaves the pack alone`() = runBlocking {
        val root = Files.createTempDirectory("nx2a-run-options").toFile()
        try {
            val pack = File(root, "pack").apply { mkdirs() }
            val repository = RunOptionsRepositoryImpl(Dispatchers.Unconfined, FakeDrivers(null))

            val snapshot = repository.read(pack)

            assertNull(snapshot.driver)
            assertTrue(!File(pack, "env.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}

/** The drivers the app has: the built-in one (the driver it ships) or none. */
private class FakeDrivers(private val builtIn: File?) : DriverRepository {
    private val mutable = MutableStateFlow<List<Driver>>(emptyList())
    override val drivers: StateFlow<List<Driver>> = mutable
    override val releases: List<DriverRelease> = emptyList()
    override val shippedRelease: DriverRelease =
        DriverRelease("none.zip", "None", "None", com.nx.android.domain.drivers.DriverNote.GPU_8XX, "")
    override suspend fun refresh(): List<Driver> = mutable.value
    override suspend fun ensureShipped(): Boolean = builtIn != null
    override suspend fun builtIn(): Driver? = builtIn?.let {
        Driver("Turnip_Gen8_V39", "Turnip Gen8 V39", "", "Mesa", it, shipped = true)
    }
    override suspend fun install(file: File): Result<Driver> = Result.failure(UnsupportedOperationException())
    override suspend fun download(release: DriverRelease, onProgress: (Float) -> Unit): Result<Driver> =
        Result.failure(UnsupportedOperationException())
    override suspend fun remove(id: String): Boolean = false
    override suspend fun shippedArchivePresent(): Boolean = builtIn != null
}
