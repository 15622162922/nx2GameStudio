package com.nx.android.appstate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.R
import com.nx.android.data.AppLocale
import com.nx.android.data.AppLog
import com.nx.android.data.PrefsStore
import com.nx.android.data.PrefsStore.Companion.PORT_HISTORY
import com.nx.android.data.PrefsStore.Companion.PORT_RUNNING
import com.nx.android.domain.PortEvents
import org.json.JSONArray
import org.json.JSONObject

/**
 * The port that runs now and the ones that ran before: the one source the activity bar, the notification, the
 * builds page and the library's cards all read, so the places cannot disagree. The writes come from the runner's
 * thread; Compose's snapshot state takes writes from any thread. One instance lives in the AppContainer — the
 * service and the UI share it.
 */
class PortState(private val prefs: PrefsStore, private val locale: AppLocale) {

    enum class Phase { IDLE, RUNNING, DONE, FAILED, CANCELLED, INTERRUPTED }

    /** A finished (or interrupted) port, for the builds page's history */
    data class Record(
        val game: String,      // the source file's name
        val phase: Phase,      // DONE / FAILED / CANCELLED / INTERRUPTED
        val out: String,       // the artifact the port made (the data pack; '' when none)
        val startedAt: Long,   // wall clock of the start
        val seconds: Long,     // how long it took
    )

    var phase by mutableStateOf(Phase.IDLE); private set
    var gameFile by mutableStateOf(""); private set
    var stageOf by mutableStateOf(0); private set
    var stages by mutableStateOf(0); private set
    var stageName by mutableStateOf(""); private set
    var stageStep by mutableStateOf(""); private set
    var progress by mutableStateOf(0f); private set
    var apkPath by mutableStateOf(""); private set   // the PC flow's APK ('' in the app's mode: the pack is the only artifact)
    var dataPath by mutableStateOf(""); private set  // the data pack, both modes' artifact
    var startedAt by mutableStateOf(0L); private set
    var log by mutableStateOf(listOf<String>()); private set
    var history by mutableStateOf(listOf<Record>()); private set

    val running: Boolean get() = phase == Phase.RUNNING

    /** The port's artifact (the pack in the app's mode, the APK on the PC flow); '' while it runs */
    val outPath: String get() = apkPath.ifEmpty { dataPath }

    /** Loads the history and answers the question "was a port interrupted?": the marker is still set */
    fun attach() {
        history = loadHistory()
        if (prefs.str(PORT_RUNNING).isNotEmpty()) markInterrupted()
    }

    /** The port begins: fresh state, and the marker that survives the process (the interrupted case reads it) */
    fun begin(game: String) {
        phase = Phase.RUNNING
        gameFile = game
        stageOf = 0; stages = 0; stageName = ""; stageStep = ""
        progress = 0f
        apkPath = ""
        dataPath = ""
        log = listOf()
        startedAt = System.currentTimeMillis()
        prefs.put(PORT_RUNNING, game)
        say(locale.str(R.string.port_begin, game))
    }

    /** One line of the porter's output: an event moves the state, plain text goes into the log */
    fun onLine(line: String) {
        val ev = PortEvents.parse(line)
        if (ev == null) { say(line); return }
        when (ev.kind) {
            "say" -> say(ev.message)
            "stage" -> {
                stageOf = ev.stage; stages = ev.stages
                stageName = ev.name; stageStep = ""
                say(locale.str(R.string.port_log_stage, ev.stage, ev.stages, ev.name))
            }
            "progress" -> {
                if (ev.frac >= 0) progress = ev.frac
                if (ev.name.isNotEmpty()) stageName = ev.name
                if (ev.step.isNotEmpty() && ev.step != stageStep) {
                    stageStep = ev.step
                    say("  · $stageName $stageStep")
                }
            }
            "result" -> {
                apkPath = ev.apk
                dataPath = ev.data
                progress = 1f
            }
        }
    }

    /** The porter ended on its own: an APK means done, none means failed */
    fun finish() {
        if (phase != Phase.RUNNING) return   // a cancel already marked it
        end(if (outPath.isNotEmpty()) Phase.DONE else Phase.FAILED)
    }

    /** The player stopped it: the porter and the tools it started are already dying */
    fun cancel() {
        if (phase != Phase.RUNNING) return
        end(Phase.CANCELLED)
    }

    /** The system killed the port (the marker is set, nothing runs): shown as interrupted, restartable */
    fun markInterrupted() {
        val game = prefs.str(PORT_RUNNING)
        if (game.isEmpty()) return
        end(Phase.INTERRUPTED, gameOverride = game)
    }

    private fun end(p: Phase, gameOverride: String = "") {
        val secs = if (startedAt > 0) (System.currentTimeMillis() - startedAt) / 1000 else 0
        val game = gameOverride.ifEmpty { gameFile }
        phase = p
        startedAt = 0
        prefs.put(PORT_RUNNING, null)
        if (game.isNotEmpty()) {
            history = (listOf(Record(game, p, outPath, System.currentTimeMillis() - secs * 1000, secs)) + history).take(20)
            saveHistory()
        }
        say(
            when (p) {
                Phase.DONE -> locale.str(R.string.port_done_path, outPath)
                Phase.FAILED -> locale.str(R.string.port_failed_no_artifact)
                Phase.CANCELLED -> locale.str(R.string.common_cancelled)
                Phase.INTERRUPTED -> locale.str(R.string.port_interrupted, game)
                else -> return
            }
        )
    }

    fun say(s: String) {
        log = (log + s).takeLast(400)
        AppLog.line(s)
    }

    // ---- the history, kept as JSON in the prefs ----------------------------------------------------------------

    private fun loadHistory(): List<Record> = try {
        val a = prefs.array(PORT_HISTORY)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Record(o.getString("game"), Phase.valueOf(o.getString("phase")), o.optString("apk"),
                o.optLong("startedAt"), o.optLong("seconds"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveHistory() {
        val a = JSONArray()
        for (r in history) {
            a.put(JSONObject().put("game", r.game).put("phase", r.phase.name).put("out", r.out)
                .put("startedAt", r.startedAt).put("seconds", r.seconds))
        }
        prefs.putArray(PORT_HISTORY, a)
    }
}
