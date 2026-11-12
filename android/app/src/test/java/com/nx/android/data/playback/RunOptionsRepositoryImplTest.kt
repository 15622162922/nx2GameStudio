package com.nx.android.data.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
            val repository = RunOptionsRepositoryImpl(Dispatchers.Unconfined)

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
}
