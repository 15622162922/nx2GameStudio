package com.nx.android.appstate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.PrefsStore
import com.nx.android.data.PrefsStore.Companion.LEGACY_EXPERIMENTAL
import com.nx.android.data.PrefsStore.Companion.LEGACY_FPS60
import com.nx.android.data.PrefsStore.Companion.LEGACY_TOUCH
import com.nx.android.data.PrefsStore.Companion.LIB_CONFIGS
import com.nx.android.data.PrefsStore.Companion.LIB_EXTRA
import com.nx.android.data.PrefsStore.Companion.LIB_PORTS
import com.nx.android.domain.FolderScanner
import com.nx.android.domain.GameCleanup
import com.nx.android.domain.GameConfig
import com.nx.android.domain.GameConfigJson
import com.nx.android.domain.GameStatus
import com.nx.android.domain.LibraryLogic
import com.nx.android.domain.Ported
import org.json.JSONObject
import java.io.File

/**
 * The library: the game files the player registered from the build page, what became of each one, and each
 * game's own port options (they follow the game now; the old app kept them global, and a game without its own
 * entry keeps the old defaults). A game counts as done when its pack and the host inside it are there — the
 * ones this app ported are remembered by source path, the ones from before are matched through the data
 * packs' manifests (the title ID in the file's name).
 *
 * [portsDir] is the app's own ports folder: the data packs the porter writes there, found by their title ID.
 * [workRoot] is the porter's work folder: the unpacked game and the shader work of every build, one folder per
 * title ID. Deleting a game takes both away.
 */
class LibraryState(private val prefs: PrefsStore, private val port: PortState, private val player: PlayerState,
                   private val portsDir: File, private val workRoot: File) {

    var games by mutableStateOf(listOf<File>()); private set

    /** The finished port of a game this app ported (source path → pack); re-checked against the disk at every scan */
    var ports by mutableStateOf(mapOf<String, Ported>()); private set

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
    // 假数据只在看排版时开（openspec 变更 library-ui-polish 的任务 1.1 已把它关掉）。

    private val FAKE_DATA = false

    private val fakeStatus = HashMap<String, GameStatus>()
    private val fakePorts = HashMap<String, Ported>()

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
            if (status == GameStatus.DONE) {
                val pack = File("/sdcard/Switch/Ports/${name}-data")
                fakePorts[f.absolutePath] = Ported(pack, File(pack, "host/libdmplay.so"), File(pack, "icon.png"),
                                                   FolderScanner.titleIdIn(name) ?: "", name)
            }
            out.add(f)
        }
        return out
    }

    init {
        try {
            val o = prefs.json(LIB_CONFIGS)
            for (k in o.keys()) {
                val c = o.getJSONObject(k)
                configs[k] = GameConfigJson.decode(c)
            }
        } catch (e: Exception) {
        }
        try {
            val o = prefs.json(LIB_PORTS)
            ports = o.keys().asSequence().mapNotNull { k -> FolderScanner.portFromPack(File(o.getString(k)))?.let { k to it } }.toMap()
        } catch (e: Exception) {
        }
        try {
            val a = prefs.array(LIB_EXTRA)
            extras = (0 until a.length()).map { a.getString(it) }.toSet()
        } catch (e: Exception) {
        }
    }

    /** 构建页选过的游戏文件登记进库（登记完就进库，库页立刻看得见） */
    fun registerExtra(f: File) {
        extras = extras + f.absolutePath
        val a = org.json.JSONArray()
        for (p in extras) a.put(p)
        prefs.putArray(LIB_EXTRA, a)
        rescan()
    }

    /** 行视图选中一个游戏（带过滤后的列表，越界收回） */
    fun selectHero(index: Int, count: Int) {
        heroIndex = if (count > 0) index.coerceIn(0, count - 1) else 0
    }

    /** 登记过的游戏文件，和每个游戏自己的数据包（记住的那些，与数据包清单对上的那些） */
    fun rescan() {
        scanning = true
        Thread {
            val real = extras.map(::File).filter { it.isFile }
            var found = (if (FAKE_DATA) fakeGames() else emptyList()) + real
            found = found.distinctBy { it.absolutePath }.sortedBy { it.name.lowercase() }
            val byTitle = FolderScanner.outIndex(portsDir)
            val matched = HashMap(ports.filterKeys { name -> found.any { it.absolutePath == name } })
            for (f in found) {
                val id = FolderScanner.titleIdIn(f.name) ?: continue
                byTitle[id]?.let { matched[f.absolutePath] = it }
            }
            games = found
            ports = matched
            scanning = false
        }.start()
    }

    fun statusOf(f: File): GameStatus {
        if (FAKE_DATA) fakeStatus[f.absolutePath]?.let { return it }
        return LibraryLogic.statusOf(f, ports, port.running, port.gameFile, failedBefore(f))
    }

    /**
     * 这个游戏上一次是不是构建失败了：构建历史里它最新的一条记录是失败。
     * 历史最多留 20 条，很久以前的失败会被挤出去——那之后它就只是「未移植」，不影响删除。
     */
    fun failedBefore(f: File): Boolean {
        val path = f.absolutePath
        val rec = port.history.firstOrNull { it.game == path } ?: return false
        return rec.phase == PortState.Phase.FAILED
    }

    /** The game's finished port (its pack), null when it has none */
    fun portOf(f: File): Ported? {
        if (FAKE_DATA) fakePorts[f.absolutePath]?.let { return it }
        return ports[f.absolutePath]
    }

    /**
     * 删掉一个游戏：把它移出游戏库，删掉它的构建数据（数据包、工作区、半成品包）。
     * 游戏源文件是玩家的，不碰。正在构建或正在运行时不动任何东西。
     */
    fun remove(f: File): GameCleanup.Result {
        removalBlocked(f)?.let { return GameCleanup.Result(it) }
        val plan = planRemoval(f)
        val result = GameCleanup.delete(plan)
        // 登记最后摘：删到一半被系统打断，游戏还在库里，玩家可以再删一次
        // 有东西没删掉时也留着登记：那点残留还在库里管得到，不至于变成没人管的孤儿
        if (result.failed.isEmpty()) forget(f)
        return GameCleanup.Result(
            outcome = if (result.failed.isEmpty()) GameCleanup.Outcome.DONE else GameCleanup.Outcome.FAILED,
            workspaceKnown = plan.workspaceKnown,
            failed = result.failed,
        )
    }

    /**
     * 只清掉构建缓存：工作区与半成品包。数据包、存档、库里的登记都不动——游戏照常能玩。
     * 正在构建或正在运行时不动任何东西。
     */
    fun clearCache(f: File): GameCleanup.Result {
        removalBlocked(f)?.let { return GameCleanup.Result(it) }
        val plan = planCache(f)
        val result = GameCleanup.delete(plan)
        return GameCleanup.Result(
            outcome = if (result.failed.isEmpty()) GameCleanup.Outcome.DONE else GameCleanup.Outcome.FAILED,
            workspaceKnown = plan.workspaceKnown,
            failed = result.failed,
        )
    }

    /** 这个游戏现在能不能删/清缓存：正在构建 / 正在运行都不能（返回原因），能删返回 null */
    fun removalBlocked(f: File): GameCleanup.Outcome? =
        LibraryLogic.removalBlocked(
            portRunning = port.running, portGameFile = port.gameFile, gamePath = f.absolutePath,
            playerRunning = player.running, playerTitleId = player.titleId, titleId = titleIdOf(f))

    /** 删除前先看看要删什么（界面在 IO 线程上调它）：数据包 + 工作区 + 半成品包 */
    fun planRemoval(f: File): GameCleanup.Plan {
        val pack = portOf(f)
        return GameCleanup.plan(titleIdOf(f), pack?.pack, workRoot, portsDir,
            keepPartials = port.running)
    }

    /** 清缓存前先看看要清什么：只有工作区与半成品包（数据包不碰） */
    fun planCache(f: File): GameCleanup.Plan =
        GameCleanup.plan(titleIdOf(f), null, workRoot, portsDir, keepPartials = port.running)

    /** 这个游戏的 title ID：文件名里的优先，没有就用数据包清单里的 */
    private fun titleIdOf(f: File): String? =
        FolderScanner.titleIdIn(f.name) ?: portOf(f)?.titleId?.takeIf { it.isNotEmpty() }

    /** 忘记一个游戏：它不再在库里登记，也不再有产物的登记 */
    private fun forget(f: File) {
        extras = extras - f.absolutePath
        val a = org.json.JSONArray()
        for (p in extras) a.put(p)
        prefs.putArray(LIB_EXTRA, a)
        ports = ports - f.absolutePath
        prefs.putJson(LIB_PORTS, JSONObject(ports.mapValues { it.value.pack.absolutePath }))
        rescan()
    }

    /** A port came out done: the source and its data pack stay together */
    fun noteDone(source: String, pack: String) {
        if (source.isEmpty() || pack.isEmpty()) return
        FolderScanner.portFromPack(File(pack))?.let {
            ports = ports + (source to it)
            prefs.putJson(LIB_PORTS, JSONObject(ports.mapValues { p -> p.value.pack.absolutePath }))
        }
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
            o.put(k, GameConfigJson.encode(v))
        }
        prefs.putJson(LIB_CONFIGS, o)
    }

    /** 库的列表（登记过的游戏，已按名字排序） */
    fun visible(): List<File> = games
}
