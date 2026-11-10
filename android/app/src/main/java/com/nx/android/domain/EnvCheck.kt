package com.nx.android.domain

import org.json.JSONArray

/**
 * The environment's checks: the two that are cheap and the rows the doctor reports.
 *
 * The chip's verdict is the cheap ones only - the environment is prepared and the keys file is there - because
 * they are read from the filesystem and can be looked at whenever the screen comes back. The doctor's rows are
 * the expensive ones: they run a python inside the rootfs, so they are asked for on purpose.
 */
object EnvCheck {

    /** The doctor's row about the keys: the checklist has its own, cheaper row for them */
    const val KEYS_ROW = "prod.keys"

    /** What a check says */
    enum class Level { OK, HINT, BAD }

    /** The chip's state */
    enum class Chip { CHECKING, READY, NOT_READY }

    /** One line of the checklist */
    data class Row(val name: String, val level: Level, val detail: String, val fix: String = "")

    /**
     * The chip's verdict: the environment and the keys are the only two things it looks at.
     */
    fun chip(envReady: Boolean, keysOk: Boolean): Chip =
        if (envReady && keysOk) Chip.READY else Chip.NOT_READY

    /**
     * The doctor's rows, in the order it printed them.
     *
     * A row that is not required and did not pass is a hint, not a failure: the doctor itself only fails for
     * the required ones. The keys row is left out - the checklist shows its own, cheaper row for them.
     */
    fun rows(json: JSONArray): List<Row> {
        val out = mutableListOf<Row>()
        for (i in 0 until json.length()) {
            val r = json.optJSONObject(i) ?: continue
            val name = r.optString("name")
            if (name.isEmpty() || name == KEYS_ROW) continue
            val ok = r.optBoolean("ok")
            val required = r.optBoolean("required", true)
            out += Row(
                name,
                when {
                    ok -> Level.OK
                    required -> Level.BAD
                    else -> Level.HINT
                },
                r.optString("detail"),
                r.optString("fix"),
            )
        }
        return out
    }
}
