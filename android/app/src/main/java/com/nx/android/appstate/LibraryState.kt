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
import com.nx.android.domain.PackMover
import com.nx.android.domain.Ported
import com.nx.android.domain.StorageUsage
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
                   private val portsDir: File, private val workRoot: File,
                   private val cardPortsDirs: () -> List<File> = { emptyList() }) {

    var games by mutableStateOf(listOf<File>()); private set

    /** The finished port of a game this app ported (source path → pack); re-checked against the disk at every scan */
    var ports by mutableStateOf(mapOf<String, Ported>()); private set

    /** 登记过的数据包路径（源文件 → 包）：卡不在时那个包够不着，路径还记得 */
    private var lastPackPaths: Map<String, String> = HashMap()

    /**
     * 记住的包：源文件 → 路径与 title ID。
     *
     * 路径与 title ID 都要记：包被搬到卡上以后，原来那条路径没了，只有 title ID 能把它找回来
     * （游戏文件的名字里未必有 title ID——下载器改过名的文件就没有）。
     */
    private var remembered: Map<String, Remembered> = HashMap()

    /** 记住的一个包 */
    private data class Remembered(val path: String, val title: String)

    /** 正在搬移的游戏的源文件路径（搬移期间不能对它再动） */
    private val moving = HashSet<String>()

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
            ports = HashMap()
            remembered = HashMap()
            for (k in o.keys()) {
                val v = o.get(k)
                // 新格式：{"path": …, "title": …}；旧格式：一条路径
                val path = if (v is JSONObject) v.optString("path") else v as? String ?: continue
                val title = if (v is JSONObject) v.optString("title") else ""
                if (path.isEmpty()) continue
                remembered = remembered + (k to Remembered(path, title))
                lastPackPaths = lastPackPaths + (k to path)
                FolderScanner.portFromPack(File(path))?.let { ports = ports + (k to it) }
            }
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
            val started = System.currentTimeMillis()
            val real = extras.map(::File).filter { it.isFile }
            var found = (if (FAKE_DATA) fakeGames() else emptyList()) + real
            found = found.distinctBy { it.absolutePath }.sortedBy { it.name.lowercase() }
            // 卡上的包先扫，私有的后扫：私有的那份优先（重建总是先落在那里，卡上那份可能是旧的）
            val byTitle = HashMap<String, Ported>()
            val byName = HashMap<String, Ported>()      // 按包名（档案派生，稳）也能找回它
            for (d in cardPortsDirs()) {
                val idx = FolderScanner.outIndex(d)
                byTitle.putAll(idx)
                idx.values.forEach { byName[it.pack.name] = it }
            }
            val own = FolderScanner.outIndex(portsDir)
            byTitle.putAll(own)
            own.values.forEach { byName[it.pack.name] = it }
            val matched = HashMap<String, Ported>()
            for (f in found) {
                // 记住的那个包：路径还在就按它重新认（它可能刚被搬到卡上，路径变了）
                val rem = remembered[f.absolutePath]
                val fresh = rem?.let { FolderScanner.portFromPack(File(it.path)) }
                if (fresh != null) {
                    matched[f.absolutePath] = fresh
                    continue
                }
                // 记住的那个不在了（它被搬到卡上了）：按包名在各个卷上找回它那份
                val byPackName = rem?.let { byName[File(it.path).name] }
                if (byPackName != null) {
                    matched[f.absolutePath] = byPackName
                    remembered = remembered + (f.absolutePath to Remembered(byPackName.pack.absolutePath, byPackName.titleId))
                    continue
                }
                // 再按 title ID 找（游戏文件的名字里可能有）
                val id = FolderScanner.titleIdIn(f.name) ?: rem?.title?.takeIf { it.isNotEmpty() }
                id?.let { byTitle[it] }?.let { matched[f.absolutePath] = it }
            }
            games = found
            ports = matched
            scanning = false
            savePorts()      // 找回的包换了地方：把它记下来
            // 扫一遍要多久（多卷扫描不许变慢）：读清单 + stat，不读内容
            android.util.Log.i("nx2a", "library scan: ${System.currentTimeMillis() - started} ms, " +
                    "${byTitle.size} packs on ${portsDirs().size} volumes")
        }.start()
    }

    /** 端口目录：私有的一个，加上插着的可移动卡上的那些 */
    fun portsDirs(): List<File> = listOf(portsDir) + cardPortsDirs()

    /** App 私有存储里那个端口目录（包都在这儿，除非玩家把它搬到了卡上） */
    fun internalPortsDir(): File = portsDir

    /**
     * 记住的包路径（一个个包，不是按游戏分的）。
     *
     * 卡不在时那个包够不着，路径还记得——占用统计要把这种「够不着」如实说出来，
     * 不能当成 0。
     */
    fun rememberedPackPaths(): List<String> = lastPackPaths.values.toList()

    /**
     * 库里每个游戏的占用来源：工作区与它的数据包。
     *
     * 占用统计照着这个清单去量（见 `appstate/StorageUsageState.kt`）：一项 = 一个游戏，
     * 项里若干段 = 若干目录（工作区 / 数据包）。每个卷上可能各有一份数据包，够不着的
     * 那份（卡不在）标成「够不着」，不当成 0。
     */
    fun usageItems(games: List<File>): List<StorageUsage.Item> {
        val items = ArrayList<StorageUsage.Item>()
        val usedIds = HashSet<String>()
        val claimedPacks = HashSet<String>()
        val claimedWork = HashSet<String>()

        for (f in games) {
            val title = titleIdOf(f)
            val parts = ArrayList<StorageUsage.Part>()
            if (!title.isNullOrEmpty()) {
                val work = File(workRoot, "${title}_build")
                parts += StorageUsage.Part(StorageUsage.Role.WORKSPACE, work.absolutePath)
                claimedWork += work.absolutePath
            }
            for (p in packsOf(f)) {
                parts += StorageUsage.Part(StorageUsage.Role.PACK, p.absolutePath)
                claimedPacks += p.absolutePath
            }
            for (p in unreachablePacks(f)) {
                parts += StorageUsage.Part(StorageUsage.Role.PACK, p.absolutePath, state = StorageUsage.State.UNKNOWN)
            }
            // 同一个 title ID 在库里出现两次时，第二个用源文件路径当标识（免得两项混成一项）
            var id = title?.takeIf { it.isNotEmpty() } ?: f.absolutePath
            if (!usedIds.add(id)) id = f.absolutePath
            items += StorageUsage.Item(
                id = id,
                name = FolderScanner.labelFor(f, portOf(f)),
                icon = portOf(f)?.icon?.absolutePath.orEmpty(),
                // 快照要是不变的值：这里给一份拷贝，别把还在拼的列表挂出去
                parts = parts.toList(),
            )
        }

        // 兜底：占了地方却不对应库里任何游戏的东西（源文件被手动删掉后留下的包、
        // 认不出是哪个游戏的工作区）。它们没人管，数字也该有人报。
        val orphans = ArrayList<StorageUsage.Part>()
        for (d in portsDirs()) {
            for (p in d.listFiles() ?: emptyArray()) {
                if (!p.isDirectory || p.absolutePath in claimedPacks) continue
                orphans += StorageUsage.Part(StorageUsage.Role.PACK, p.absolutePath)
            }
        }
        for (w in workRoot.listFiles() ?: emptyArray()) {
            if (!w.isDirectory || w.absolutePath in claimedWork) continue
            orphans += StorageUsage.Part(StorageUsage.Role.WORKSPACE, w.absolutePath)
        }
        if (orphans.isNotEmpty()) {
            items += StorageUsage.Item(id = StorageUsage.ORPHAN_ID, name = "", parts = orphans.toList())
        }
        return items
    }

    /** 这个游戏的包现在在哪：私有的目录，还是卡上那个（重建跟着它走） */
    fun packDirOf(f: File): File? = portOf(f)?.pack?.parentFile

    /** 这个游戏在各个卷上的数据包（每个卷可能各有一份） */
    fun packsOf(f: File): List<File> {
        val id = titleIdOf(f)
        // 不知道 title ID（下载器改过名的文件），就只认「记住的那个包」：
        // 认不出就是认不出，不能把别的游戏的包算成这个游戏的（删游戏时会把它们一起删掉）
        if (id.isNullOrEmpty()) return listOfNotNull(portOf(f)?.pack)
        val out = ArrayList<File>()
        for (d in portsDirs()) {
            for (p in d.listFiles() ?: emptyArray()) {
                if (!p.isDirectory || !p.name.endsWith("-data")) continue
                val pid = FolderScanner.manifestTitleId(p) ?: continue
                if (!pid.equals(id, ignoreCase = true)) continue
                out += p
            }
        }
        return out
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
        // 卡不在时够不着的包：先算出来（forget 之后那条登记就没了）
        val unreachable = unreachablePacks(f)
        val result = GameCleanup.delete(plan)
        // 登记最后摘：删到一半被系统打断，游戏还在库里，玩家可以再删一次
        // 有东西没删掉时也留着登记：那点残留还在库里管得到，不至于变成没人管的孤儿
        val clean = result.failed.isEmpty() && unreachable.isEmpty()
        if (clean) forget(f)
        return GameCleanup.Result(
            outcome = if (clean) GameCleanup.Outcome.DONE else GameCleanup.Outcome.FAILED,
            workspaceKnown = plan.workspaceKnown,
            // 卡不在时，卡上的那份够不着：不能当成删干净了
            failed = result.failed + unreachable,
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
        val packs = packsOf(f)
        return GameCleanup.plan(titleIdOf(f), packs, workRoot, portsDirs(), keepPartials = port.running)
    }

    /** 清缓存前先看看要清什么：只有工作区与半成品包（数据包不碰） */
    fun planCache(f: File): GameCleanup.Plan =
        GameCleanup.plan(titleIdOf(f), emptyList(), workRoot, portsDirs(), keepPartials = port.running)

    /**
     * 把一个游戏的包搬到另一个卷（可移动卡上 App 自己的目录），或者搬回来。
     *
     * 搬移的规矩在 PackMover 里：落位成功之后才删源包；失败时源包原地不动，游戏照常能玩。
     * 搬移期间这个游戏不许再动（再搬一次或删掉它都会把那一次弄坏）。
     *
     * @param onProgress 进度回报（每抄完一个文件一次）：界面用它显示进度条
     */
    fun movePack(f: File, targetDir: File, onProgress: (PackMover.Progress) -> Unit = {}): PackMover.Result {
        val pack = portOf(f)?.pack ?: return PackMover.Result.fail("the game has no pack")
        if (f.absolutePath in moving) return PackMover.Result.fail("the pack is being moved already")
        moving += f.absolutePath
        try {
            val r = PackMover.move(pack, targetDir, onProgress)
            if (r.ok) {
                val moved = File(targetDir, pack.name)
                lastPackPaths = lastPackPaths + (f.absolutePath to moved.absolutePath)
                // 记住的那个包换了地方：登记跟着换（否则库认不到它）
                FolderScanner.portFromPack(moved)?.let {
                    remembered = remembered + (f.absolutePath to Remembered(moved.absolutePath, it.titleId))
                    ports = ports + (f.absolutePath to it)
                }
                savePorts()
                rescan()
            }
            return r
        } finally {
            moving -= f.absolutePath
        }
    }

    /** 这个游戏现在能不能搬它的数据包：正在构建 / 正在运行 / 正在搬都不能（返回原因），能搬返回 null */
    fun moveBlocked(f: File): GameCleanup.Outcome? =
        LibraryLogic.moveBlocked(
            portRunning = port.running, portGameFile = port.gameFile, gamePath = f.absolutePath,
            playerRunning = player.running, playerTitleId = player.titleId, titleId = titleIdOf(f),
            moving = f.absolutePath in moving)

    /** 卡不在时够不着的包：路径还记得，那个卷却不在了（删不到，要说实话） */
    fun unreachablePacks(f: File): List<File> {
        val path = lastPackPaths[f.absolutePath] ?: return emptyList()
        val p = File(path)
        if (p.exists()) return emptyList()
        val root = FolderScanner.volumeRootOf(p) ?: return emptyList()
        if (root.isDirectory) return emptyList()   // 卷在着，只是这个包没了（玩家自己删的）
        return listOf(p)
    }

    /** 这个游戏的 title ID：文件名里的优先，没有就用数据包清单里的；包够不着时用记住的那个 */
    fun titleIdOf(f: File): String? =
        FolderScanner.titleIdIn(f.name)
            ?: portOf(f)?.titleId?.takeIf { it.isNotEmpty() }
            ?: remembered[f.absolutePath]?.title?.takeIf { it.isNotEmpty() }

    /** 忘记一个游戏：它不再在库里登记，也不再有产物的登记 */
    private fun forget(f: File) {
        extras = extras - f.absolutePath
        val a = org.json.JSONArray()
        for (p in extras) a.put(p)
        prefs.putArray(LIB_EXTRA, a)
        ports = ports - f.absolutePath
        remembered = remembered - f.absolutePath
        lastPackPaths = lastPackPaths - f.absolutePath
        savePorts()      // 剩下的那些按新格式写回去（路径 + title ID）
        rescan()
    }

    /** A port came out done: the source and its data pack stay together */
    fun noteDone(source: String, pack: String) {
        if (source.isEmpty() || pack.isEmpty()) return
        lastPackPaths = lastPackPaths + (source to pack)
        FolderScanner.portFromPack(File(pack))?.let {
            remembered = remembered + (source to Remembered(pack, it.titleId))
            ports = ports + (source to it)
            savePorts()
        }
    }

    /** 把记住的那些包写回去：路径与 title ID 一起（包被搬走后，靠 title ID 或包名把它找回来） */
    private fun savePorts() {
        val o = JSONObject()
        for ((source, rem) in remembered) {
            o.put(source, JSONObject()
                .put("path", rem.path)
                .put("title", ports[source]?.titleId ?: rem.title))
        }
        prefs.putJson(LIB_PORTS, o)
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
