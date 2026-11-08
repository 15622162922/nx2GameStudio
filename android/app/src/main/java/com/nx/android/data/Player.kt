package com.nx.android.data

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
     */
    fun run(context: Context, port: Ported, name: String, onReady: (Boolean) -> Unit) {
        Thread {
            val ok = try {
                val dst = hostDir(context, port.titleId)
                dst.mkdirs()
                copy(port.host, File(dst, port.host.name))
                File(port.pack, "drivers").listFiles()?.forEach { if (it.isFile) copy(it, File(dst, it.name)) }
                true
            } catch (e: Exception) {
                false
            }
            android.os.Handler(context.mainLooper).post {
                if (ok) {
                    val i = Intent(context, GameActivity::class.java)
                        .putExtra(GameActivity.EXTRA_PACK, port.pack.absolutePath)
                        .putExtra(GameActivity.EXTRA_HOST, File(hostDir(context, port.titleId), port.host.name).absolutePath)
                        .putExtra(GameActivity.EXTRA_TITLE_ID, port.titleId)
                        .putExtra(GameActivity.EXTRA_NAME, name)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                }
                onReady(ok)
            }
        }.start()
    }

    private fun copy(src: File, dst: File) {
        if (src.length() == dst.length()) return   // the pack's copy is already in place
        dst.parentFile?.mkdirs()
        val tmp = File(dst.parentFile, dst.name + ".part")
        src.inputStream().use { s -> tmp.outputStream().use { s.copyTo(it) } }
        if (!tmp.renameTo(dst)) {
            dst.delete()
            if (!tmp.renameTo(dst)) throw java.io.IOException("cannot put ${dst.name} in place")
        }
    }
}
