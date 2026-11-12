package com.nx.android.domain.porting

import com.nx.android.domain.GameConfig

/** One accepted build attempt. Paths are private app paths; the ID is stable for late callbacks and navigation. */
data class BuildSession(
    val id: String,
    val game: String,
    val phase: PortPhase,
    val output: String,
    val startedAt: Long,
    val seconds: Long = 0,
    val config: GameConfig? = null,
    val stage: String = "",
    val progress: Float = 0f,
    val legacy: Boolean = false,
    val logAvailable: Boolean = true,
) {
    val terminal: Boolean get() = phase in setOf(PortPhase.DONE, PortPhase.FAILED, PortPhase.CANCELLED, PortPhase.INTERRUPTED)
}
