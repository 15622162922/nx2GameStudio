package com.nx.android.domain

import com.nx.android.domain.porting.PortCompletionLedger
import com.nx.android.domain.porting.PortCompletion
import com.nx.android.domain.porting.PortCompletionProcessor
import com.nx.android.domain.porting.PortPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortCompletionLedgerTest {
    @Test
    fun `the completion identity survives reloading and repeated callbacks`() {
        val first = PortCompletionLedger()
        first.record("123:game.nsp")

        val restored = PortCompletionLedger(first.snapshot())

        assertTrue(restored.contains("123:game.nsp"))
        assertFalse(restored.contains("124:game.nsp"))
        restored.record("123:game.nsp")
        assertEquals(listOf("123:game.nsp"), restored.snapshot())
    }

    @Test
    fun `the ledger keeps only the most recent bounded set`() {
        val ledger = PortCompletionLedger(limit = 2)

        ledger.record("one")
        ledger.record("two")
        ledger.record("three")

        assertEquals(listOf("two", "three"), ledger.snapshot())
        assertFalse(ledger.contains("one"))
    }

    @Test
    fun `terminal handling runs without a screen subscriber and duplicate callbacks apply once`() {
        val processor = PortCompletionProcessor(PortCompletionLedger())
        val task = PortCompletion("game.nsp", PortPhase.CANCELLED, "", 42L)
        var applied = 0

        val first = processor.complete(task) { applied++ }
        val duplicate = processor.complete(task) { applied++ }

        assertTrue(first)
        assertFalse(duplicate)
        assertEquals(1, applied)
    }
}
