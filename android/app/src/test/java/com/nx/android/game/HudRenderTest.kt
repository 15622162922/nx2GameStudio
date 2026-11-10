package com.nx.android.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 性能 HUD 的算术与渲染：帧率的差分、帧时间、温度的分档与单位、充电时的功耗行 */
class HudRenderTest {

    private fun host(game: Long, gen: Long, spanNs: Long, spans: Long) = HostSample(game, gen, spanNs, spans, 16_666_667)

    @Test
    fun `the frame rate comes from the difference between two host samples`() {
        // 60 frames of 16.666 ms each, then 60 more
        val a = host(100, 0, 1_000_000_020, 60)
        val b = host(160, 0, 2_000_000_040, 120)
        val s = HudData.stats(a, b, 1000)
        assertEquals(60.0, s.gameFps!!, 0.01)
        assertEquals(60.0, s.presentedFps!!, 0.01)
        assertEquals(16.666, s.frameMs!!, 0.01)   // the mean of the intervals between the game's frames
    }

    @Test
    fun `the generated frames count towards what reaches the screen`() {
        val a = host(100, 0, 1_000_000_020, 60)
        val b = host(160, 120, 2_000_000_040, 120)
        val s = HudData.stats(a, b, 1000)
        assertEquals(60.0, s.gameFps!!, 0.01)
        assertEquals(180.0, s.presentedFps!!, 0.01)
    }

    @Test
    fun `the first sample has nothing to compare with`() {
        val s = HudData.stats(null, host(10, 0, 1_000_000, 1), 1000)
        assertNull(s.gameFps); assertNull(s.presentedFps); assertNull(s.frameMs)
    }

    @Test
    fun `a game that is not running shows no frame rate`() {
        val a = host(100, 0, 1_000_000_020, 60)
        val b = host(100, 0, 1_000_000_020, 60)
        val s = HudData.stats(a, b, 1000)
        assertNull(s.gameFps); assertNull(s.presentedFps)
    }

    @Test
    fun `the fields are drawn in a fixed order and the unselected ones are left out`() {
        val sample = HudSample(gameFps = 60.0, cpuPercent = 42f, tempC = 40f)
        val lines = HudData.render(sample, setOf(HudField.TEMP, HudField.FPS, HudField.CPU))
        assertEquals(listOf("60 fps", "CPU 42%", "40.0°C"), lines.map { it.text })
        assertEquals(HudRole.ACCENT, lines[0].role)
        assertEquals(HudRole.GOOD, lines[2].role)
    }

    @Test
    fun `the frame generation names its engine and shows both rates`() {
        val sample = HudSample(gameFps = 60.0, presentedFps = 120.0)
        val lines = HudData.render(sample, setOf(HudField.FPS), engineLabel = "LSFG x2")
        assertEquals("LSFG x2 60 → 120 fps", lines[0].text)
    }

    @Test
    fun `the temperature grades good, warn and bad, and converts to Fahrenheit`() {
        assertEquals(HudRole.GOOD, HudData.tempRole(41.9f))
        assertEquals(HudRole.WARN, HudData.tempRole(42f))
        assertEquals(HudRole.WARN, HudData.tempRole(46.9f))
        assertEquals(HudRole.BAD, HudData.tempRole(47f))
        assertEquals(HudRole.PLAIN, HudData.tempRole(null))
        val sample = HudSample(tempC = 40f)
        assertEquals("40.0°C", HudData.render(sample, setOf(HudField.TEMP))[0].text)
        assertEquals("104.0°F", HudData.render(sample, setOf(HudField.TEMP), fahrenheit = true)[0].text)
    }

    @Test
    fun `the charging battery shows a marker, not watts`() {
        val charging = HudSample(powerW = 3.2f, capacity = 80, charging = true)
        val line = HudData.render(charging, setOf(HudField.POWER), chargingText = "Charging")[0]
        assertEquals("Charging 80%", line.text)
        assertEquals(HudRole.ACCENT, line.role)
        val running = HudSample(powerW = 6.2f, capacity = 80)
        assertEquals("6.2 W 80%", HudData.render(running, setOf(HudField.POWER))[0].text)
    }

    @Test
    fun `a reading the device does not offer shows a placeholder, not a made-up number`() {
        val lines = HudData.render(HudSample(), setOf(HudField.FPS, HudField.CPU, HudField.GPU, HudField.APP_RAM, HudField.SYS_RAM, HudField.TEMP))
        assertEquals(listOf("—", "CPU —", "GPU —", "RAM —", "MEM —", "—"), lines.map { it.text })
    }

    @Test
    fun `the fields survive a round trip through the preferences`() {
        val fields = setOf(HudField.FPS, HudField.GPU, HudField.POWER)
        assertEquals(fields, HudField.of(HudField.keys(fields).split(",")))
        assertTrue(HudField.of(listOf("NOPE")).isEmpty())
    }
}
