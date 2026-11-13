package com.nx.android.data.playback

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
        assertEquals(listOf("en-US" to "English", "ja" to "日本語", "zh-Hans" to "简体中文"), PackEnvStore.languages(p))
        // a driver the pack does not carry is not offered
        assertEquals(listOf("libvulkan_freedreno.so"), PackEnvStore.drivers(p))
        p.deleteRecursively()
    }

    @Test
    fun `set writes the two rows, the others keep their places`() {
        val p = pack()
        File(p, "env.txt").writeText("# Written by nx2android's porter\nDM_LANG=en-US\nDM_AOC_DIR=/sdcard/x/aoc\nDM_ROOT=/sdcard/x\n")
        PackEnvStore.set(p, "ja", "libvulkan_freedreno.so")
        val (lang, drv) = PackEnvStore.current(p)
        assertEquals("ja", lang); assertEquals("libvulkan_freedreno.so", drv)
        val body = File(p, "env.txt").readText()
        assertTrue(body.contains("DM_AOC_DIR=/sdcard/x/aoc"))
        assertTrue(body.contains("# Written by nx2android's porter"))
        // a removal: back to the system's driver, the language row goes too with null
        PackEnvStore.set(p, "zh-Hans", null)
        val (lang2, drv2) = PackEnvStore.current(p)
        assertEquals("zh-Hans", lang2); assertNull(drv2)
        p.deleteRecursively()
    }

    @Test
    fun `a pack without env answers empty`() {
        val (lang, drv) = PackEnvStore.current(pack())
        assertNull(lang); assertNull(drv)
        assertEquals(0, PackEnvStore.languages(pack()).size)
    }

    @Test
    fun `an empty driver row is the player's own choice, an absent row is the default's place`() {
        val p = pack()
        File(p, "env.txt").writeText("DM_VK_DRIVER=\n")
        assertEquals("", PackEnvStore.current(p).second)
        // the default does not take that choice back
        PackEnvStore.ensureDriverDefault(p, "/app/files/drivers/Turnip/libvulkan_freedreno.so")
        assertEquals("", PackEnvStore.current(p).second)
        p.deleteRecursively()
    }

    @Test
    fun `the default driver goes into a pack whose driver was never picked`() {
        val p = pack()
        File(p, "env.txt").writeText("# Written by nx2android's porter\nDM_ROOT=/sdcard/x\n")
        PackEnvStore.ensureDriverDefault(p, "/app/files/drivers/Turnip/libvulkan_freedreno.so")
        assertEquals("/app/files/drivers/Turnip/libvulkan_freedreno.so", PackEnvStore.current(p).second)
        val body = File(p, "env.txt").readText()
        assertTrue(body.contains("# Written by nx2android's porter"))
        assertTrue(body.contains("DM_ROOT=/sdcard/x"))
        // it is written once: a second call leaves the row alone
        PackEnvStore.ensureDriverDefault(p, "/other/driver.so")
        assertEquals("/app/files/drivers/Turnip/libvulkan_freedreno.so", PackEnvStore.current(p).second)
        p.deleteRecursively()
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
            PackEnvStore.driverChoices(p, installed),
        )
        p.deleteRecursively()
    }

    @Test
    fun `an installed driver and a pack's own may carry the same name and stay two choices`() {
        val p = pack()
        File(p, "drivers.txt").writeText("libvulkan_freedreno.so\n")
        File(p, "drivers").mkdirs()
        File(p, "drivers/libvulkan_freedreno.so").writeText("DRV")
        val choices = PackEnvStore.driverChoices(p, listOf("/app/files/drivers/x/libvulkan_freedreno.so" to "Turnip Gen8 V39"))
        assertEquals(3, choices.size)
        assertEquals("/app/files/drivers/x/libvulkan_freedreno.so", choices[1].first)
        assertEquals("libvulkan_freedreno.so", choices[2].first)
        p.deleteRecursively()
    }
}
