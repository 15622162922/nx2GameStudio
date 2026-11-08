package com.nx.android.appstate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.PrefsStore
import com.nx.android.data.PrefsStore.Companion.LEGACY_EXPERIMENTAL
import com.nx.android.data.PrefsStore.Companion.LEGACY_FPS60
import com.nx.android.data.PrefsStore.Companion.LEGACY_TOUCH
import com.nx.android.data.PrefsStore.Companion.LIB_CONFIGS
import com.nx.android.data.PrefsStore.Companion.LIB_FOLDER
import com.nx.android.data.PrefsStore.Companion.LIB_EXTRA
import com.nx.android.data.PrefsStore.Companion.LIB_PORTS
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

    var folder by mutableStateOf(prefs.str(LIB_FOLDER))
    var games by mutableStateOf(listOf<File>()); private set

    /** The APK of a game this app ported (source path → APK); re-checked against the disk at every scan */
    var apks by mutableStateOf(mapOf<String, File>()); private set

    var scanning by mutableStateOf(false); private set

    /** 行视图里选中的游戏（hero），随列表伸缩 */
    var heroIndex by mutableStateOf(0); private set

    /** 打开配置面板的游戏 */
    var selected by mutableStateOf<File?>(null)

    /** 构建页聚焦的游戏：库的「▶ 构建」把它带过去（NX2Apple 的 activeBuildID 对应物） */
    var buildsTarget by mutableStateOf<File?>(null)

    /** 从构建页登记的游戏文件（库文件夹之外也能进库），路径集合，prefs 持久化 */
    var extras by mutableStateOf(setOf<String>()); private set

    private var configs = HashMap<String, GameConfig>()

    // ---- 调试假数据：往库里并 15 条看排版效果，看完把 FAKE_DATA 改回 false ----

    private val FAKE_DATA = true

    private val fakeStatus = HashMap<String, GameStatus>()
    private val fakeApks = HashMap<String, File>()

    /** 15 条假游戏（名字/状态各异；路径是假的，只为看网格、hero 与磨砂背景的排版） */
    private fun fakeGames(): List<File> {
        val names = listOf(
            "塞尔达传说 旷野之息 [0100000000010000]" to GameStatus.DONE,
            "马力欧卡丁车8 豪华版 [0100000000018000]" to GameStatus.NOT_PORTED,
            "集合啦 动物森友会 [0100000000020000]" to GameStatus.DONE,
            "宝可梦 剑 [0100000000030000]" to GameStatus.NOT_PORTED,
            "异度神剑3 [0100000000040000]" to GameStatus.PORTING,
            "密特罗德 生存恐惧 [0100000000050000]" to GameStatus.NOT_PORTED,
            "星之卡比 探索发现 [0100000000060000]" to GameStatus.DONE,
            "火焰纹章 风花雪月 [0100000000070000]" to GameStatus.NOT_PORTED,
            "超级马力欧 奥德赛 [0100000000080000]" to GameStatus.DONE,
            "斯普拉遁3 [0100000000090000]" to GameStatus.NOT_PORTED,
            "塞尔达传说 王国之泪 [0100000000100000]" to GameStatus.DONE,
            "牧场物语 橄榄镇 [0100000000110000]" to GameStatus.NOT_PORTED,
            "怪物猎人 崛起 [0100000000120000]" to GameStatus.NOT_PORTED,
            "桃太郎电铁 [0100000000130000]" to GameStatus.NOT_PORTED,
            "茶杯头 [0100000000140000]" to GameStatus.PORTING,
        )
        val out = ArrayList<File>()
        for ((name, status) in names) {
            val f = File("/sdcard/FakeLibrary/$name.nsp")
            fakeStatus[f.absolutePath] = status
            if (status == GameStatus.DONE) fakeApks[f.absolutePath] = File("/sdcard/Switch/Ports/$name.apk")
            out.add(f)
        }
        return out
    }

    init {
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
        try {
            val a = prefs.array(LIB_EXTRA)
            extras = (0 until a.length()).map { a.getString(it) }.toSet()
        } catch (e: Exception) {
        }
    }

    /** 构建页选过的游戏文件登记进库（构建完成后库里有它的卡片） */
    fun registerExtra(f: File) {
        extras = extras + f.absolutePath
        val a = org.json.JSONArray()
        for (p in extras) a.put(p)
        prefs.putArray(LIB_EXTRA, a)
    }

    fun chooseFolder(path: String) {
        folder = path
        prefs.put(LIB_FOLDER, path)
        rescan()
    }

    /** 行视图选中一个游戏（带过滤后的列表，越界收回） */
    fun selectHero(index: Int, count: Int) {
        heroIndex = if (count > 0) index.coerceIn(0, count - 1) else 0
    }

    /** The folder's game files, and the APK each one has (the remembered ones, the data packs' matches) */
    fun rescan() {
        scanning = true
        Thread {
            val scanned = FolderScanner.scan(File(folder))
            val real = (scanned + extras.map(::File)).filter { it.isFile }
            var found = (if (FAKE_DATA) fakeGames() else emptyList()) + real
            found = found.distinctBy { it.absolutePath }.sortedBy { it.name.lowercase() }
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

    fun statusOf(f: File): GameStatus {
        if (FAKE_DATA) fakeStatus[f.absolutePath]?.let { return it }
        return LibraryLogic.statusOf(f, apks, port.running, port.gameFile)
    }

    fun apkOf(f: File): File? {
        if (FAKE_DATA) fakeApks[f.absolutePath]?.let { return it }
        return apks[f.absolutePath]?.takeIf { it.isFile }
    }

    /**
     * 删除一个移植产物：产物 APK 与同目录的数据包目录（**存档在数据包里，随之删除**）。游戏源文件不动。
     * 删完刷新状态：该游戏回落为未移植。
     */
    fun deleteOutcome(f: File): Boolean {
        val apk = apks[f.absolutePath] ?: return false
        val pack = File(apk.parentFile, apk.nameWithoutExtension + "-data")
        val apkGone = apk.delete()
        val packGone = !pack.exists() || pack.deleteRecursively()
        apks = apks - f.absolutePath
        prefs.putJson(LIB_PORTS, JSONObject(apks.mapValues { it.value.absolutePath }))
        rescan()
        return apkGone && packGone
    }

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

    /** 库的列表（扫描 ∪ 登记，已按名字排序） */
    fun visible(): List<File> = games
}
