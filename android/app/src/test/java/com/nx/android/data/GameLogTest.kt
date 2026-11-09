package com.nx.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The game's runtime log: the host's log in the data pack, and the copy that goes out to another app.
 */
class GameLogTest {

    private fun tmp(): File = Files.createTempDirectory("nx2a-log").toFile()

    /** a pack whose game has run: the host's log is in it */
    private fun packWithLog(body: String): File {
        val pack = tmp()
        File(pack, "log.txt").writeText(body)
        return pack
    }

    @Test
    fun theLogIsInThePack() {
        val pack = tmp()
        assertNull("a game that never ran has no log", GameLog.inPack(pack))
        val log = File(pack, "log.txt")
        log.writeText("[dm] NX2A host on Android\n")
        assertEquals(log, GameLog.inPack(pack))
    }

    @Test
    fun aGameThatNeverRanHasNoLogToShare() {
        val dest = tmp()
        assertNull(GameLog.copyTo(tmp(), dest, "Hades"))
        assertEquals("nothing is written", 0, dest.listFiles()?.size ?: 0)
    }

    @Test
    fun theCopyIsTheLogItself() {
        val pack = packWithLog("[dm] NX2A host on Android\n[dm] the game ran\n")
        val dest = tmp()
        val out = GameLog.copyTo(pack, dest, "Hades")
        assertNotNull(out)
        assertEquals(File(dest, "Hades-log.txt"), out)
        assertEquals("[dm] NX2A host on Android\n[dm] the game ran\n", out!!.readText())
    }

    @Test
    fun theNameIsSafeInAFileName() {
        assertEquals("Hades-log.txt", GameLog.shareName("Hades"))
        assertEquals("Super_Mario_Odyssey-log.txt", GameLog.shareName("Super Mario Odyssey"))
        assertEquals("A_B-log.txt", GameLog.shareName("A/B"))
        assertEquals("game-log.txt", GameLog.shareName(""))
    }

    @Test
    fun anOlderCopyIsReplaced() {
        val pack = packWithLog("[dm] the second run\n")
        val dest = tmp()
        File(dest, "Hades-log.txt").writeText("[dm] the first run\n")
        val out = GameLog.copyTo(pack, dest, "Hades")
        assertNotNull(out)
        assertEquals("[dm] the second run\n", out!!.readText())
        assertFalse("no half file is left behind", File(dest, ".Hades-log.txt.part").exists())
    }

    @Test
    fun theCopyGoesIntoAFolderThatIsNotThereYet() {
        val pack = packWithLog("[dm] hello\n")
        val dest = File(tmp(), "nx2android")
        val out = GameLog.copyTo(pack, dest, "Hades")
        assertNotNull(out)
        assertTrue(dest.isDirectory)
        assertTrue(out!!.isFile)
    }
}
