package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The card's status: the running port, the remembered APK, and the APK that went away */
class LibraryLogicTest {

    private val apk = File.createTempFile("game", ".apk").also { it.writeText("apk") }

    @Test
    fun `a remembered apk makes the game done`() {
        val f = File("/sdcard/Games/Game.nsp")
        assertEquals(GameStatus.DONE,
                     LibraryLogic.statusOf(f, mapOf(f.absolutePath to apk), portRunning = false, portGameFile = ""))
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
    fun `a deleted apk falls back to not ported`() {
        val f = File("/sdcard/Games/Game.nsp")
        val gone = File("/sdcard/Switch/Ports/Gone.apk")
        assertEquals(GameStatus.NOT_PORTED,
                     LibraryLogic.statusOf(f, mapOf(f.absolutePath to gone), portRunning = false, portGameFile = ""))
        apk.delete()
    }
}
