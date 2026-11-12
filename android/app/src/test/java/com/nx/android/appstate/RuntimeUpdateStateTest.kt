package com.nx.android.appstate

import org.junit.Assert.*
import org.junit.Test

class RuntimeUpdateStateTest {
    @Test fun `duplicate taps and dismissal cannot release an active update`() {
        val state = RuntimeUpdateState()
        assertTrue(state.begin("/pack/Game-data", "Game"))
        assertFalse(state.begin("/pack/Other-data", "Other"))
        state.dismiss()
        assertTrue(state.running)
        assertEquals("/pack/Game-data", state.pack)
        state.finish(true)
        assertEquals(RuntimeUpdatePhase.SUCCEEDED, state.phase)
        state.dismiss()
        assertEquals(RuntimeUpdatePhase.IDLE, state.phase)
    }

    @Test fun `failure keeps the target for retry without a build task`() {
        val state = RuntimeUpdateState()
        state.begin("/pack/Game-data", "Game")
        state.finish(false)
        assertFalse(state.running)
        assertEquals(RuntimeUpdatePhase.FAILED, state.phase)
        assertTrue(state.begin(state.pack, state.name))
        assertTrue(state.running)
        assertFalse(state.interrupted)
    }

    @Test fun `a surviving pending marker restores a dismissible interruption prompt`() {
        val state = RuntimeUpdateState("/pack/Game-data")
        assertEquals(RuntimeUpdatePhase.FAILED, state.phase)
        assertTrue(state.interrupted)
        assertFalse(state.running)
        state.dismiss()
        assertEquals(RuntimeUpdatePhase.IDLE, state.phase)
        assertEquals("", state.pack)
    }
}
