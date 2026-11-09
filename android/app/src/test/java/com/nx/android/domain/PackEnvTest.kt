package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The pack's run options: the lists come from the pack, and set() rewrites only the two rows it owns */
class PackEnvTest {

    private fun pack(): File = File.createTempFile("nx2a-packenv", "").let { f -> f.delete(); f.mkdirs(); f }

    @Test
    fun `the lists come from the pack`() {
        val p = pack()
        File(p, "languages.txt").writeText("en-US\tEnglish\nja\t日本語\nzh-Hans\t简体中文\n")
        File(p, "drivers.txt").writeText("libvulkan_freedreno.so\nlibvulkan_turnip.so\n")
        File(p, "drivers").mkdirs()
        File(p, "drivers/libvulkan_freedreno.so").writeText("DRV")
        assertEquals(listOf("en-US" to "English", "ja" to "日本語", "zh-Hans" to "简体中文"), PackEnv.languages(p))
        // a driver the pack does not carry is not offered
        assertEquals(listOf("libvulkan_freedreno.so"), PackEnv.drivers(p))
        p.deleteRecursively()
    }

    @Test
    fun `set writes the two rows, the others keep their places`() {
        val p = pack()
        File(p, "env.txt").writeText("# Written by nx2android's porter\nDM_LANG=en-US\nDM_AOC_DIR=/sdcard/x/aoc\nDM_ROOT=/sdcard/x\n")
        PackEnv.set(p, "ja", "libvulkan_freedreno.so")
        val (lang, drv) = PackEnv.current(p)
        assertEquals("ja", lang); assertEquals("libvulkan_freedreno.so", drv)
        val body = File(p, "env.txt").readText()
        assertTrue(body.contains("DM_AOC_DIR=/sdcard/x/aoc"))
        assertTrue(body.contains("# Written by nx2android's porter"))
        // a removal: back to the system's driver, the language row goes too with null
        PackEnv.set(p, "zh-Hans", null)
        val (lang2, drv2) = PackEnv.current(p)
        assertEquals("zh-Hans", lang2); assertNull(drv2)
        p.deleteRecursively()
    }

    @Test
    fun `a pack without env answers empty`() {
        val (lang, drv) = PackEnv.current(pack())
        assertNull(lang); assertNull(drv)
        assertEquals(0, PackEnv.languages(pack()).size)
    }

    @Test
    fun `the driver's field offers the device's own, the installed and the pack's own`() {
        val p = pack()
        File(p, "drivers.txt").writeText("libvulkan_freedreno.so\n")
        File(p, "drivers").mkdirs()
        File(p, "drivers/libvulkan_freedreno.so").writeText("DRV")
        val installed = listOf("/data/user/0/com.nx.android/files/drivers/Turnip_Gen8_V39/libvulkan_freedreno.so" to "Turnip Gen8 V39")
        assertEquals(
            listOf(
                // the device's own driver has no name here: the app names it in its own words
                "" to null,
                installed[0],
                "libvulkan_freedreno.so" to "libvulkan_freedreno.so",
            ),
            PackEnv.driverChoices(p, installed),
        )
        p.deleteRecursively()
    }

    @Test
    fun `an installed driver and a pack's own may carry the same name and stay two choices`() {
        val p = pack()
        File(p, "drivers.txt").writeText("libvulkan_freedreno.so\n")
        File(p, "drivers").mkdirs()
        File(p, "drivers/libvulkan_freedreno.so").writeText("DRV")
        val choices = PackEnv.driverChoices(p, listOf("/app/files/drivers/x/libvulkan_freedreno.so" to "Turnip Gen8 V39"))
        assertEquals(3, choices.size)
        assertEquals("/app/files/drivers/x/libvulkan_freedreno.so", choices[1].first)
        assertEquals("libvulkan_freedreno.so", choices[2].first)
        p.deleteRecursively()
    }
}
