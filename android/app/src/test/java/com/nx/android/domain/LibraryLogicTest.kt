package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The card's status: the running port, the remembered pack (the host inside makes it done), and the pack whose host went away */
class LibraryLogicTest {

    private fun tmpDir(): File = File.createTempFile("nx2a-port", "").let { f -> f.delete(); f.mkdirs(); f }

    private fun port(hostExists: Boolean): Ported {
        val pack = tmpDir()
        File(pack, "host").mkdirs()
        val host = File(pack, "host/libdmplay.so")
        if (hostExists) host.writeText("ELF")
        return Ported(pack, host, null, "0100000000010000", "Hades II")
    }

    @Test
    fun `a finished port (pack and host together) makes the game done`() {
        val f = File("/sdcard/Games/Game.nsp")
        assertEquals(GameStatus.DONE,
                     LibraryLogic.statusOf(f, mapOf(f.absolutePath to port(hostExists = true)), portRunning = false, portGameFile = ""))
    }

    @Test
    fun `the game that runs now is porting, the others are not`() {
        val f = File("/sdcard/Games/Game.nsp")
        assertEquals(GameStatus.PORTING,
                     LibraryLogic.statusOf(f, mapOf(), portRunning = true, portGameFile = f.absolutePath))
        assertEquals(GameStatus.NOT_PORTED,
                     LibraryLogic.statusOf(File("/sdcard/Games/Other.nsp"), mapOf(), portRunning = true, portGameFile = f.absolutePath))
    }

    @Test
    fun `a pack whose host went away falls back to not ported`() {
        val f = File("/sdcard/Games/Game.nsp")
        assertEquals(GameStatus.NOT_PORTED,
                     LibraryLogic.statusOf(f, mapOf(f.absolutePath to port(hostExists = false)), portRunning = false, portGameFile = ""))
    }
}
