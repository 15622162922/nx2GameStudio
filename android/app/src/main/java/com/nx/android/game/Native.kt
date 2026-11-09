package com.nx.android.game

import android.content.Context
import android.view.Surface

/**
 * The game's JNI surface (one libdmplay.so per game: the host statically linked with its entry, see
 * nxnative/dm/android/jni_android.c). Loaded with System.load of the copy in the app's private folder
 * (sdcard is noexec), started once per process — the host ends the process with the game, a second
 * start in one process is refused on the native side.
 */
object Native {
    @JvmStatic external fun start(pack: String, internal: String, titleId: String?, dpi: Int, env: Array<String>, ctx: Context): Boolean
    @JvmStatic external fun setSurface(surface: Surface?)
    @JvmStatic external fun motion(source: Int, device: Int, action: Int, ids: IntArray, xs: FloatArray, ys: FloatArray, axis: FloatArray): Boolean
    @JvmStatic external fun key(device: Int, source: Int, code: Int, action: Int, meta: Int, repeat: Int): Boolean
    @JvmStatic external fun lifecycle(ev: Int)

    /** 运行中抽屉的帧生成：即时下发（DM_CTL 的 env 行走不到 JNI，这里直达宿主）；"<state> <reason>" 见 dm_framegen.h */
    @JvmStatic external fun frameGenSet(on: Boolean, mult: Int, target: Int, flow: Int)
    @JvmStatic external fun frameGenStatus(): String

    /** 运行中抽屉的宿主菜单行（画面与操作/存档）：what 0 overlay 1 opacity 2 rumble 3 fps 4 docked */
    @JvmStatic external fun menuGet(what: Int): Int
    @JvmStatic external fun menuSet(what: Int, v: Double)
    @JvmStatic external fun menuState(): String
    @JvmStatic external fun menuEditOpen()
    @JvmStatic external fun menuSaves(): String
    @JvmStatic external fun menuExport()
    @JvmStatic external fun menuImport(name: String): Boolean
    @JvmStatic external fun menuExportNote(): String

    /** 虚拟手柄（App 绘制层）：布局与实时状态（JSON，见 front_android.c 的 dm_pad_*） */
    @JvmStatic external fun padLayout(): String
    @JvmStatic external fun padState(): String

    /** 虚拟手柄层（虚拟手柄与 ☰ 菜单按钮是一层）：App 画不画它——不画时那一块不回答触摸，触摸还给游戏 */
    @JvmStatic external fun padLayer(drawn: Boolean)
}
