package com.nx.android.domain.environment

/** Operations that prepare and validate the bundled Android porter environment. */
interface EnvironmentSetup {
    val ready: Boolean
    val lastCheck: String
    fun unpack(onStep: (String) -> Unit)
    fun reset()
    fun refreshTools()
    fun patchInterpreters()
}
