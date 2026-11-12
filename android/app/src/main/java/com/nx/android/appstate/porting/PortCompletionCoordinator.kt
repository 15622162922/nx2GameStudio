package com.nx.android.appstate.porting

import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.PortState
import com.nx.android.data.PrefsStore
import com.nx.android.domain.porting.PortCompletionLedger
import com.nx.android.domain.porting.PortPhase
import com.nx.android.domain.porting.PortCompletion
import com.nx.android.domain.porting.PortCompletionProcessor
import javax.inject.Inject
import javax.inject.Singleton

/** Applies a terminal build result exactly once, independent of whether any screen is observing it. */
@Singleton
class PortCompletionCoordinator @Inject constructor(
    private val library: LibraryState,
    private val prefs: PrefsStore,
) {
    private val completed = run {
        val values = ArrayList<String>()
        val stored = prefs.json(PrefsStore.PORT_COMPLETED)
        for (key in stored.keys()) stored.optString(key).takeIf(String::isNotEmpty)?.let(values::add)
        PortCompletionLedger(values)
    }
    private val processor = PortCompletionProcessor(completed)

    @Synchronized
    fun complete(record: PortState.Record) {
        processor.complete(PortCompletion(record.game, record.phase, record.out, record.startedAt,
            record.id.takeUnless { record.legacy }.orEmpty())) {
            when (record.phase) {
                PortPhase.DONE -> library.noteDone(record.game, record.out)
                PortPhase.FAILED,
                PortPhase.CANCELLED,
                PortPhase.INTERRUPTED -> library.noteFailed(record.game)
                else -> Unit
            }
        }
        val json = org.json.JSONObject()
        completed.snapshot().forEachIndexed { index, value -> json.put(index.toString(), value) }
        prefs.putJson(PrefsStore.PORT_COMPLETED, json)
    }
}
