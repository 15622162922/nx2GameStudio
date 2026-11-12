package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The guided setup's gates: which step may advance, and where "later" lands when the machine changed */
class OnboardingLogicTest {

    private class FakeProgressStore(var stepName: String = "", var deferred: Boolean = false) {
        fun restore(facts: ObFacts) = OnboardingLogic.resume(stepName, deferred, facts)
    }

    private fun facts(
        storage: Boolean = true,
        free: Long = 10,
        env: Boolean = true,
        keysOk: Boolean = true,
        keysSkipped: Boolean = false,
    ) = ObFacts(
        storageOk = storage, freeGB = free, neededGB = 2,
        envReady = env, keysPath = if (keysOk) "/sdcard/switch/prod.keys" else "",
        keysOk = keysOk, keysSkipped = keysSkipped,
    )

    // ---- the gates ---------------------------------------------------------------------------------------------

    @Test
    fun `welcome needs the permission and the space`() {
        assertTrue(OnboardingLogic.canAdvance(ObStep.WELCOME, facts(free = 10)))
        assertFalse(OnboardingLogic.canAdvance(ObStep.WELCOME, facts(storage = false)))
        assertFalse(OnboardingLogic.canAdvance(ObStep.WELCOME, facts(free = 1)))
    }

    @Test
    fun `env needs the environment`() {
        assertTrue(OnboardingLogic.canAdvance(ObStep.ENV, facts(env = true)))
        assertFalse(OnboardingLogic.canAdvance(ObStep.ENV, facts(env = false)))
    }

    @Test
    fun `keys pass when valid or skipped, not otherwise`() {
        assertTrue(OnboardingLogic.canAdvance(ObStep.KEYS, facts(keysOk = true)))
        assertTrue(OnboardingLogic.canAdvance(ObStep.KEYS, facts(keysOk = false, keysSkipped = true)))
        assertFalse(OnboardingLogic.canAdvance(ObStep.KEYS, facts(keysOk = false)))
    }

    @Test
    fun `done never advances by its gate`() {
        assertFalse(OnboardingLogic.canAdvance(ObStep.DONE, facts()))
    }

    // ---- the action of a step ------------------------------------------------------------------------------------

    @Test
    fun `the last step's action is always available`() {
        // the way into the library: the player may always take it once they are there
        assertTrue(OnboardingLogic.canAct(ObStep.DONE, facts()))
        assertTrue(OnboardingLogic.canAct(ObStep.DONE, facts(storage = false, env = false, keysOk = false)))
        // the gate itself still says no: the last step has no next step
        assertFalse(OnboardingLogic.canAdvance(ObStep.DONE, facts()))
    }

    @Test
    fun `acting on a step follows that step's own gate`() {
        for (s in ObStep.entries) {
            if (s == ObStep.DONE) continue
            assertEquals(OnboardingLogic.canAdvance(s, facts()), OnboardingLogic.canAct(s, facts()))
        }
        assertFalse(OnboardingLogic.canAct(ObStep.ENV, facts(env = false)))
        assertTrue(OnboardingLogic.canAct(ObStep.ENV, facts(env = true)))
        assertFalse(OnboardingLogic.canAct(ObStep.WELCOME, facts(storage = false)))
        assertFalse(OnboardingLogic.canAct(ObStep.KEYS, facts(keysOk = false)))
    }

    // ---- the revalidation of a saved step ----------------------------------------------------------------------

    @Test
    fun `a saved step whose premises hold stays`() {
        assertEquals(ObStep.KEYS, OnboardingLogic.revalidate(ObStep.KEYS, facts()))
        assertEquals(ObStep.DONE, OnboardingLogic.revalidate(ObStep.DONE, facts()))
    }

    @Test
    fun `deferred onboarding resumes from fake persisted step after rechecking prerequisites`() {
        val store = FakeProgressStore(stepName = "DONE", deferred = true)
        assertEquals(ObStep.ENV, store.restore(facts(env = false)))
        store.stepName = "KEYS"
        assertEquals(ObStep.WELCOME, store.restore(facts(storage = false)))
    }

    @Test
    fun `completed or invalid persisted progress starts at welcome`() {
        assertEquals(ObStep.WELCOME, FakeProgressStore(stepName = "DONE").restore(facts()))
        assertEquals(ObStep.WELCOME, FakeProgressStore(stepName = "removed-step", deferred = true).restore(facts()))
    }

    @Test
    fun `a taken-away permission lands on welcome`() {
        assertEquals(ObStep.WELCOME, OnboardingLogic.revalidate(ObStep.DONE, facts(storage = false)))
    }

    @Test
    fun `a lost environment pulls a later step back to env`() {
        assertEquals(ObStep.ENV, OnboardingLogic.revalidate(ObStep.DONE, facts(env = false)))
        assertEquals(ObStep.ENV, OnboardingLogic.revalidate(ObStep.KEYS, facts(env = false)))
    }

    @Test
    fun `invalid keys pull a later step back to keys, a skip does not`() {
        assertEquals(ObStep.KEYS, OnboardingLogic.revalidate(ObStep.DONE, facts(keysOk = false)))
        assertEquals(ObStep.DONE, OnboardingLogic.revalidate(ObStep.DONE, facts(keysOk = false, keysSkipped = true)))
    }

    @Test
    fun `the earliest failing step wins`() {
        // the permission and the environment are both gone: welcome, not env
        assertEquals(ObStep.WELCOME, OnboardingLogic.revalidate(ObStep.DONE, facts(storage = false, env = false)))
    }
}
