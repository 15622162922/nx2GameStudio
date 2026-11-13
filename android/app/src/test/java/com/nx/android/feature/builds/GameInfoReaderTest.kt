package com.nx.android.feature.builds

import com.nx.android.domain.GameInfo
import com.nx.android.domain.GameInspector
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameInfoReaderTest {

    @Test
    fun `the same file is read once and the answer is kept`() = runTest {
        val inspector = FakeInspector()
        inspector.answers["/games/one.nsp"] = GameInfo(titleId = "0100AAAA00000000")
        val reader = GameInfoReader(inspector, backgroundScope)

        reader.read(File("/games/one.nsp"), "")
        runCurrent()
        assertEquals(1, inspector.calls.size)
        assertEquals("0100AAAA00000000", reader.info?.titleId)

        reader.read(File("/games/one.nsp"), "")
        runCurrent()
        // the answer was kept: the guest is not started again
        assertEquals(1, inspector.calls.size)
        // every read counts, the cached one included: the page reacts to each of them
        assertEquals(2, reader.reads)
        assertEquals("0100AAAA00000000", reader.info?.titleId)
    }

    @Test
    fun `an answer that arrives after another file was picked is dropped`() = runTest {
        val inspector = FakeInspector()
        val gate = CompletableDeferred<GameInfo?>()
        inspector.gates["/games/one.nsp"] = gate
        inspector.answers["/games/two.nsp"] = GameInfo(titleId = "0100BBBB00000000")
        val reader = GameInfoReader(inspector, backgroundScope)

        reader.read(File("/games/one.nsp"), "")
        runCurrent()
        reader.read(File("/games/two.nsp"), "")
        runCurrent()
        gate.complete(GameInfo(titleId = "0100AAAA00000000"))
        runCurrent()

        // the late answer belongs to the file the player left behind
        assertEquals("0100BBBB00000000", reader.info?.titleId)
    }

    @Test
    fun `clearing the target puts the answer away`() = runTest {
        val inspector = FakeInspector()
        inspector.answers["/games/one.nsp"] = GameInfo(titleId = "0100AAAA00000000")
        val reader = GameInfoReader(inspector, backgroundScope)

        reader.read(File("/games/one.nsp"), "")
        runCurrent()
        assertEquals("0100AAAA00000000", reader.info?.titleId)

        reader.clear()
        assertNull(reader.info)
        assertEquals(false, reader.reading)
    }

    private class FakeInspector : GameInspector {
        val calls = mutableListOf<String>()
        val answers = HashMap<String, GameInfo?>()
        val gates = HashMap<String, CompletableDeferred<GameInfo?>>()

        override suspend fun inspect(gameFile: String, keys: String): GameInfo? {
            calls += gameFile
            gates[gameFile]?.let { return it.await() }
            return answers[gameFile]
        }
    }
}
