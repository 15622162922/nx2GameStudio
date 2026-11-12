package com.nx.android.data

import com.nx.android.BuildConfig
import com.nx.android.domain.PortEvents
import com.nx.android.domain.environment.GuestProcessLauncher
import java.io.File
import javax.inject.Inject

/** Executes only rebuild-runtime; a successful process must also report the requested pack. */
class RuntimeCompiler @Inject constructor(private val guestLauncher: GuestProcessLauncher) {
    fun rebuild(pack: String, noLsfg: Boolean = !BuildConfig.NX_LSFG, log: (String) -> Unit = AppLog::line): Boolean {
        var committed = false
        return try {
            val process = guestLauncher.guest(args(pack, noLsfg))
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val event = PortEvents.parse(line)
                    if (event?.kind == "result") {
                        committed = File(event.data).canonicalFile == File(pack).canonicalFile
                    }
                    log(event?.message?.takeIf { it.isNotBlank() } ?: line)
                }
            }
            val code = process.waitFor()
            if (code != 0) log("[RED] Runtime compiler exited with code $code")
            code == 0 && committed
        } catch (error: Exception) {
            log("[RED] Runtime update failed: ${error.message}")
            false
        }
    }

    companion object {
        fun args(pack: String, noLsfg: Boolean): List<String> =
            listOf("-m", "porter", "rebuild-runtime", pack, "--progress", "json") +
                if (noLsfg) listOf("--no-lsfg") else emptyList()
    }
}
