package com.nx.android.domain.porting

/** Lifecycle of the single foreground port task. */
enum class PortPhase { IDLE, RUNNING, DONE, FAILED, CANCELLED, INTERRUPTED }

object PortPhaseTransitions {
    fun finish(current: PortPhase, hasArtifact: Boolean): PortPhase =
        if (current != PortPhase.RUNNING) current else if (hasArtifact) PortPhase.DONE else PortPhase.FAILED

    fun cancel(current: PortPhase): PortPhase =
        if (current == PortPhase.RUNNING) PortPhase.CANCELLED else current

    fun interrupted(current: PortPhase, markerPresent: Boolean): PortPhase =
        if (markerPresent && current in setOf(PortPhase.IDLE, PortPhase.RUNNING)) PortPhase.INTERRUPTED else current
}
