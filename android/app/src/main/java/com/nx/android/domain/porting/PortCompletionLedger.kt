package com.nx.android.domain.porting

data class PortCompletion(
    val game: String,
    val phase: PortPhase,
    val output: String,
    val startedAt: Long,
    val id: String = "",
)

/** Bounded, restartable record of completed task identities. */
class PortCompletionLedger(initial: Collection<String> = emptyList(), private val limit: Int = 40) {
    private val ids = LinkedHashSet(initial.filter(String::isNotEmpty).takeLast(limit))

    @Synchronized
    fun contains(id: String): Boolean = id in ids

    @Synchronized
    fun record(id: String) {
        if (id.isEmpty()) return
        ids += id
        while (ids.size > limit) ids.remove(ids.first())
    }

    @Synchronized
    fun snapshot(): List<String> = ids.toList()
}

/** Applies each terminal task result once; callers can run it from a Service without a UI collector. */
class PortCompletionProcessor(private val ledger: PortCompletionLedger) {
    @Synchronized
    fun complete(task: PortCompletion, apply: () -> Unit): Boolean {
        if (task.phase !in setOf(PortPhase.DONE, PortPhase.FAILED, PortPhase.CANCELLED, PortPhase.INTERRUPTED)) return false
        val key = task.id.ifEmpty { "${task.startedAt}:${task.game}" }
        if (ledger.contains(key)) return false
        apply()
        ledger.record(key)
        return true
    }
}
