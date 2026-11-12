package com.nx.android.domain.porting

data class PortHistoryEntry(
    val game: String,
    val phase: String,
    val out: String,
    val startedAt: Long,
    val seconds: Long,
)

interface PortHistoryRepository {
    fun load(): List<PortHistoryEntry>
    fun save(entries: List<PortHistoryEntry>)
}
