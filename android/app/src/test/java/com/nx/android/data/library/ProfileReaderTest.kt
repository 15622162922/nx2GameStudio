package com.nx.android.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 档案里那几个键：图形接口、联机、设备要求、调通状态 */
class ProfileReaderTest {

    @Test
    fun `the keys are read from the profile's own rows`() {
        val p = parseProfile(
            """
            # Football Manager 26 Touch on Android
            status	playable
            graphics	gl
            lan	1
            requires	bcn
            """.trimIndent(),
        )
        assertEquals("playable", p.status)
        assertEquals("gl", p.graphics)
        assertTrue(p.lan)
        assertEquals(listOf("bcn"), p.requires)
    }

    @Test
    fun `a profile without the new keys says nothing`() {
        val p = parseProfile("status\twip\ntested\t1.0 Pixel\n")
        assertEquals("wip", p.status)
        assertEquals("", p.graphics)
        assertFalse(p.lan)
        assertEquals(emptyList<String>(), p.requires)
    }

    @Test
    fun `lan is only yes when it says one`() {
        assertFalse(parseProfile("lan\t0\n").lan)
        assertFalse(parseProfile("lan\n").lan)
        assertTrue(parseProfile("lan\t1\n").lan)
    }
}
