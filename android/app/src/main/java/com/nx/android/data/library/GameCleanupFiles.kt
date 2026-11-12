package com.nx.android.data.library

import com.nx.android.domain.GameCleanup
import java.io.File

/** File-system execution for a previously reviewed cleanup plan. */
object GameCleanupFiles {
    /**
     * 删掉它们。
     *
     * 不量体积：一个工作区几个 GB、几十万文件，遍历文件树要好几秒，弹框会卡。空间是删掉就回来的，
     * 玩家要的是「删干净」，不是「先报个数」。
     */
    fun delete(plan: GameCleanup.Plan): GameCleanup.Deleted {
        val failed = ArrayList<File>()
        for (t in plan.targets) {
            val gone = try { !t.file.exists() || t.file.deleteRecursively() } catch (e: Exception) { false }
            if (!gone) failed += t.file
        }
        return GameCleanup.Deleted(failed, plan.workspaceKnown)
    }
}
