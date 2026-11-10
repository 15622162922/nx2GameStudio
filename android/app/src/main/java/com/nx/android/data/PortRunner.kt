package com.nx.android.data

import com.nx.android.BuildConfig
import com.nx.android.R
import java.io.File

/**
 * Runs the porter and the doctor: the process handling that used to live in the activity, unchanged in its
 * behavior (one stream for stdout and stderr, the porter's events by line, cancel stops the porter and the
 * tools it started). Every run is one thread; the output goes to [onLine] from that thread.
 */
class PortRunner(private val env: Env, private val locale: AppLocale, private val onLine: (String) -> Unit) {

    @Volatile private var proc: Process? = null

    fun port(
        gameFile: String, keys: String, out: String,
        touch: Boolean, fps60: Boolean, experimental: Boolean,
        aliasHeadroom: Int,
        onEnd: () -> Unit,
    ): Thread {
        val args = portArgs(gameFile, keys, out, touch, fps60, experimental, aliasHeadroom, !BuildConfig.NX_LSFG)
        return start(args, onEnd)
    }

    companion object {

        /**
         * The porter's command line for a port: pure, so the test can check it on the JVM.
         *
         * The extra address space (the alias region): Auto passes nothing, the porter decides by itself
         * (the version the profile was tested with gets none, another one 8 GB).
         */
        fun portArgs(gameFile: String, keys: String, out: String, touch: Boolean, fps60: Boolean,
                     experimental: Boolean, aliasHeadroom: Int, noLsfg: Boolean): List<String> {
            val args = mutableListOf("-m", "porter", "port", gameFile, "-k", keys, "-o", out, "--progress", "json", "--app")
            if (touch) args += "--touch"
            if (fps60) args += "--fps60"
            if (experimental) args += "--experimental"
            if (aliasHeadroom >= 0) args += listOf("--alias-headroom", aliasHeadroom.toString())
            // A build without the frame generation (the Gradle property nxLsfg=false): the host the app builds on the
            // device is then smaller and the frame generation reports that it is not in this build (android/NOTICE.md).
            if (noLsfg) args += "--no-lsfg"
            return args
        }
    }

    /**
     * The tools check: the interpreter paths first (the porter would otherwise report its tools missing from the
     * saved setting). [json] asks for the rows as one JSON array, the form the keys check reads.
     */
    fun doctor(keys: String, json: Boolean, onEnd: () -> Unit): Thread {
        val args = mutableListOf("-m", "porter", "doctor")
        if (json) args += "--json"
        if (keys.isNotBlank()) args += listOf("-k", keys.trim())
        val t = Thread {
            try {
                onLine(locale.str(R.string.runner_checking_tools))
                env.patchInterpreters()
                onLine(locale.str(R.string.runner_interpreter_patched))
            } catch (e: Exception) {
                onLine(locale.str(R.string.runner_interpreter_patch_failed, e.message))
            }
            start(args, onEnd)
        }
        t.start()
        return t
    }

    private fun start(args: List<String>, onEnd: () -> Unit): Thread {
        val t = Thread {
            try {
                val p = env.guest(args)
                proc = p
                p.inputStream.bufferedReader().forEachLine { onLine(it) }
                val code = runCatching { p.waitFor() }.getOrDefault(-1)
                if (code != 0) onLine(locale.str(R.string.runner_exit_code, code))
            } catch (e: Exception) {
                onLine("[RED] ${e.message}")
            } finally {
                proc = null
                onEnd()
            }
        }
        t.start()
        return t
    }

    /** Stops the porter and the tools it started (they are the app's own processes, stopped one by one) */
    fun cancel() {
        try { proc?.destroyForcibly() } catch (e: Exception) {
        }
        proc = null
        val me = android.os.Process.myPid()
        for (d in File("/proc").listFiles() ?: return) {
            if (!d.name.all { it.isDigit() }) continue
            val pid = d.name.toLong()
            if (pid == me.toLong()) continue
            try {
                val cmd = File(d, "cmdline").readText()
                if (cmd.contains("python3.13") || cmd.contains("dotnet") || cmd.contains("ld-linux") ||
                    cmd.contains("clang") || cmd.contains("nx_extract")
                ) {
                    android.os.Process.killProcess(pid.toInt())
                }
            } catch (e: Exception) {
            }
        }
    }
}
