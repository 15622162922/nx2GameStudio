package com.nx.android.domain

/** Update state/actions shared by the settings row and the root update popup. */
enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

interface UpdateController {
    val phase: UpdatePhase
    val latest: String
    val mine: String
    val notes: String
    val later: Boolean

    fun checkNow()
    fun postpone()
}
