package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The library's ground truth: which files are games, which APK is whose, which status a card wears */
class FolderScannerTest {

    private fun tmpDir(): File = File.createTempFile("nx2a-scan", "").let { f ->
        f.delete(); f.mkdirs(); f
    }

    @Test
    fun `scanning finds the game files of the folder and its subfolders, by name`() {
        val d = tmpDir()
        File(d, "Zelda [0100000000010000][v0].nsp").writeText("")
        File(d, "Hades II.xci").writeText("")
        File(d, "DLC").mkdirs()
        File(d, "DLC/pack.nsz").writeText("")
        File(d, "notes.txt").writeText("")
        File(d, ".hidden.xci").writeText("")
        val found = FolderScanner.scan(d)
        assertEquals(listOf("Hades II.xci", "pack.nsz", "Zelda [0100000000010000][v0].nsp"),
                     found.map { it.name })
        d.deleteRecursively()
    }

    @Test
    fun `a missing folder scans as empty`() {
        assertEquals(0, FolderScanner.scan(File("/no/such/folder")).size)
    }

    @Test
    fun `a title id in the file's name is read`() {
        assertEquals("0100000000010000", FolderScanner.titleIdIn("Zelda [0100000000010000][v0].nsp"))
        assertEquals("0100ABCDEF012345", FolderScanner.titleIdIn("Game [0100abcdef012345].xci"))
        assertNull(FolderScanner.titleIdIn("Hades II.xci"))
        assertNull(FolderScanner.titleIdIn("[not a title id].nsp"))
    }

    @Test
    fun `the output folder is indexed by title id through the data packs' manifests`() {
        val out = tmpDir()
        val pack = File(out, "HadesIIData-data")
        pack.mkdirs()
        File(pack, "nx2a-data.json").writeText("""{"title_id":"0100000000010000","name":"Hades II","package":"org.nx2a.hades"}""")
        File(out, "HadesIIData.apk").writeText("")
        // another data pack whose APK is gone: it does not enter the index
        val orphan = File(out, "Gone-data")
        orphan.mkdirs()
        File(orphan, "nx2a-data.json").writeText("""{"title_id":"0100000000019999"}""")

        val idx = FolderScanner.outIndex(out)
        assertEquals(1, idx.size)
        assertEquals("HadesIIData.apk", idx["0100000000010000"]!!.name)
        assertFalse(idx.containsKey("0100000000019999"))
        out.deleteRecursively()
    }

    @Test
    fun `a display name drops the brackets and the extension`() {
        assertEquals("Zelda", FolderScanner.displayName(File("Zelda [0100000000010000][v0].nsp")))
        assertEquals("Hades II", FolderScanner.displayName(File("Hades II.xci")))
        // nothing left after the brackets: the file's whole name stays
        assertEquals("(demo).nsp", FolderScanner.displayName(File("(demo).nsp")))
    }
}
