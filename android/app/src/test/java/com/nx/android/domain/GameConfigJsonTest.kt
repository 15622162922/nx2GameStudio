package com.nx.android.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 按游戏配置的存取：多出来的「额外地址空间」记得住，旧版本存下的配置读出来是 Auto */
class GameConfigJsonTest {

    @Test
    fun `the config survives the round trip`() {
        val c = GameConfig(touch = true, fps60 = false, experimental = true, aliasHeadroom = 16)
        val back = GameConfigJson.decode(GameConfigJson.encode(c))
        assertEquals(c, back)
        assertEquals(16, back.aliasHeadroom)
    }

    @Test
    fun `an old config without the alias reads as Auto`() {
        val old = JSONObject().put("touch", true).put("fps60", true).put("exp", false)
        val c = GameConfigJson.decode(old)
        assertEquals(ALIAS_AUTO, c.aliasHeadroom)
        assertTrue(c.touch)
        assertTrue(c.fps60)
        assertFalse(c.experimental)
    }

    @Test
    fun `Auto is what a fresh config carries`() {
        assertEquals(ALIAS_AUTO, GameConfig().aliasHeadroom)
        assertEquals(0, GameConfig().copy(aliasHeadroom = 0).aliasHeadroom)
    }
}
