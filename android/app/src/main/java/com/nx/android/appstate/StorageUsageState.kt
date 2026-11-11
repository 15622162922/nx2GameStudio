package com.nx.android.appstate

import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.Env
import com.nx.android.domain.StorageUsage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * 「存储」分区背后的状态：库里每个游戏占了多少（工作区 + 数据包），以及一共占了多少。
 *
 * 统计是**故意**的一次文件遍历，所以这一层管住它的代价：
 *
 * - 闸门在界面那边（可见不足 [GATE_MS] 就一次都不遍历）；
 * - 遍历跑在后台低优先级的单线程上（[background]），不与界面渲染抢核；
 * - 每段有预算（文件数/耗时），触顶即按「下界」呈现（见 `domain/StorageUsage`）；
 * - 取消靠协程：离开分区/切换分区/关页面/退后台 ⇒ 遍历在下一个取消点停下，
 *   已测得的那一段留下当下界；
 * - 结果落盘（`files/usage.json`），当天再进直接用，不重��。
 */
class StorageUsageState(private val env: Env, private val library: LibraryState) {

    /** 面板看到的状态 */
    var snapshot by mutableStateOf(StorageUsage.Snapshot()); private set

    /**
     * 遍历用的专用低优先级线程：不与界面渲染和游戏抢核。
     *
     * `Process.setThreadPriority` 只对**调用它的线程**生效，所以必须放在线程体的第一行，
     * 而不是线程工厂里（工厂在创建线程的那个线程上执行）。
     */
    private val background: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread({
            runCatching {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            }
            r.run()
        }, "storage-usage-scan").apply { priority = Thread.MIN_PRIORITY }
    }.asCoroutineDispatcher()

    /**
     * 读回落盘的快照（当天量过就不再遍历）。
     *
     * 快照是「今天量过一次就别再量」的凭据：面板进来先读它，够新就直接用。
     */
    fun load() {
        if (snapshot.items.isNotEmpty() || snapshot.scannedAt > 0L) return
        val loaded = StorageUsage.read(snapshotFile()) ?: return
        snapshot = loaded
    }

    /**
     * 列清单的骨架：库里每个游戏一项（工作区 + 它的数据包），末尾是兜底那一项。
     *
     * 不遍历任何目录——只把要量的地方摆出来（上一轮量到的数字原样留着）。
     * 取数要读各个卷的端口目录（认包），所以它跑在后台线程上。
     */
    suspend fun prepare() {
        val games = library.visible()               // 主线程读：库里的清单
        val items = withContext(background) { library.usageItems(games) }
        val previous = snapshot.items.associateBy { it.id }
        snapshot = snapshot.copy(
            items = items.map { item ->
                val old = previous[item.id]
                // 各段一一对上：路径与角色相同才算同一段
                val oldParts = old?.parts?.associateBy { it.role to it.path } ?: emptyMap()
                val sameShape = old != null && old.parts.size == item.parts.size &&
                    item.parts.all { oldParts.containsKey(it.role to it.path) }
                item.copy(
                    parts = item.parts.map { p ->
                        val o = oldParts[p.role to p.path]
                        // 只把上一轮的**数字**搬过来，状态用现在的：够不着就是够不着。
                        // 拿上一轮「量到了」的状态盖住「卡不在了」⇒ 面板会说「数据包 0 B」，
                        // 那是在编一个数字（其实那一段根本够不着）。
                        if (o != null && p.state == StorageUsage.State.PENDING &&
                            (o.state == StorageUsage.State.DONE || o.state == StorageUsage.State.PARTIAL))
                            p.copy(bytes = o.bytes, files = o.files, state = o.state)
                        else p
                    },
                    // 各段都还是原来那些 ⇒ 指纹还有效（当天不必重量）
                    fingerprint = if (sameShape) old!!.fingerprint else "",
                )
            },
        )
    }

    /**
     * 统计一遍（当天量过、且量的是现在这份内容，就不量），量完落盘。
     *
     * 统计在调用方的上下文里推进（面板从 `LaunchedEffect` 调它），只有遍历本身在后台线程上。
     * 面板关闭 / 切换分区 ⇒ 协程被取消 ⇒ 遍历在下一个取消点停下；已经测出的那一段作为
     * 下界留在那一项上，标为部分统计。**统计没跑完就不落盘**：半份数字不算「今天量过」。
     */
    suspend fun scan(force: Boolean = false) {
        val result = StorageUsage.scanAndSave(
            snapshot = snapshot,
            target = snapshotFile(),
            force = force,
            publish = { snapshot = it },
            fingerprintOf = { dir -> withContext(background) { StorageUsage.fingerprintOf(dir) } },
        ) { dir, acc ->
            withContext(background) { StorageUsage.measure(dir, acc) }
        }
        snapshot = result
    }

    private fun snapshotFile(): File = File(env.files, "usage.json")

    companion object {
        /**
         * 误触保护的闸门：面板可见不足这么久就不统计。
         *
         * 玩家「点进去马上又退出」是常态，闸门让这次进入**零遍历**。
         */
        const val GATE_MS = 400L
    }
}
