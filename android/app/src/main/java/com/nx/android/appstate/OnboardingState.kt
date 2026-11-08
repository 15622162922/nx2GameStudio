package com.nx.android.appstate

import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.AppLog
import com.nx.android.data.Env
import com.nx.android.data.PrefsStore
import com.nx.android.data.PrefsStore.Companion.OB_COMPLETED
import com.nx.android.data.PrefsStore.Companion.OB_FOLDER
import com.nx.android.data.PrefsStore.Companion.OB_KEYS
import com.nx.android.data.PrefsStore.Companion.OB_LATER
import com.nx.android.data.PrefsStore.Companion.OB_STEP
import com.nx.android.data.PortRunner
import com.nx.android.domain.FolderScanner
import com.nx.android.domain.ObFacts
import com.nx.android.domain.ObStep
import com.nx.android.domain.OnboardingLogic
import java.io.File

/**
 * The guided setup's state: the step the player is on, what each step gathered, and where "later" lands next
 * launch. The gates' rules are OnboardingLogic's (pure, tested); this class wires them to the machine — the
 * storage permission, the environment's unpack, the doctor's keys check, the folder scan — and remembers
 * everything across restarts in the PrefsStore (its own ob.* keys; the tool's older keys stay untouched).
 */
class OnboardingState(private val prefs: PrefsStore, private val env: Env,
                      private val storageOk: () -> Boolean) {

    companion object {
        /** The unpack writes about 1.6 GB; a little headroom for the check */
        const val NEEDED_GB = 2L
        /** Where prod.keys usually lies on a phone (the common folders of the file managers) */
        val COMMON_KEYS = listOf("/sdcard/switch/prod.keys", "/sdcard/Switch/prod.keys", "/sdcard/switch/keys/prod.keys")
    }

    /** The flow is on screen (first run, or Settings' run-again); otherwise the main screen shows */
    var active by mutableStateOf(false); private set

    var step by mutableStateOf(ObStep.WELCOME); private set

    var keysPath by mutableStateOf(prefs.str(OB_KEYS))
    var keysOk by mutableStateOf(false); private set
    var keysChecking by mutableStateOf(false); private set
    var keysDetail by mutableStateOf(""); private set
    var keysSkipped by mutableStateOf(false); private set

    var folder by mutableStateOf(prefs.str(OB_FOLDER))
    var games by mutableStateOf(listOf<File>()); private set

    var unpacking by mutableStateOf(false); private set
    var unpackStep by mutableStateOf(""); private set
    var unpackError by mutableStateOf(""); private set

    /** Opens the flow: first run lands on Welcome; "later" resumes at its saved step, revalidated */
    fun open() {
        val saved = prefs.str(OB_STEP)
        step = if (saved.isNotEmpty() && prefs.bool(OB_LATER)) {
            OnboardingLogic.revalidate(ObStep.valueOf(saved), facts())
        } else {
            ObStep.WELCOME
        }
        active = true
        enter()
    }

    /** The flow is done: the main screen takes over, and the next launch does not ask again */
    fun openLibrary() {
        prefs.put(OB_COMPLETED, true)
        prefs.put(OB_LATER, false)
        active = false
    }

    /** Everything gathered so far is kept, and the next launch comes back to this step */
    fun skipForLater() {
        prefs.put(OB_STEP, step.name)
        prefs.put(OB_LATER, true)
        active = false
    }

    fun next() {
        if (!OnboardingLogic.canAdvance(step, facts()) || step >= ObStep.DONE) return
        step = ObStep.entries[step.ordinal + 1]
        enter()
    }

    fun back() {
        if (step <= ObStep.WELCOME) return
        step = ObStep.entries[step.ordinal - 1]
        enter()
    }

    /** Arriving at a step sets it up (the keys prefill, the folder's scan) */
    private fun enter() {
        when (step) {
            ObStep.KEYS -> prefillKeys()
            ObStep.GAMES -> rescan()
            else -> {}
        }
    }

    /** The environment's state, for the settings page's display */
    val envReady: Boolean get() = env.ready
    val lastCheck: String get() = env.lastCheck

    /** Throws the unpacked environment away: the next prepare does everything again */
    fun resetEnv() {
        Thread { env.reset() }.start()
    }

    fun facts(): ObFacts = ObFacts(
        storageOk = storageOk(), freeGB = freeGB(), neededGB = NEEDED_GB,
        envReady = env.ready, keysPath = keysPath, keysOk = keysOk, keysSkipped = keysSkipped,
        folderSet = folder.isNotBlank(),
    )

    /** The free space where the environment is unpacked (the app's own, internal storage) */
    fun freeGB(): Long = try {
        Environment.getDataDirectory().let { android.os.StatFs(it.path).availableBytes } / (1024L * 1024L * 1024L)
    } catch (e: Exception) {
        0L
    }

    // ---- the environment ---------------------------------------------------------------------------------------

    fun unpack() {
        if (unpacking) return
        unpacking = true
        unpackError = ""
        Thread {
            try {
                env.unpack { s -> unpackStep = s }
                unpacking = false
            } catch (e: Exception) {
                unpacking = false
                unpackError = "${e.javaClass.simpleName}: ${e.message}"
                AppLog.line("准备失败：${e.message}")
            }
        }.start()
    }

    // ---- the keys -----------------------------------------------------------------------------------------------

    fun chooseKeys(path: String) {
        keysPath = path
        keysSkipped = false
        prefs.put(OB_KEYS, path)
        checkKeys()
    }

    fun skipKeys() {
        keysSkipped = true
        keysOk = false
    }

    /** The skip taken back: the saved path (if any) is checked again */
    fun unskipKeys() {
        keysSkipped = false
        if (keysPath.isNotBlank()) checkKeys()
    }

    /** Entering the keys step: a saved path or a file in the usual places is checked at once */
    private fun prefillKeys() {
        if (keysSkipped || keysOk) return
        if (keysPath.isNotBlank()) { checkKeys(); return }
        for (p in COMMON_KEYS) {
            if (File(p).isFile()) { chooseKeys(p); return }
        }
    }

    /** The doctor's prod.keys row says whether the keys are good */
    fun checkKeys() {
        val p = keysPath.trim()
        if (p.isEmpty() || !File(p).isFile()) {
            keysOk = false
            keysDetail = if (p.isEmpty()) "还没有选择 prod.keys" else "文件不存在：$p"
            return
        }
        keysChecking = true
        keysDetail = ""
        val buf = StringBuilder()
        PortRunner(env) { line -> buf.appendLine(line) }.doctor(p, json = true) {
            keysChecking = false
            val rows = runCatching { org.json.JSONArray(buf.toString().trim()) }.getOrNull()
            if (rows == null) {
                keysOk = false
                keysDetail = "自检没有输出（环境未就绪？）"
                return@doctor
            }
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                if (r.optString("name") == "prod.keys") {
                    keysOk = r.optBoolean("ok")
                    keysDetail = r.optString("detail")
                    return@doctor
                }
            }
            keysOk = false
            keysDetail = "自检没有报告 prod.keys"
        }
    }

    // ---- the games -----------------------------------------------------------------------------------------------

    fun chooseFolder(path: String) {
        folder = path
        prefs.put(OB_FOLDER, path)
        rescan()
    }

    fun rescan() {
        games = FolderScanner.scan(File(folder))
    }
}
