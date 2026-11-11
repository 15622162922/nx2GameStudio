package com.nx.android.domain

import java.io.File
import java.util.Locale

/**
 * 一个游戏留在手机上的构建数据：数据包（移植成功时）、工作区（移植器解包重写的地方，几个 GB）、
 * 半成品包（打包阶段被打断留下的）。删一个游戏就是删这些——游戏源文件是玩家自己的，不碰。
 *
 * 纯文件操作，不依赖 Android，测试跑在 JVM 上。
 */
object GameCleanup {

    /** 删除一个游戏的结果：删掉了、被拒（正在构建 / 正在运行 / 正在搬移）、失败 */
    enum class Outcome { DONE, BUSY_PORTING, BUSY_RUNNING, BUSY_MOVING, FAILED }

    data class Result(
        val outcome: Outcome,
        val workspaceKnown: Boolean = true,  // 工作区知不知道在哪（文件名里没有 title ID 就不知道）
        val failed: List<File> = emptyList(),
    )

    /** 要删的东西 */
    enum class Kind { PACK, WORKSPACE, PARTIAL }

    data class Target(val file: File, val kind: Kind)

    /**
     * 一次删除的计划：删哪些东西、工作区知不知道在哪。
     *
     * [workspaceKnown] 为 false 是因为游戏文件名里没有 title ID（多半是下载器改过名），
     * 移植器按 title ID 命名的工作区就找不到了——这时要说实话，不能假装删干净了。
     */
    data class Plan(
        val targets: List<Target>,
        val workspaceKnown: Boolean,
    )

    /** 删完的结果：什么没删掉 */
    data class Deleted(
        val failed: List<File>,
        val workspaceKnown: Boolean,
    )

    /**
     * 这个游戏要删的东西。
     *
     * @param titleId 这个游戏的 title ID（文件名里的，或数据包清单里的）；null = 不知道工作区在哪
     * @param packs 这个游戏已完成的数据包（每个卷上可能各有一份：私有的、可移动卡上的）
     * @param keepPartials 留着半成品包：另一个构建正在写它，删了就把那次构建杀了
     */
    fun plan(titleId: String?, packs: List<File>, workRoot: File, portsDirs: List<File>,
             keepPartials: Boolean): Plan {
        val targets = ArrayList<Target>()
        packs.filter { it.exists() }.forEach { targets += Target(it, Kind.PACK) }
        workspaceOf(titleId, workRoot)?.takeIf { it.exists() }?.let { targets += Target(it, Kind.WORKSPACE) }
        if (!keepPartials) portsDirs.forEach { d -> partials(d).forEach { targets += Target(it, Kind.PARTIAL) } }
        return Plan(targets, workspaceKnown = titleId != null)
    }

    /** 工作区：<work>/<title ID>_build（移植器按 title ID 建的目录）；没有 title ID 就不知道它在哪 */
    fun workspaceOf(titleId: String?, workRoot: File): File? {
        if (titleId.isNullOrBlank()) return null
        return File(workRoot, "${titleId.uppercase(Locale.US)}_build")
    }

    /** 半成品包：ports/ 下的 *.partial（打包被打断留下的，永远没有重试价值） */
    fun partials(portsDir: File): List<File> =
        portsDir.listFiles()
            ?.filter { it.isDirectory && it.name.endsWith(".partial") }
            ?.sortedBy { it.name }
            ?: emptyList()

    /**
     * 删掉它们。
     *
     * 不量体积：一个工作区几个 GB、几十万文件，遍历文件树要好几秒，弹框会卡。空间是删掉就回来的，
     * 玩家要的是「删干净」，不是「先报个数」。
     */
    fun delete(plan: Plan): Deleted {
        val failed = ArrayList<File>()
        for (t in plan.targets) {
            val gone = try { !t.file.exists() || t.file.deleteRecursively() } catch (e: Exception) { false }
            if (!gone) failed += t.file
        }
        return Deleted(failed, plan.workspaceKnown)
    }
}
