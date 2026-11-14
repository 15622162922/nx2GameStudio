package com.nx.android.input

/**
 * 查表规则：物理键 → 游戏键。
 *
 * 顺序是「玩家自己绑的 → 出厂字典」：玩家没动过的地方，落回出厂字典（与改动前逐键一致）。
 * 出厂字典有两套：
 * - 按位置（默认）：Switch 的布局 —— 下面那颗是 B，右边那颗是 A；
 * - 按标签：键上印的 A 就是游戏的 A（Xbox 布局的手柄最常见）。**标签属于手柄，不属于游戏**，
 *   所以这一项是每只手柄自己的选择，不是档案里的游戏设置。
 *
 * 这份规则与宿主那边的一份是同一套（nxnative/dm/android/pad_map.h）：宿主按事件的 device 查同一张表。
 */
object PadMapping {

    /** Android 的 keyCode（KeyEvent）：出厂字典用的就是它们。 */
    object Key {
        const val DPAD_UP = 19
        const val DPAD_DOWN = 20
        const val DPAD_LEFT = 21
        const val DPAD_RIGHT = 22
        const val BUTTON_A = 96
        const val BUTTON_B = 97
        const val BUTTON_X = 99
        const val BUTTON_Y = 100
        const val BUTTON_L1 = 102
        const val BUTTON_R1 = 103
        const val BUTTON_L2 = 104
        const val BUTTON_R2 = 105
        const val BUTTON_THUMBL = 106
        const val BUTTON_THUMBR = 107
        const val BUTTON_START = 108
        const val BUTTON_SELECT = 109
    }

    /** Linux 手柄规范的扫描码：BTN_SOUTH 是下面那颗，BTN_EAST 右边，BTN_NORTH 上面，BTN_WEST 左边。 */
    object Scan {
        const val SOUTH = 304
        const val EAST = 305
        const val NORTH = 307
        const val WEST = 308
        const val L1 = 310
        const val R1 = 311
        const val L2 = 312
        const val R2 = 313
        const val SELECT = 314
        const val START = 315
        const val L3 = 317
        const val R3 = 318
    }

    /** 一个键占了两个目标时的冲突：源原来绑的、目标原来是谁的。 */
    data class Conflict(val sourceTarget: String?, val targetSource: String?)

    /**
     * 出厂字典：**NS 的位置**（游戏嘴里的 A/B/X/Y 指的就是 NS 手柄上的四个位置：
     * X 在上、Y 在左、A 在右、B 在下）。跟手柄上印的字无关——印字只是贴纸。
     *
     * 所以 NS 印字的手柄零配置全对；Xbox 印字的手柄按位置走（游戏要 A 就是右边那颗），
     * 想按印字来的玩家在改键页自己绑（或者点那个一键预设）。
     */
    private val POSITION: Map<Int, String> = mapOf(
        Key.BUTTON_A to "b", Key.BUTTON_B to "a", Key.BUTTON_X to "x", Key.BUTTON_Y to "y",
        Key.BUTTON_L1 to "l", Key.BUTTON_R1 to "r", Key.BUTTON_L2 to "zl", Key.BUTTON_R2 to "zr",
        Key.BUTTON_THUMBL to "ls", Key.BUTTON_THUMBR to "rs",
        Key.BUTTON_START to "plus", Key.BUTTON_SELECT to "minus",
        Key.DPAD_UP to "up", Key.DPAD_DOWN to "down", Key.DPAD_LEFT to "left", Key.DPAD_RIGHT to "right",
    )

    /**
     * 按印字的出厂字典（弃用：面键按位置还是按标签的老开关，恒 false）。
     * 留着是为了老档案读得出来也不作数——它按 Xbox 的印字：下 A、右 B、上 Y、左 X。
     */
    private val LABEL: Map<Int, String> = POSITION + mapOf(
        Key.BUTTON_A to "a", Key.BUTTON_B to "b", Key.BUTTON_X to "y", Key.BUTTON_Y to "x",
    )

    /**
     * 出厂字典：这个 keyCode 是哪个游戏键（不是手柄键就是 null）。
     *
     * @param labels 弃用：面键按位置还是按标签的老开关（恒 false）。面键的语义现在由逐键绑定表达。
     */
    fun defaultTarget(keyCode: Int, labels: Boolean = false): String? =
        (if (labels) LABEL else POSITION)[keyCode]

    /**
     * 某个游戏键的默认来源（出厂字典：扫描码优先，没有就 keyCode）：设置页显示默认绑定用。
     *
     * 这里走的是**运行时那一条路**（扫描码 → 它的 keyCode → 出厂字典），所以面板上写的
     * 默认来源与游戏真的收到的一致（X/Y 那对按方位与按名字对调，别处算出来的那一套不作数）。
     */
    fun defaultSourceFor(target: String, labels: Boolean = false): String? {
        for (scan in intArrayOf(
            Scan.SOUTH, Scan.EAST, Scan.NORTH, Scan.WEST, Scan.L1, Scan.R1, Scan.L2, Scan.R2,
            Scan.SELECT, Scan.START, Scan.L3, Scan.R3,
        )) {
            if (defaultTarget(Face.keyCodeOf(scan), labels) == target) return PadSources.scan(scan)
        }
        for (key in POSITION.keys) if (POSITION[key] == target) return PadSources.key(key)
        return null
    }

    /** 查表：玩家自己绑的先（扫描码优先，keyCode 兜底），没有就落回出厂字典。 */
    fun resolveButton(
        mapping: Map<String, String>,
        labels: Boolean,
        scanCode: Int,
        keyCode: Int,
    ): String? {
        if (scanCode > 0) {
            val bound = mapping[PadSources.scan(scanCode)]
            if (bound != null) return if (bound == PadSources.DISABLED) null else bound
        }
        val bound = mapping[PadSources.key(keyCode)]
        if (bound != null) return if (bound == PadSources.DISABLED) null else bound
        return defaultTarget(keyCode, labels)
    }

    /** 这个键被禁用了吗（禁用是「在这儿，但什么都不做」）。 */
    fun isDisabled(mapping: Map<String, String>, scanCode: Int, keyCode: Int): Boolean {
        if (scanCode > 0 && mapping[PadSources.scan(scanCode)] == PadSources.DISABLED) return true
        return mapping[PadSources.key(keyCode)] == PadSources.DISABLED
    }

    /** 模拟轴的绑定：命中就是那个目标键，没绑就是 null。 */
    fun resolveAxis(mapping: Map<String, String>, axis: Int, value: Float): String? {
        if (!PadSources.isCapturableAxis(axis)) return null
        val bound = mapping[PadSources.axis(axis, value)] ?: return null
        return if (bound == PadSources.DISABLED) null else bound
    }

    /** 这个轴有玩家自己的绑定吗（宿主那边：有就不再用它的默认触发）。 */
    fun hasAxisBinding(mapping: Map<String, String>, axis: Int): Boolean =
        PadSources.isCapturableAxis(axis) &&
            (mapping.containsKey(PadSources.axis(axis, -1f)) || mapping.containsKey(PadSources.axis(axis, 1f)))

    /** 绑一个键：它原来的绑定让位，别人占着的目标被腾出来（新绑的赢）。 */
    fun withBinding(mapping: Map<String, String>, source: String, target: String): Map<String, String> {
        if (!PadSources.isKnown(source) || !PadTargets.isValid(target)) return mapping
        val out = mapping.toMutableMap()
        out.entries.removeAll { it.value == target && it.key != source }
        out[source] = target
        return out
    }

    /** 这个绑定会不会撞车：源原来绑着别的、目标原来是谁的。 */
    fun conflict(mapping: Map<String, String>, source: String, target: String): Conflict? {
        val sourceTarget = mapping[source]?.takeIf { it != target && it != PadSources.DISABLED }
        val targetSource = if (target == PadSources.DISABLED) null else
            mapping.entries.firstOrNull {
                it.key != source && it.value == target && it.value != PadSources.DISABLED
            }?.key
        return if (sourceTarget == null && targetSource == null) null else Conflict(sourceTarget, targetSource)
    }

    /** 禁用一个键。 */
    fun disableBinding(mapping: Map<String, String>, source: String): Map<String, String> =
        if (PadSources.isKnown(source)) mapping + (source to PadSources.DISABLED) else mapping

    /** 交换两个键的绑定：新的那个拿到目标，旧的那个拿到新的原来的目标（没有就松开）。 */
    fun swapBinding(mapping: Map<String, String>, source: String, target: String): Map<String, String> {
        val conflict = conflict(mapping, source, target) ?: return mapping
        val otherSource = conflict.targetSource ?: return mapping
        return mapping.toMutableMap().apply {
            put(source, target)
            val oldTarget = conflict.sourceTarget
            if (oldTarget != null) put(otherSource, oldTarget) else remove(otherSource)
        }
    }

    /** 去掉一条绑定（那个键落回出厂字典）。 */
    fun withoutBinding(mapping: Map<String, String>, source: String): Map<String, String> =
        if (mapping.containsKey(source)) mapping - source else mapping

    /**
     * 面键的**方位**：手柄上那四颗键各在哪儿。
     *
     * Linux 的扫描码说的是位置（BTN_NORTH 是上面那颗），Android 的 keyCode 说的是它在那儿叫什么
     * （BTN_NORTH 在 Android 那边叫 BUTTON_X）。方位是扫描码的事，所以面板上的菱形按它画。
     */
    object Face {
        /** 上面那颗（打印 Y 的那颗在 Xbox 布局的手柄上）。 */
        const val TOP = Scan.NORTH
        /** 左边那颗。 */
        const val LEFT = Scan.WEST
        /** 右边那颗。 */
        const val RIGHT = Scan.EAST
        /** 下面那颗。 */
        const val BOTTOM = Scan.SOUTH

        /** 那颗键在 Android 那边是什么 keyCode（系统的键位表：扫描码与 keyCode 是一对）。 */
        fun keyCodeOf(scanCode: Int): Int = when (scanCode) {
            Scan.SOUTH -> Key.BUTTON_A
            Scan.EAST -> Key.BUTTON_B
            Scan.NORTH -> Key.BUTTON_X
            Scan.WEST -> Key.BUTTON_Y
            Scan.L1 -> Key.BUTTON_L1
            Scan.R1 -> Key.BUTTON_R1
            Scan.L2 -> Key.BUTTON_L2
            Scan.R2 -> Key.BUTTON_R2
            Scan.SELECT -> Key.BUTTON_SELECT
            Scan.START -> Key.BUTTON_START
            Scan.L3 -> Key.BUTTON_THUMBL
            Scan.R3 -> Key.BUTTON_THUMBR
            else -> 0
        }
    }
}
