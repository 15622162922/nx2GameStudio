package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The porter's progress events, as the library's cards and the builds page read them */
class PortEventsTest {

    @Test
    fun `a stage event carries the stage and its count`() {
        val ev = PortEvents.parse("""{"ev":"stage","k":3,"n":7,"name":"Vulkan shaders"}""")
        assertEquals("stage", ev!!.kind)
        assertEquals(3, ev.stage)
        assertEquals(7, ev.stages)
        assertEquals("Vulkan shaders", ev.name)
    }

    @Test
    fun `a progress event carries the fraction and the step`() {
        val ev = PortEvents.parse("""{"ev":"progress","frac":0.42,"name":"extract","step":"RomFS"}""")
        assertEquals("progress", ev!!.kind)
        assertEquals(0.42f, ev.frac, 0.001f)
        assertEquals("RomFS", ev.step)
    }

    @Test
    fun `a progress event without a fraction reads as minus one`() {
        val ev = PortEvents.parse("""{"ev":"progress","name":"extract"}""")
        assertEquals(-1f, ev!!.frac, 0.001f)
    }

    @Test
    fun `a result event carries the apk and the package`() {
        val ev = PortEvents.parse("""{"ev":"result","apk":"/sdcard/Switch/Ports/Game.apk","data":"/sdcard/Switch/Ports/Game-data","package":"org.nx2a.game"}""")
        assertEquals("result", ev!!.kind)
        assertEquals("/sdcard/Switch/Ports/Game.apk", ev.apk)
        assertEquals("org.nx2a.game", ev.pkg)
    }

    @Test
    fun `a say event carries its message`() {
        val ev = PortEvents.parse("""{"ev":"say","msg":"unpacking"}""")
        assertEquals("say", ev!!.kind)
        assertEquals("unpacking", ev.message)
    }

    @Test
    fun `plain log lines are not events`() {
        assertNull(PortEvents.parse("  · extract RomFS"))
        assertNull(PortEvents.parse("[RED] no heap check"))
        assertNull(PortEvents.parse(""))
        assertFalse(PortEvents.parse("{not json").let { it != null })
    }
}
