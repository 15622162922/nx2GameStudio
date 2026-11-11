package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The library's ground truth: the title id in a game's name, the data packs of the output folder, its display name */
class FolderScannerTest {

    private fun tmpDir(): File = File.createTempFile("nx2a-scan", "").let { f ->
        f.delete(); f.mkdirs(); f
    }

    @Test
    fun `a title id in the file's name is read`() {
        assertEquals("0100000000010000", FolderScanner.titleIdIn("Zelda [0100000000010000][v0].nsp"))
        assertEquals("0100ABCDEF012345", FolderScanner.titleIdIn("Game [0100abcdef012345].xci"))
        assertNull(FolderScanner.titleIdIn("Hades II.xci"))
        assertNull(FolderScanner.titleIdIn("[not a title id].nsp"))
    }

    @Test
    fun `the output folder is indexed by title id through the data packs' manifests, host included`() {
        val out = tmpDir()
        val pack = File(out, "HadesIIData-data")
        File(pack, "host").mkdirs()
        File(pack, "nx2a-data.json").writeText("""{"title_id":"0100000000010000","name":"Hades II","package":"org.nx2a.hades"}""")
        File(pack, "host/libdmplay.so").writeText("ELF")
        File(pack, "icon.png").writeBytes(byteArrayOf(1))
        // another data pack whose host is gone (a rebuild in flight): it does not count as done
        val orphan = File(out, "Gone-data")
        orphan.mkdirs()
        File(orphan, "nx2a-data.json").writeText("""{"title_id":"0100000000019999"}""")

        val idx = FolderScanner.outIndex(out)
        assertEquals(1, idx.size)
        val p = idx["0100000000010000"]!!
        assertEquals("Hades II", p.name)
        assertEquals("0100000000010000", p.titleId)
        assertTrue(p.host.isFile)
        assertTrue(p.icon!!.isFile)
        assertFalse(idx.containsKey("0100000000019999"))
        out.deleteRecursively()
    }

    @Test
    fun `the pack's own status is read, so an experimental port says so`() {
        val out = tmpDir()
        val pack = File(out, "DarkSouls-data")
        File(pack, "host").mkdirs()
        File(pack, "nx2a-data.json").writeText("""{"title_id":"01004AB00A260000","name":"DARK SOULS","status":"experimental"}""")
        File(pack, "host/libdmplay.so").writeText("ELF")

        val p = FolderScanner.outIndex(out)["01004AB00A260000"]!!
        assertEquals("experimental", p.status)
        // a pack from before the manifest carried the status: it is not experimental
        val old = File(out, "Old-data")
        File(old, "host").mkdirs()
        File(old, "nx2a-data.json").writeText("""{"title_id":"0100000000010000"}""")
        File(old, "host/libdmplay.so").writeText("ELF")
        assertEquals("", FolderScanner.outIndex(out)["0100000000010000"]!!.status)
        out.deleteRecursively()
    }

    @Test
    fun `a display name drops the brackets and the extension`() {
        assertEquals("Zelda", FolderScanner.displayName(File("Zelda [0100000000010000][v0].nsp")))
        assertEquals("Hades II", FolderScanner.displayName(File("Hades II.xci")))
        // nothing left after the brackets: the file's whole name stays
        assertEquals("(demo).nsp", FolderScanner.displayName(File("(demo).nsp")))
    }

    @Test
    fun `the label is the game's own name when the pack carries one`() {
        val out = tmpDir()
        val pack = File(out, "Arceus-data")
        File(pack, "host").mkdirs()
        File(pack, "nx2a-data.json").writeText("""{"title_id":"01001F5010DFA000","name":"Pokémon Legends: Arceus","display":"1.1.1"}""")
        File(pack, "host/libdmplay.so").writeText("ELF")

        val p = FolderScanner.outIndex(out)["01001F5010DFA000"]!!
        // the file the player downloaded says nothing about the game: the pack does
        assertEquals("Pokémon Legends: Arceus", FolderScanner.labelFor(File("/sdcard/Games/[本体v1.0.0]xxxx.nsp"), p))
        // the game's own version comes from the pack too
        assertEquals("1.1.1", p.display)
        out.deleteRecursively()
    }

    @Test
    fun `a game without a pack keeps its file's name as the label`() {
        assertEquals("xxxx", FolderScanner.labelFor(File("/sdcard/Games/[本体v1.0.0]xxxx.nsp"), null))
    }

    @Test
    fun `a pack without a name, or with a blank one, falls back to the file's name`() {
        val out = tmpDir()
        val old = File(out, "Old-data")
        File(old, "host").mkdirs()
        File(old, "nx2a-data.json").writeText("""{"title_id":"0100000000010000"}""")
        File(old, "host/libdmplay.so").writeText("ELF")
        val blank = File(out, "Blank-data")
        File(blank, "host").mkdirs()
        File(blank, "nx2a-data.json").writeText("""{"title_id":"0100000000020000","name":"   "}""")
        File(blank, "host/libdmplay.so").writeText("ELF")

        val f = File("/sdcard/Games/[本体v1.0.0]xxxx.nsp")
        assertEquals("xxxx", FolderScanner.labelFor(f, FolderScanner.outIndex(out)["0100000000010000"]))
        assertEquals("xxxx", FolderScanner.labelFor(f, FolderScanner.outIndex(out)["0100000000020000"]))
        out.deleteRecursively()
    }

    @Test
    fun `a pack without a display version leaves the version empty`() {
        val out = tmpDir()
        val pack = File(out, "Old-data")
        File(pack, "host").mkdirs()
        File(pack, "nx2a-data.json").writeText("""{"title_id":"0100000000010000","name":"Hades II"}""")
        File(pack, "host/libdmplay.so").writeText("ELF")

        assertEquals("", FolderScanner.outIndex(out)["0100000000010000"]!!.display)
        out.deleteRecursively()
    }
}
