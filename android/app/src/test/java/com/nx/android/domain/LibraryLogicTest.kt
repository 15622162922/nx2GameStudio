package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /** 上次构建失败的游戏与「从没移植过」不是一回事，玩家要能看出来 */
    @Test
    fun `a game whose last build failed is failed, not just not ported`() {
        val f = File("/sdcard/Games/Game.nsp")
        assertEquals(GameStatus.FAILED,
                     LibraryLogic.statusOf(f, mapOf(), portRunning = false, portGameFile = "", lastFailed = true))
        assertEquals(GameStatus.NOT_PORTED,
                     LibraryLogic.statusOf(f, mapOf(), portRunning = false, portGameFile = "", lastFailed = false))
    }

    /** 重新构建失败但旧包还在：游戏照样能玩 */
    @Test
    fun `a finished port wins over a failed build`() {
        val f = File("/sdcard/Games/Game.nsp")
        assertEquals(GameStatus.DONE,
                     LibraryLogic.statusOf(f, mapOf(f.absolutePath to port(hostExists = true)),
                                          portRunning = false, portGameFile = "", lastFailed = true))
    }

    /** 正在构建的永远是构建中 */
    @Test
    fun `the running build wins over everything else`() {
        val f = File("/sdcard/Games/Game.nsp")
        assertEquals(GameStatus.PORTING,
                     LibraryLogic.statusOf(f, mapOf(f.absolutePath to port(hostExists = true)),
                                          portRunning = true, portGameFile = f.absolutePath, lastFailed = true))
    }

    /** 正在构建 / 正在运行的游戏不能删，别的游戏能删 */
    @Test
    fun `a game that is being ported or is running cannot be deleted`() {
        val path = "/sdcard/Games/Game.nsp"
        assertEquals(GameCleanup.Outcome.BUSY_PORTING,
                     LibraryLogic.removalBlocked(portRunning = true, portGameFile = path, gamePath = path,
                                                playerRunning = false, playerTitleId = "", titleId = "0100000000010000"))
        assertEquals(GameCleanup.Outcome.BUSY_RUNNING,
                     LibraryLogic.removalBlocked(portRunning = false, portGameFile = "", gamePath = path,
                                                playerRunning = true, playerTitleId = "0100000000010000",
                                                titleId = "0100000000010000"))
        // 别的游戏在构建 / 在玩：这个游戏照样能删
        assertEquals(null,
                     LibraryLogic.removalBlocked(portRunning = true, portGameFile = "/sdcard/Games/Other.nsp",
                                                gamePath = path, playerRunning = true,
                                                playerTitleId = "0100000000020000", titleId = "0100000000010000"))
        // 什么都没在跑：能删
        assertEquals(null,
                     LibraryLogic.removalBlocked(portRunning = false, portGameFile = "", gamePath = path,
                                                playerRunning = false, playerTitleId = "", titleId = "0100000000010000"))
    }

    /**
     * 点「运行」的两种走法：这个游戏还活着就回到它（不重启），否则全新启动。
     * 状态慢半拍（进程没了而状态还没清）与状态整个丢了（App 的进程被回收过）都要走全新启动。
     */
    @Test
    fun `the run button brings the running game back instead of starting it again`() {
        assertEquals(RunAction.RESUME,
                     LibraryLogic.runAction(running = true, runningTitleId = "0100000000010000",
                                           portTitleId = "0100000000010000", processAlive = true))
    }

    @Test
    fun `a game that is not running any more is started afresh`() {
        // 进程没了（游戏刚结束，状态还没清）：回到前台会回到一个已经不在的页面
        assertEquals(RunAction.START,
                     LibraryLogic.runAction(running = true, runningTitleId = "0100000000010000",
                                           portTitleId = "0100000000010000", processAlive = false))
        // 别的游戏在跑：玩家点的这个要自己起来（先结束前一个）
        assertEquals(RunAction.START,
                     LibraryLogic.runAction(running = true, runningTitleId = "0100000000020000",
                                           portTitleId = "0100000000010000", processAlive = true))
        // 状态整个没了（App 的进程被回收重启过）
        assertEquals(RunAction.START,
                     LibraryLogic.runAction(running = false, runningTitleId = "",
                                           portTitleId = "0100000000010000", processAlive = true))
    }

    /** 搬移：正在构建 / 正在运行 / 正在搬，都不能搬 */
    @Test
    fun `a pack cannot be moved while the game is busy`() {
        val path = "/games/Game [0100000000010000].nsp"
        assertEquals(GameCleanup.Outcome.BUSY_MOVING,
                     LibraryLogic.moveBlocked(portRunning = false, portGameFile = "", gamePath = path,
                                             playerRunning = false, playerTitleId = "",
                                             titleId = "0100000000010000", moving = true))
        assertEquals(GameCleanup.Outcome.BUSY_PORTING,
                     LibraryLogic.moveBlocked(portRunning = true, portGameFile = path, gamePath = path,
                                             playerRunning = false, playerTitleId = "",
                                             titleId = "0100000000010000", moving = false))
        assertEquals(GameCleanup.Outcome.BUSY_RUNNING,
                     LibraryLogic.moveBlocked(portRunning = false, portGameFile = "", gamePath = path,
                                             playerRunning = true, playerTitleId = "0100000000010000",
                                             titleId = "0100000000010000", moving = false))
        assertNull(
            LibraryLogic.moveBlocked(portRunning = false, portGameFile = "", gamePath = path,
                                     playerRunning = false, playerTitleId = "",
                                     titleId = "0100000000010000", moving = false))
    }
}
