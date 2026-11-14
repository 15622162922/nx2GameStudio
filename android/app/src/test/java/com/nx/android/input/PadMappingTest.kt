package com.nx.android.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 查表规则：玩家自己绑的先，出厂字典兜底。
 *
 * 出厂字典必须与改动前逐键一致（按位置：下面那颗是 B，右边那颗是 A）；按标签是另一套（键上印什么就是什么）。
 */
class PadMappingTest {

    @Test
    fun `the default dictionary is the Switch's layout by position`() {
        assertEquals("b", PadMapping.defaultTarget(PadMapping.Key.BUTTON_A))
        assertEquals("a", PadMapping.defaultTarget(PadMapping.Key.BUTTON_B))
        assertEquals("y", PadMapping.defaultTarget(PadMapping.Key.BUTTON_X))
        assertEquals("x", PadMapping.defaultTarget(PadMapping.Key.BUTTON_Y))
        assertEquals("l", PadMapping.defaultTarget(PadMapping.Key.BUTTON_L1))
        assertEquals("r", PadMapping.defaultTarget(PadMapping.Key.BUTTON_R1))
        assertEquals("zl", PadMapping.defaultTarget(PadMapping.Key.BUTTON_L2))
        assertEquals("zr", PadMapping.defaultTarget(PadMapping.Key.BUTTON_R2))
        assertEquals("ls", PadMapping.defaultTarget(PadMapping.Key.BUTTON_THUMBL))
        assertEquals("rs", PadMapping.defaultTarget(PadMapping.Key.BUTTON_THUMBR))
        assertEquals("plus", PadMapping.defaultTarget(PadMapping.Key.BUTTON_START))
        assertEquals("minus", PadMapping.defaultTarget(PadMapping.Key.BUTTON_SELECT))
        assertEquals("up", PadMapping.defaultTarget(PadMapping.Key.DPAD_UP))
        assertEquals("down", PadMapping.defaultTarget(PadMapping.Key.DPAD_DOWN))
        assertEquals("left", PadMapping.defaultTarget(PadMapping.Key.DPAD_LEFT))
        assertEquals("right", PadMapping.defaultTarget(PadMapping.Key.DPAD_RIGHT))
        assertNull(PadMapping.defaultTarget(9999))
    }

    @Test
    fun `by label the face buttons are A A B B X X Y Y`() {
        assertEquals("a", PadMapping.defaultTarget(PadMapping.Key.BUTTON_A, labels = true))
        assertEquals("b", PadMapping.defaultTarget(PadMapping.Key.BUTTON_B, labels = true))
        assertEquals("x", PadMapping.defaultTarget(PadMapping.Key.BUTTON_X, labels = true))
        assertEquals("y", PadMapping.defaultTarget(PadMapping.Key.BUTTON_Y, labels = true))
        // the rest is the same either way
        assertEquals("l", PadMapping.defaultTarget(PadMapping.Key.BUTTON_L1, labels = true))
        assertEquals("plus", PadMapping.defaultTarget(PadMapping.Key.BUTTON_START, labels = true))
    }

    @Test
    fun `the scan codes follow the same two layouts`() {
        assertEquals("b", PadMapping.defaultTargetByScan(PadMapping.Scan.SOUTH, labels = false))
        assertEquals("a", PadMapping.defaultTargetByScan(PadMapping.Scan.EAST, labels = false))
        assertEquals("a", PadMapping.defaultTargetByScan(PadMapping.Scan.SOUTH, labels = true))
        assertEquals("b", PadMapping.defaultTargetByScan(PadMapping.Scan.EAST, labels = true))
        assertEquals("l", PadMapping.defaultTargetByScan(PadMapping.Scan.L1, labels = false))
        assertNull(PadMapping.defaultTargetByScan(306, labels = false))
    }

    @Test
    fun `the player's own binding wins over the dictionary`() {
        val mapping = mapOf(PadSources.scan(305) to "y")
        assertEquals("y", PadMapping.resolveButton(mapping, labels = false, scanCode = 305, keyCode = PadMapping.Key.BUTTON_B))
        // a key the player did not touch: the dictionary
        assertEquals("b", PadMapping.resolveButton(mapping, labels = false, scanCode = 0, keyCode = PadMapping.Key.BUTTON_A))
        // the key code is the fallback when there is no scan code
        val byKey = mapOf(PadSources.key(PadMapping.Key.BUTTON_A) to "plus")
        assertEquals("plus", PadMapping.resolveButton(byKey, labels = false, scanCode = 0, keyCode = PadMapping.Key.BUTTON_A))
    }

    @Test
    fun `a disabled source is no button at all`() {
        val mapping = mapOf(PadSources.scan(314) to PadSources.DISABLED)
        assertTrue(PadMapping.isDisabled(mapping, 314, PadMapping.Key.BUTTON_SELECT))
        assertNull(PadMapping.resolveButton(mapping, labels = false, scanCode = 314, keyCode = PadMapping.Key.BUTTON_SELECT))
    }

    @Test
    fun `binding a key moves it and frees the target it took`() {
        val mapping = mapOf(PadSources.scan(305) to "a")
        val moved = PadMapping.withBinding(mapping, PadSources.scan(307), "a")
        assertEquals("a", moved[PadSources.scan(307)])
        assertNull(moved[PadSources.scan(305)])   // the old one let go
    }

    @Test
    fun `a conflict names both sides`() {
        val mapping = mapOf(PadSources.scan(305) to "a", PadSources.scan(307) to "b")
        val conflict = PadMapping.conflict(mapping, PadSources.scan(309), "a")
        assertNull(conflict?.sourceTarget)   // the new source was not bound to anything
        assertEquals(PadSources.scan(305), conflict?.targetSource)
        // swapping gives the target to the new source and lets the old one go
        val swapped = PadMapping.swapBinding(mapping, PadSources.scan(309), "a")
        assertEquals("a", swapped[PadSources.scan(309)])
        assertFalse(swapped.containsKey(PadSources.scan(305)))
    }

    @Test
    fun `an axis binding is a half axis`() {
        val mapping = mapOf(PadSources.axis(17, 1f) to "zl")
        assertEquals("zl", PadMapping.resolveAxis(mapping, 17, 0.9f))
        assertNull(PadMapping.resolveAxis(mapping, 17, -0.9f))
        assertTrue(PadMapping.hasAxisBinding(mapping, 17))
        assertFalse(PadMapping.hasAxisBinding(mapping, 18))
    }

    @Test
    fun `the sources we know`() {
        assertTrue(PadSources.isKnown("scan:305"))
        assertTrue(PadSources.isKnown("key:96"))
        assertTrue(PadSources.isKnown("axis:17:+"))
        assertFalse(PadSources.isKnown("scan:"))
        assertFalse(PadSources.isKnown("nonsense"))
        assertFalse(PadSources.isKnown("axis:0:+"))   // the sticks are not capturable
    }
}
