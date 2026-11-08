package com.nx.android.data

import android.content.Context

/**
 * 帧生成的四个旋钮（纯值，JVM 可测）：运行前组进 env 数组交给宿主，运行中由抽屉经 JNI 即时下发。
 * 一个游戏一份，按 Title ID 记忆（FrameGenSettings 的 prefs 壳读写它）。
 */
data class FrameGenKnobs(var on: Boolean, var mult: Int, var target: Int, var flow: Int) {

    companion object {
        val MULTS = intArrayOf(2, 3, 4)
        val TARGETS = intArrayOf(0, 60, 90, 120)   // 0 = the game's own pace times the multiplier
        val FLOWS = intArrayOf(0, 25, 50, 75, 100) // 0 = auto (the game's own resolution)
        fun clampMult(v: Int) = if (v < 2) 2 else if (v > 4) 4 else v
        fun clampFlow(v: Int) = if (v <= 0) 0 else if (v < 25) 25 else if (v > 100) 100 else v
    }

    /** The env rows for the game's start (DM_LSFG*); the DLL row only when the private copy is there */
    fun envLines(dllPath: String?): Array<String> {
        if (!on) return emptyArray()
        val m = clampMult(mult); val f = clampFlow(flow)
        val rows = mutableListOf("DM_LSFG=1", "DM_LSFG_MULT=$m", "DM_LSFG_TARGET=${if (target < 0) 0 else target}", "DM_LSFG_FLOW=$f")
        if (dllPath != null) rows.add("DM_LSFG_DLL=$dllPath")
        return rows.toTypedArray()
    }
}

/**
 * 一个游戏的帧生成设置：prefs 壳（按 Title ID 的键），读写 [FrameGenKnobs]。
 */
class FrameGenSettings(private val prefs: PrefsStore, private val titleId: String) {

    private val base = "fg.${titleId.lowercase()}"

    fun load(): FrameGenKnobs = FrameGenKnobs(
        on = prefs.bool("$base.on"),
        mult = FrameGenKnobs.clampMult(prefs.str("$base.mult").toIntOrNull() ?: 2),
        target = (prefs.str("$base.target").toIntOrNull() ?: 0).coerceAtLeast(0),
        flow = FrameGenKnobs.clampFlow(prefs.str("$base.flow").toIntOrNull() ?: 0),
    )

    fun save(k: FrameGenKnobs) {
        prefs.put("$base.on", k.on)
        prefs.put("$base.mult", FrameGenKnobs.clampMult(k.mult).toString())
        prefs.put("$base.target", k.target.coerceAtLeast(0).toString())
        prefs.put("$base.flow", FrameGenKnobs.clampFlow(k.flow).toString())
    }
}
