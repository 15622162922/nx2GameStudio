package com.nx.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The save package: the game's save leaves the app as a zip and comes back the same way.
 */
class SaveBackupTest {

    private fun tmp(name: String): File = Files.createTempDirectory("nx2a-save").toFile()

    /** a live save: <internal>/save/<titleId>, with a file in it */
    private fun liveSave(internal: File, titleId: String, name: String, body: String): File {
        val dir = SaveBackup.liveDir(internal, titleId)
        dir.mkdirs()
        File(dir, name).writeText(body)
        return dir
    }

    @Test
    fun theLiveSaveIsTheAppsOwn() {
        val internal = tmp("internal")
        val live = SaveBackup.liveDir(internal, "0100000000010000")
        assertEquals(File(internal, "save/0100000000010000"), live)
        assertEquals(File(internal, "save/0100000000010000_device"), SaveBackup.deviceDir(internal, "0100000000010000"))
    }

    @Test
    fun theNameCarriesTheTitleId() {
        val name = SaveBackup.packageName("塞尔达传说 旷野之息", "0100000000010000", "20261109-153012")
        assertTrue(name, name.endsWith(".zip"))
        assertEquals("0100000000010000", SaveBackup.titleIdIn(name))
        // the game's own name may hold dashes: the id is still the third part from the end
        assertEquals("0100000000018000", SaveBackup.titleIdIn("Mario-Kart-0100000000018000-20261109-153012.zip"))
    }

    @Test
    fun aNameWithoutATitleIdCarriesNone() {
        assertNull(SaveBackup.titleIdIn("save.zip"))
        assertNull(SaveBackup.titleIdIn("塞尔达传说 旷野之息-20261109-153012.zip"))
        assertNull(SaveBackup.titleIdIn("not-a-package.txt"))
    }

    @Test
    fun aPackageIsTheSaveAndItsDeviceSave() {
        val internal = tmp("internal")
        val live = liveSave(internal, "0100000000010000", "slot0.dat", "progress")
        File(live, "sub").mkdirs()
        File(live, "sub/other.dat").writeText("more")
        val device = SaveBackup.deviceDir(internal, "0100000000010000")
        device.mkdirs()
        File(device, "island.dat").writeText("device")

        val dest = File(tmp("out"), "pkg.zip")
        val written = SaveBackup.export(live, device, dest)
        assertNotNull(written)
        assertTrue(dest.isFile)

        val unpacked = SaveBackup.unpack(dest, tmp("unpacked"))
        assertEquals("pkg", unpacked.tree.name)     // the tree in the package is named after the package
        assertEquals("progress", File(unpacked.tree, "slot0.dat").readText())
        assertEquals("more", File(unpacked.tree, "sub/other.dat").readText())
        assertNotNull("the device save comes with it", unpacked.device)
        assertEquals("device", File(unpacked.device!!, "island.dat").readText())
    }

    @Test
    fun twoPackagesInTheSameSecondDoNotShareAName() {
        val internal = tmp("internal")
        val live = liveSave(internal, "0100000000010000", "slot0.dat", "progress")
        val out = tmp("out")
        val first = SaveBackup.export(live, null, File(out, "game-0100000000010000-20261109-153012.zip"))
        val second = SaveBackup.export(live, null, File(out, "game-0100000000010000-20261109-153012.zip"))
        assertNotNull(first)
        assertNotNull(second)
        assertTrue(first!!.name != second!!.name)
        assertTrue(second.isFile)
    }

    @Test
    fun aGameWithoutASaveHasNoPackage() {
        val internal = tmp("internal")
        SaveBackup.liveDir(internal, "0100000000010000").mkdirs()
        val dest = File(tmp("out"), "pkg.zip")
        assertNull(SaveBackup.export(SaveBackup.liveDir(internal, "0100000000010000"),
            SaveBackup.deviceDir(internal, "0100000000010000"), dest))
        assertTrue(!dest.exists())
    }

    @Test
    fun aPackageThatPointsOutOfTheFolderIsRefused() {
        val pkg = File(tmp("out"), "evil.zip")
        ZipOutputStream(pkg.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escaped.txt"))
            zip.write("gotcha".toByteArray())
            zip.closeEntry()
        }
        try {
            SaveBackup.unpack(pkg, tmp("unpacked"))
            fail("a package that writes out of the folder has to be refused")
        } catch (e: Exception) {
            // the refusal carries a code, not a sentence: the app's words for it live in its own resources
            assertTrue(e.message ?: "", e is SaveBackup.Refused)
            val refused = e as SaveBackup.Refused
            assertEquals(SaveBackup.Refusal.PATH_ESCAPES, refused.refusal)
        }
    }

    @Test
    fun theSaveAnImportReplacesIsKept() {
        val internal = tmp("internal")
        val live = liveSave(internal, "0100000000010000", "slot0.dat", "old progress")
        val packSaves = File(tmp("pack"), "saves")

        val backup = SaveBackup.backup(live, null, packSaves, "20261109-153012")
        assertNotNull(backup)
        assertEquals("20261109-153012-before-import", backup!!.name)
        assertEquals("old progress", File(backup, "slot0.dat").readText())

        // the new save takes the place, the old one is gone
        val fresh = tmp("fresh")
        File(fresh, "slot0.dat").writeText("new progress")
        SaveBackup.swapIn(fresh, null, live, SaveBackup.deviceDir(internal, "0100000000010000"))
        assertEquals("new progress", File(live, "slot0.dat").readText())
        assertTrue("the old tree is gone", !File(internal, "save/0100000000010000.old").exists())
        assertTrue("no leftovers", !File(internal, "save/0100000000010000.import").exists())
    }

    @Test
    fun theDeviceSaveIsSwappedToo() {
        val internal = tmp("internal")
        val live = liveSave(internal, "0100000000010000", "slot0.dat", "progress")
        val device = SaveBackup.deviceDir(internal, "0100000000010000")
        device.mkdirs()
        File(device, "island.dat").writeText("old island")

        val fresh = tmp("fresh")
        File(fresh, "slot0.dat").writeText("progress")
        val freshDevice = tmp("fresh-device")
        File(freshDevice, "island.dat").writeText("new island")
        SaveBackup.swapIn(fresh, freshDevice, live, device)
        assertEquals("new island", File(device, "island.dat").readText())
        assertEquals("progress", File(live, "slot0.dat").readText())
    }

    @Test
    fun a_failed_install_restores_the_original_live_save() {
        val internal = tmp("internal")
        val live = liveSave(internal, "0100000000010000", "slot0.dat", "original")
        val device = SaveBackup.deviceDir(internal, "0100000000010000")
        val missing = File(tmp("missing"), "not-a-tree")

        val result = runCatching { SaveBackup.swapIn(missing, null, live, device) }

        assertTrue(result.isFailure)
        assertEquals("original", File(live, "slot0.dat").readText())
        assertFalse(File(internal, "save/0100000000010000.old").exists())
        assertFalse(File(internal, "save/0100000000010000.import").exists())
    }
}
