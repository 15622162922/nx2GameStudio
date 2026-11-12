package com.nx.android.appstate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.domain.FolderScanner
import com.nx.android.domain.GameCleanup
import com.nx.android.domain.GameConfig
import com.nx.android.domain.library.GameConfigRepository
import com.nx.android.domain.library.GameLibraryRepository
import com.nx.android.domain.GameEntry
import com.nx.android.domain.GameStatus
import com.nx.android.domain.LibraryLogic
import com.nx.android.domain.PackMover
import com.nx.android.domain.Ported
import java.io.File

/**
 * The library: the games the player built, what became of each one, and each game's own port options.
 *
 * A card's identity is the game's **title ID**, not a file path: the source file may be moved away or deleted
 * (players do it to free space), and the game stays in the library — playable, with its pack.
 *
 * The card list is the union of two sources, merged by title ID:
 *  - **the data packs** in the app's own folders (the private one and the removable card's), read from their
 *    manifests;
 *  - **the work folders** the porter left behind (`<title ID>_build`) — a build that failed or whose pack was
 *    deleted still has its workspace, so the game stays in the library and the player can clean it up.
 *
 * Two small memories, neither of them an identity:
 *  - **the pack path** each title ID was last seen at — the storage page says "out of reach" for the pack on a
 *    card that is not plugged in;
 *  - **the source file** each title ID was last built from — the rebuild button uses it (the player does not have
 *    to pick the file again when it has not moved).
 *
 * [portsDir] is the app's own ports folder: the data packs the porter writes there, found by their title ID.
 * [workRoot] is the porter's work folder: the unpacked game and the shader work of every build, one folder per
 * title ID. Deleting a game takes both away.
 */
class LibraryState(private val port: PortState, private val player: PlayerState,
                   private val libraryRepository: GameLibraryRepository,
                   private val configRepository: GameConfigRepository) {

    /** 库里的卡：一个游戏一张 */
    var games by mutableStateOf(listOf<GameEntry>()); private set

    var scanning by mutableStateOf(false); private set

    /** 行视图里选中的游戏（hero），随列表伸缩 */
    var heroIndex by mutableStateOf(0); private set

    /** 打开配置面板的游戏 */
    var selected by mutableStateOf<GameEntry?>(null)

    /** 构建页聚焦的游戏：库的「▶ 构建」把它带过去（NX2Apple 的 activeBuildID 对应物） */
    var buildsTarget by mutableStateOf<File?>(null)

    private val moving = HashSet<String>()

    /** 行视图选中一个游戏（带过滤后的列表，越界收回） */
    fun selectHero(index: Int, count: Int) {
        heroIndex = if (count > 0) index.coerceIn(0, count - 1) else 0
    }

    // ---- 扫库（包与工作区，按 title ID 合成卡） ----------------------------------------------------------------------

    /**
     * 扫一遍：包（App 自己的目录）与工作区（移植器建的），按 title ID 合成卡。
     *
     * 源文件不在了不算没这个游戏：包或工作区还在，卡就在。
     */
    fun rescan() {
        scanning = true
        Thread {
            try {
                games = libraryRepository.scan()
                configRepository.migrateLegacyConfigs(games)
            } finally {
                scanning = false
            }
        }.start()
    }

    /** 端口目录：私有的一个，加上插着的可移动卡上的那些 */
    fun portsDirs(): List<File> = libraryRepository.portsDirs()

    /** App 私有存储里那个端口目录（包都在这儿，除非玩家把它搬到了卡上） */
    fun internalPortsDir(): File = libraryRepository.internalPortsDir()

    /** 这个游戏的包现在在哪：私有的目录，还是卡上那个（重建跟着它走） */
    fun packDirOf(e: GameEntry): File? = libraryRepository.packDirOf(e)

    /** 构建页拿的是源文件：按它找到那张卡的包目录（重建时新包落在那儿） */
    fun packDirOf(f: File): File? = libraryRepository.packDirOf(f, games)

    /** 这个游戏在各个卷上的数据包（每个卷可能各有一份） */
    fun packsOf(e: GameEntry): List<File> = libraryRepository.packsOf(e)

    /** 这个游戏的状态 */
    fun statusOf(e: GameEntry): GameStatus =
        LibraryLogic.statusOf(e, port.running, port.gameFile, port.startedAt)

    /** 构建页拿的是源文件：按它找到这张卡（找不到就当没构建过） */
    fun statusOf(f: File): GameStatus = entryOf(f)?.let { statusOf(it) } ?: GameStatus.NOT_PORTED

    /** 源文件对应的那张卡（它上次是从这个文件构建的） */
    fun entryOf(f: File): GameEntry? {
        val path = f.absolutePath
        return games.firstOrNull { it.sourcePath == path }
    }

    /** 这个游戏完成的移植（它的包） */
    fun portOf(e: GameEntry): Ported? = e.pack

    /** 构建页拿的是源文件：按它找到那张卡的包 */
    fun portOf(f: File): Ported? = entryOf(f)?.pack

    /**
     * 删掉一个游戏：把它移出游戏库，删掉它的构建数据（数据包、工作区、半成品包）。
     * 游戏源文件是玩家的，不碰。正在构建或正在运行时不动任何东西。
     */
    fun remove(e: GameEntry): GameCleanup.Result {
        removalBlocked(e)?.let { return GameCleanup.Result(it) }
        val plan = planRemoval(e)
        // 卡不在时够不着的包：先算出来（forget 之后那条登记就没了）
        val unreachable = libraryRepository.unreachablePacks(e)
        val result = GameCleanup.delete(plan)
        // 记忆最后摘：删到一半被系统打断，游戏还在库里，玩家可以再删一次
        // 有东西没删掉时也留着记忆：那点残留还在库里管得到，不至于变成没人管的孤儿
        val clean = result.failed.isEmpty() && unreachable.isEmpty()
        if (clean) forget(e) else rescan()
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
    fun clearCache(e: GameEntry): GameCleanup.Result {
        removalBlocked(e)?.let { return GameCleanup.Result(it) }
        val plan = planCache(e)
        val result = GameCleanup.delete(plan)
        rescan()
        return GameCleanup.Result(
            outcome = if (result.failed.isEmpty()) GameCleanup.Outcome.DONE else GameCleanup.Outcome.FAILED,
            workspaceKnown = plan.workspaceKnown,
            failed = result.failed,
        )
    }

    /** 这个游戏现在能不能删/清缓存：正在构建 / 正在运行都不能（返回原因），能删返回 null */
    fun removalBlocked(e: GameEntry): GameCleanup.Outcome? =
        LibraryLogic.removalBlocked(
            portRunning = port.running, portGameFile = port.gameFile, gamePath = e.sourcePath,
            playerRunning = player.running, playerTitleId = player.titleId, titleId = e.titleId)

    /** 删除前先看看要删什么（界面在 IO 线程上调它）：数据包 + 工作区 + 半成品包 */
    fun planRemoval(e: GameEntry): GameCleanup.Plan {
        val packs = libraryRepository.packsOf(e)
        return GameCleanup.plan(e.titleId, packs, libraryRepository.workRoot(), portsDirs(), keepPartials = port.running)
    }

    /** 清缓存前先看看要清什么：只有工作区与半成品包（数据包不碰） */
    fun planCache(e: GameEntry): GameCleanup.Plan =
        GameCleanup.plan(e.titleId, emptyList(), libraryRepository.workRoot(), portsDirs(), keepPartials = port.running)

    /**
     * 把一个游戏的包搬到另一个卷（可移动卡上 App 自己的目录），或者搬回来。
     *
     * 搬移的规矩在 PackMover 里：落位成功之后才删源包；失败时源包原地不动，游戏照常能玩。
     * 搬移期间这个游戏不许再动（再搬一次或删掉它都会把那一次弄坏）。
     *
     * @param onProgress 进度回报（每抄完一个文件一次）：界面用它显示进度条
     */
    fun movePack(e: GameEntry, targetDir: File, onProgress: (PackMover.Progress) -> Unit = {}): PackMover.Result {
        val pack = e.pack?.pack ?: return PackMover.Result.fail("the game has no pack")
        val key = e.titleId
        if (key in moving) return PackMover.Result.fail("the pack is being moved already")
        moving += key
        try {
            val r = PackMover.move(pack, targetDir, onProgress)
            if (r.ok) {
                libraryRepository.rememberPackPath(e.titleId, File(targetDir, pack.name).absolutePath)
                rescan()
            }
            return r
        } finally {
            moving -= key
        }
    }

    /** 这个游戏现在能不能搬它的数据包：正在构建 / 正在运行 / 正在搬都不能（返回原因），能搬返回 null */
    fun moveBlocked(e: GameEntry): GameCleanup.Outcome? =
        LibraryLogic.moveBlocked(
            portRunning = port.running, portGameFile = port.gameFile, gamePath = e.sourcePath,
            playerRunning = player.running, playerTitleId = player.titleId, titleId = e.titleId,
            moving = e.titleId in moving)

    /** 卡不在时够不着的包：路径还记得，那个卷却不在了（删不到，要说实话） */
    fun unreachablePacks(e: GameEntry): List<File> = libraryRepository.unreachablePacks(e)

    /** 这个游戏的 title ID */
    fun titleIdOf(e: GameEntry): String? = e.titleId

    /** 忘记一个游戏：它不再在库里登记，也不再有产物的登记 */
    private fun forget(e: GameEntry) {
        libraryRepository.forget(e.titleId)
        rescan()
    }

    /** 一个构建成功了：这个游戏是从哪个源文件建的，记下来（重建时用它） */
    fun noteDone(source: String, pack: String) {
        val titleId = libraryRepository.noteDone(source, pack) ?: return
        configRepository.migrateSourceConfig(source, titleId)
        rescan()
    }

    /**
     * 一个构建没成功（失败、取消、进程被杀）：工作区的目录名里带着它的 title ID，学过来。
     *
     * 一次只跑一个构建，所以这次动过的工作区就是它的——但要看准时间窗：构建开始到结束之间动过的
     * 工作区才是它的（后来那次构建的工作区不算）。
     */
    fun noteFailed(source: String) {
        if (source.isEmpty()) return
        val rec = port.history.firstOrNull { it.game == source } ?: return
        val from = rec.startedAt
        val to = rec.startedAt + rec.seconds * 1000 + 10_000   // 留一点余量（目录的 mtime 是最后一次写入）
        val tid = FolderScanner.workFolderIn(libraryRepository.workRoot(), from, to) ?: return
        libraryRepository.rememberSource(tid, source)
        rescan()
    }

    /** The port options a game carries: its own, or the old app's globals as the defaults */
    fun configOf(e: GameEntry): GameConfig = configRepository.forTitle(e.titleId)

    /** 构建页拿的是源文件：按它找到那张卡的配置 */
    fun configOf(f: File): GameConfig = configRepository.forSource(entryOf(f)?.sourcePath ?: f.absolutePath,
        entryOf(f)?.titleId)

    fun setConfig(e: GameEntry, c: GameConfig) = configRepository.set(e.titleId, null, c)

    /** 构建页拿的是源文件：还没构建过的按路径记，构建过的是那张卡的（按 title ID） */
    fun setConfig(f: File, c: GameConfig) = configRepository.set(entryOf(f)?.titleId, f.absolutePath, c)

    /** 库的列表（按名字排序） */
    fun visible(): List<GameEntry> = games
}
