package com.nx.android.appstate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.PrefsStore
import com.nx.android.data.PrefsStore.Companion.LEGACY_EXPERIMENTAL
import com.nx.android.data.PrefsStore.Companion.LEGACY_FPS60
import com.nx.android.data.PrefsStore.Companion.LEGACY_TOUCH
import com.nx.android.data.PrefsStore.Companion.LIB_CONFIGS
import com.nx.android.data.PrefsStore.Companion.LIB_FILTER
import com.nx.android.data.PrefsStore.Companion.LIB_FOLDER
import com.nx.android.data.PrefsStore.Companion.LIB_PORTS
import com.nx.android.data.PrefsStore.Companion.LIB_VIEW
import com.nx.android.domain.FolderScanner
import com.nx.android.domain.GameConfig
import com.nx.android.domain.GameStatus
import com.nx.android.domain.LibraryLogic
import org.json.JSONObject
import java.io.File

/**
 * The library: the game files of the chosen folder, what became of each one, and each game's own port options
 * (they follow the game now; the old app kept them global, and a game without its own entry keeps the old
 * defaults). A game counts as done when the port's APK is there — the ones this app ported are remembered by
 * source path, the ones from before are matched through the data packs' manifests (the title ID in the file's
 * name).
 */
class LibraryState(private val prefs: PrefsStore, private val port: PortState) {

    enum class Filter { ALL, NOT_PORTED, DONE }

    /** 库的两种视图：行（hero）与网格 */
    enum class View { ROW, GRID }

    var folder by mutableStateOf(prefs.str(LIB_FOLDER))
    var games by mutableStateOf(listOf<File>()); private set

    /** The APK of a game this app ported (source path → APK); re-checked against the disk at every scan */
    var apks by mutableStateOf(mapOf<String, File>()); private set

    var search by mutableStateOf("")
    var filter by mutableStateOf(Filter.ALL); private set
    var scanning by mutableStateOf(false); private set

    /** 行/网格双视图，选择被记住 */
    var view by mutableStateOf(View.ROW); private set

    /** 行视图里选中的游戏（hero），随列表伸缩 */
    var heroIndex by mutableStateOf(0); private set

    /** 打开配置面板的游戏 */
    var selected by mutableStateOf<File?>(null)

    private var configs = HashMap<String, GameConfig>()

    init {
        filter = Filter.entries.firstOrNull { it.name == prefs.str(LIB_FILTER) } ?: Filter.ALL
        view = View.entries.firstOrNull { it.name == prefs.str(LIB_VIEW) } ?: View.ROW
        try {
            val o = prefs.json(LIB_CONFIGS)
            for (k in o.keys()) {
                val c = o.getJSONObject(k)
                configs[k] = GameConfig(c.optBoolean("touch"), c.optBoolean("fps60"), c.optBoolean("exp"))
            }
        } catch (e: Exception) {
        }
        try {
            val o = prefs.json(LIB_PORTS)
            apks = o.keys().asSequence().associateWith { File(o.getString(it)) }
        } catch (e: Exception) {
        }
    }

    fun chooseFolder(path: String) {
        folder = path
        prefs.put(LIB_FOLDER, path)
        rescan()
    }

    fun chooseFilter(f: Filter) {
        filter = f
        prefs.put(LIB_FILTER, f.name)
    }

    fun chooseView(v: View) {
        view = v
        prefs.put(LIB_VIEW, v.name)
    }

    /** 行视图选中一个游戏（带过滤后的列表，越界收回） */
    fun selectHero(index: Int, count: Int) {
        heroIndex = if (count > 0) index.coerceIn(0, count - 1) else 0
    }

    /** The folder's game files, and the APK each one has (the remembered ones, the data packs' matches) */
    fun rescan() {
        scanning = true
        Thread {
            val found = FolderScanner.scan(File(folder))
            val byTitle = FolderScanner.outIndex(File(prefs.out))
            val matched = HashMap(apks.filterKeys { name -> found.any { it.absolutePath == name } })
            for (f in found) {
                val id = FolderScanner.titleIdIn(f.name) ?: continue
                byTitle[id]?.let { matched[f.absolutePath] = it }
            }
            games = found
            apks = matched
            scanning = false
        }.start()
    }

    fun statusOf(f: File): GameStatus =
        LibraryLogic.statusOf(f, apks, port.running, port.gameFile)

    fun apkOf(f: File): File? = apks[f.absolutePath]?.takeIf { it.isFile }

    /** A port came out done: the source and its APK stay together */
    fun noteDone(source: String, apk: String) {
        if (source.isEmpty() || apk.isEmpty()) return
        apks = apks + (source to File(apk))
        prefs.putJson(LIB_PORTS, JSONObject(apks.mapValues { it.value.absolutePath }))
    }

    /** The port options a game carries: its own, or the old app's globals as the defaults */
    fun configOf(f: File): GameConfig =
        configs[f.name] ?: GameConfig(
            touch = prefs.bool(LEGACY_TOUCH),
            fps60 = prefs.bool(LEGACY_FPS60),
            experimental = prefs.bool(LEGACY_EXPERIMENTAL),
        )

    fun setConfig(f: File, c: GameConfig) {
        configs[f.name] = c
        val o = JSONObject()
        for ((k, v) in configs) {
            o.put(k, JSONObject().put("touch", v.touch).put("fps60", v.fps60).put("exp", v.experimental))
        }
        prefs.putJson(LIB_CONFIGS, o)
    }

    /** The cards the grid shows: the filter's statuses, the search's names */
    fun visible(): List<File> {
        val s = search.trim()
        return games.filter { f ->
            (s.isEmpty() || f.name.contains(s, ignoreCase = true)) && when (filter) {
                Filter.ALL -> true
                Filter.NOT_PORTED -> statusOf(f) != GameStatus.DONE
                Filter.DONE -> statusOf(f) == GameStatus.DONE
            }
        }
    }
}
