package com.nx.android.domain

/** The guided setup's steps */
enum class ObStep { WELCOME, ENV, KEYS, GAMES, DONE }

/** What the machine can do right now: the facts the gates read */
data class ObFacts(
    val storageOk: Boolean,
    val freeGB: Long,
    val neededGB: Long,
    val envReady: Boolean,
    val keysPath: String,
    val keysOk: Boolean,
    val keysSkipped: Boolean,
    val folderSet: Boolean,
)

/**
 * The onboarding's pure rules: which step may advance, and where a saved step lands when the machine changed
 * under it. Free of Android, so the tests run on the JVM.
 */
object OnboardingLogic {

    fun canAdvance(step: ObStep, f: ObFacts): Boolean = when (step) {
        ObStep.WELCOME -> f.storageOk && f.freeGB >= f.neededGB
        ObStep.ENV -> f.envReady
        ObStep.KEYS -> f.keysOk || f.keysSkipped
        ObStep.GAMES -> f.folderSet
        ObStep.DONE -> false
    }

    /**
     * What the step's own action may do. The last step's action is the way into the library, and that is
     * always allowed; the other steps move on to the next one, so their answer is the next one's premise.
     */
    fun canAct(step: ObStep, f: ObFacts): Boolean =
        if (step == ObStep.DONE) true else canAdvance(step, f)

    /**
     * Where a saved step lands now: back to the earliest one whose premise no longer holds. The permission
     * taken away lands on Welcome, an environment that went missing on Env, keys that stopped being valid on
     * Keys; a step before all that stays.
     */
    fun revalidate(saved: ObStep, f: ObFacts): ObStep {
        var s = saved
        if (!f.storageOk) return ObStep.WELCOME
        if (s.ordinal > ObStep.ENV.ordinal && !f.envReady) s = ObStep.ENV
        if (s.ordinal > ObStep.KEYS.ordinal && !(f.keysOk || f.keysSkipped)) s = ObStep.KEYS
        return s
    }
}
