package com.nx.android.appstate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 正在玩的那个游戏（一次一个）：状态机自己看着 :play 进程，进程没了就把状态清掉。
 *
 * 这里没有「游戏已退出」这个状态了——以前它是一个全局布尔，会贴到任何一个被选中的游戏上；
 * 现在进程没了就只是没了，库页什么都不说（那个字段已经删了，编译不过就是它不在了）。
 */
class PlayerStateTest {

    private fun waitUntil(what: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (what()) return true
            Thread.sleep(10)
        }
        return what()
    }

    @Test
    fun `a started game is running, and the library knows which one`() {
        val p = PlayerState(alive = { true }, pollMs = 20)
        p.start("0100000000010000", "Hades II")
        assertTrue("the game is running", p.running)
        assertEquals("0100000000010000", p.titleId)
        assertEquals("Hades II", p.name)
        p.stop()
    }

    @Test
    fun `the process going away clears the state and says nothing`() {
        var alive = true
        val p = PlayerState(alive = { alive }, pollMs = 20)
        p.start("0100000000010000", "Hades II")
        alive = false
        assertTrue("the watch notices the process is gone", waitUntil { !p.running })
        assertEquals("", p.titleId)
        assertEquals("", p.name)
    }

    @Test
    fun `the app's own exit clears the state at once`() {
        val p = PlayerState(alive = { true }, pollMs = 20)
        p.start("0100000000010000", "Hades II")
        p.stop()
        assertFalse("the game is not running any more", p.running)
        assertEquals("", p.titleId)
        assertEquals("", p.name)
    }

    @Test
    fun `starting again after the game ended watches again`() {
        var alive = true
        val p = PlayerState(alive = { alive }, pollMs = 20)
        p.start("0100000000010000", "Hades II")
        alive = false
        assertTrue(waitUntil { !p.running })
        alive = true
        p.start("0100000000020000", "Another game")
        assertTrue(p.running)
        assertEquals("0100000000020000", p.titleId)
        assertTrue(waitUntil { p.running })   // 旧的那次轮询没有把新的状态清掉
        p.stop()
    }
}
