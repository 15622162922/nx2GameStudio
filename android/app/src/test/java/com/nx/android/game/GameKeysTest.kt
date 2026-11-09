package com.nx.android.game

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音量键归 Android：游戏的音频走媒体音量组（AAUDIO_USAGE_GAME），系统的音量条就是游戏的音量。
 * 应用把音量键消费掉，系统就永远收不到它——音量条不出现，游戏的声音也一点不变。
 */
class GameKeysTest {

    @Test
    fun `the volume keys stay with Android`() {
        assertFalse(forwardsKeyToGame(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(forwardsKeyToGame(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertFalse(forwardsKeyToGame(KeyEvent.KEYCODE_VOLUME_MUTE))
    }

    @Test
    fun `every other key keeps going to the game`() {
        assertTrue(forwardsKeyToGame(KeyEvent.KEYCODE_BACK))
        assertTrue(forwardsKeyToGame(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(forwardsKeyToGame(KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(forwardsKeyToGame(KeyEvent.KEYCODE_A))
        assertTrue(forwardsKeyToGame(KeyEvent.KEYCODE_SPACE))
        assertTrue(forwardsKeyToGame(KeyEvent.KEYCODE_ESCAPE))
    }
}
