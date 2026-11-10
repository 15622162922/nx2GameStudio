package com.nx.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * The tool's saved settings: every key in one place, and the two paths the whole UI reads (the keys file and
 * the output folder) as observable state — a write goes to disk at once, a read is a Compose read. The key
 * names and their meanings are the old app's, unchanged: an update keeps its settings.
 */
class PrefsStore(ctx: Context) {

    companion object {
        // the two paths (the old app's keys, unchanged)
        const val KEYS = "keys"
        const val OUT = "out"
        const val OUT_DEFAULT = "/sdcard/Switch/Ports"
        // the old app's global port options: a game without its own config keeps them as the defaults
        const val LEGACY_TOUCH = "touch"
        const val LEGACY_FPS60 = "fps60"
        const val LEGACY_EXPERIMENTAL = "experimental"
        // the app's own language: "" follows the phone, "zh" and "en" are the player's own choice
        const val LANG = "lang"
        // the library's memory
        const val LIB_CONFIGS = "lib.configs"
        const val LIB_PORTS = "lib.ports"
        const val LIB_EXTRA = "lib.extra"
        // the guided setup
        const val OB_STEP = "ob.step"
        const val OB_LATER = "ob.later"
        const val OB_COMPLETED = "ob.completed"
        // the open-source notice: the player has seen it (once is enough)
        const val OSS_NOTICE_SHOWN = "oss.notice.shown"
        // the port: the interruption marker and the history
        const val PORT_RUNNING = "port.running"
        const val PORT_HISTORY = "port.history"
        // the update check: the version the player closed the notice for (empty = none)
        const val UPDATE_IGNORED = "update.ignored"
    }

    val prefs: SharedPreferences = ctx.getSharedPreferences("nx2a", Context.MODE_PRIVATE)

    /** prod.keys 的路径：库的发起前提、设置页、引导都读它；picker 与引导写它（一份，就是这一个） */
    var keys by mutableStateOf(prefs.getString(KEYS, "") ?: "")
        private set

    /** 移植产物（数据包）的输出目录 */
    var out by mutableStateOf(prefs.getString(OUT, OUT_DEFAULT) ?: OUT_DEFAULT)
        private set

    fun writeKeys(path: String) {
        keys = path.trim()
        prefs.edit().putString(KEYS, keys).apply()
    }

    fun writeOut(path: String) {
        out = path.trim()
        prefs.edit().putString(OUT, out).apply()
    }

    // ---- 通用读写（引导与库的记忆；键名见常量） ----

    fun str(key: String, def: String = ""): String = prefs.getString(key, def) ?: def

    fun bool(key: String, def: Boolean = false): Boolean = prefs.getBoolean(key, def)

    fun put(key: String, value: String?) = prefs.edit().putString(key, value).apply()

    fun put(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    // ---- JSON（历史、按游戏配置、产物映射） ----

    fun json(key: String): JSONObject = try {
        JSONObject(prefs.getString(key, "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    fun putJson(key: String, o: JSONObject) = prefs.edit().putString(key, o.toString()).apply()

    fun array(key: String): JSONArray = try {
        JSONArray(prefs.getString(key, "[]") ?: "[]")
    } catch (e: Exception) {
        JSONArray()
    }

    fun putArray(key: String, a: JSONArray) = prefs.edit().putString(key, a.toString()).apply()
}
