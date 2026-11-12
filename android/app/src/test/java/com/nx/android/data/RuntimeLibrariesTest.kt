package com.nx.android.data

import com.nx.android.domain.Ported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RuntimeLibrariesTest {
    @Test
    fun `same size runtime is refreshed with its own hooks and user driver`() {
        val root = Files.createTempDirectory("runtime-copy").toFile()
        try {
            val pack = File(root, "Game-data").apply { mkdirs() }
            val generation = File(pack, ".runtime-new").apply { mkdirs() }
            val host = File(generation, "libdmplay.so").apply { writeText("new!") }
            val drivers = File(pack, "drivers").apply { mkdirs() }
            RuntimeLibraries.hooks.forEach {
                File(generation, it).writeText("new!")
                File(drivers, it).writeText("old!")
            }
            File(drivers, "custom.so").writeText("user")
            val destination = File(root, "launch").apply { mkdirs() }
            File(destination, "libdmplay.so").writeText("old!")
            File(destination, "removed.so").writeText("gone")
            RuntimeLibraries.copyForLaunch(Ported(pack, host, null, "0100000000010000"), destination)
            assertEquals("new!", File(destination, "libdmplay.so").readText())
            RuntimeLibraries.hooks.forEach { assertEquals("new!", File(destination, it).readText()) }
            assertEquals("user", File(destination, "custom.so").readText())
            assertEquals("old!", File(drivers, RuntimeLibraries.hooks.first()).readText())
            assertFalse(File(destination, "removed.so").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = java.io.IOException::class)
    fun `incomplete generation cannot use old hooks`() {
        val root = Files.createTempDirectory("runtime-incomplete").toFile()
        try {
            val host = File(root, ".runtime-new/libdmplay.so")
            host.parentFile!!.mkdirs()
            host.writeText("host")
            val drivers = File(root, "drivers").apply { mkdirs() }
            RuntimeLibraries.hooks.forEach { File(drivers, it).writeText("old!") }
            RuntimeLibraries.copyForLaunch(Ported(root, host, null, "0100000000010000"), File(root, "launch"))
        } finally {
            root.deleteRecursively()
        }
    }
}
