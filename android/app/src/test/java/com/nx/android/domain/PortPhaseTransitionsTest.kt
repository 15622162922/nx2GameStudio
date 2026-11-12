package com.nx.android.domain

import com.nx.android.domain.porting.PortPhase
import com.nx.android.domain.porting.PortPhaseTransitions
import org.junit.Assert.assertEquals
import org.junit.Test

class PortPhaseTransitionsTest {
    @Test
    fun `finish records success or failure only from running`() {
        assertEquals(PortPhase.DONE, PortPhaseTransitions.finish(PortPhase.RUNNING, hasArtifact = true))
        assertEquals(PortPhase.FAILED, PortPhaseTransitions.finish(PortPhase.RUNNING, hasArtifact = false))
        assertEquals(PortPhase.CANCELLED, PortPhaseTransitions.finish(PortPhase.CANCELLED, hasArtifact = true))
    }

    @Test
    fun `cancel and process interruption are terminal and require a running task or marker`() {
        assertEquals(PortPhase.CANCELLED, PortPhaseTransitions.cancel(PortPhase.RUNNING))
        assertEquals(PortPhase.DONE, PortPhaseTransitions.cancel(PortPhase.DONE))
        assertEquals(PortPhase.INTERRUPTED, PortPhaseTransitions.interrupted(PortPhase.IDLE, markerPresent = true))
        assertEquals(PortPhase.IDLE, PortPhaseTransitions.interrupted(PortPhase.IDLE, markerPresent = false))
    }
}
