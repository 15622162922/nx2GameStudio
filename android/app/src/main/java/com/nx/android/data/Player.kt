package com.nx.android.data

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.nx.android.domain.Ported
import com.nx.android.game.GameActivity
import java.io.File

/**
 * The player's launcher: a game runs in the app's own ":play" process (the host is loaded there from the
 * copy in the app's private folder — sdcard is noexec, the pack's files cannot be loaded in place). Running
 * a game prepares that copy (the host beside its driver libraries, one folder per title id), then hands the
 * pack to the GameActivity. One game at a time; the process ends with the game, and the library watches the
 * process to know whether one is playing.
 */
object Player {

    /** The ":play" process's name suffix (the manifest's android:process) */
    const val PROCESS_SUFFIX = ":play"

    /** the game's host and its driver libraries, in the app's private folder, one folder per title id */
    fun hostDir(context: Context, titleId: String): File = File(context.filesDir, "hosts/${titleId.lowercase()}")

    /** the ":play" process's presence: a game is playing (or hanging in the background) while it is there */
    fun playProcessAlive(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.runningAppProcesses?.any { it.processName.endsWith(PROCESS_SUFFIX) } == true
    }

    /**
     * Runs a finished port: the pack's host and drivers are copied into the private folder (over an older
     * copy: the pack is the truth), then the GameActivity comes up. [onReady] answers on a worker thread with
     * false when the pack has no host to copy (the library already shows such a game as not ported).
     *
     * 先结束存活的 :play 进程：重建过的宿主永远加载不进一个还在跑着旧宿主的进程（System.load 对同路径
     * 已加载的库是空操作），所以每次运行都以全新的进程开始。
     *
     * [activity] 是 Activity 而不是 Context：游戏页要落进**调用者所在的任务**（库页那个任务），所以这条
     * intent 不能带 FLAG_ACTIVITY_NEW_TASK——带了系统就另起一个任务，游戏页就不再是 App 自己的一页，
     * 退出游戏也就不会回到库页。
     */
    fun run(activity: Activity, port: Ported, name: String, onReady: (Boolean) -> Unit) {
        if (com.nx.android.data.library.LibraryFileScanner.portFromPack(port.pack)?.runnable != true) {
            onReady(false)
            return
        }
        if (playProcessAlive(activity)) {
            GameActivity.stop(activity)
            val deadline = System.currentTimeMillis() + 3000
            while (playProcessAlive(activity) && System.currentTimeMillis() < deadline) Thread.sleep(100)
            if (playProcessAlive(activity)) { onReady(false); return }
        }
        Thread {
            val ok = try {
                val dst = hostDir(activity, port.titleId)
                dst.mkdirs()
                RuntimeLibraries.copyForLaunch(port, dst)
                true
            } catch (e: Exception) {
                false
            }
            android.os.Handler(activity.mainLooper).post {
                if (ok) {
                    val i = Intent(activity, GameActivity::class.java)
                        .putExtra(GameActivity.EXTRA_PACK, port.pack.absolutePath)
                        .putExtra(GameActivity.EXTRA_HOST, File(hostDir(activity, port.titleId), port.host.name).absolutePath)
                        .putExtra(GameActivity.EXTRA_TITLE_ID, port.titleId)
                        .putExtra(GameActivity.EXTRA_NAME, name)
                    // 不带 FLAG_ACTIVITY_NEW_TASK：游戏页压在这个任务的库页上面（见上面的说明）
                    activity.startActivity(i)
                }
                onReady(ok)
            }
        }.start()
    }

}
