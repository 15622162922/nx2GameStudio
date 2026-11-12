package com.nx.android.appstate

import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.R
import com.nx.android.data.AppLocale
import com.nx.android.data.AppLog
import com.nx.android.data.PortRunner
import com.nx.android.data.PrefsStore
import com.nx.android.domain.environment.EnvironmentSetup
import com.nx.android.domain.environment.GuestProcessLauncher
import com.nx.android.data.PrefsStore.Companion.OB_COMPLETED
import com.nx.android.data.PrefsStore.Companion.OB_LATER
import com.nx.android.data.PrefsStore.Companion.OB_STEP
import com.nx.android.domain.DoctorOutput
import com.nx.android.domain.ObFacts
import com.nx.android.domain.ObStep
import com.nx.android.domain.OnboardingLogic
import com.nx.android.feature.environment.EnvCheckState
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The guided setup's state: the step the player is on, what each step gathered, and where "later" lands next
 * launch. The gates' rules are OnboardingLogic's (pure, tested); this class wires them to the machine — the
 * storage permission, the environment's unpack, the doctor's keys check — and remembers everything across
 * restarts in the PrefsStore (its own ob.* keys; the tool's older keys stay untouched).
 */
@Singleton
class OnboardingState @Inject constructor(
    private val prefs: PrefsStore,
    private val env: EnvironmentSetup,
    private val guestLauncher: GuestProcessLauncher,
    private val locale: AppLocale,
    private val envCheck: EnvCheckState,
) {
    @Volatile var storageOk: Boolean = false

    companion object {
        /** The unpack writes about 700 MB (the rootfs, the build tools and the .NET runtime); a little headroom for the check */
        const val NEEDED_GB = 1L
        /** The folder prod.keys usually lies in, on the phone's own storage (/sdcard/switch) */
        const val USUAL_KEYS_FOLDER = "switch"
    }

    /** The flow is on screen (first run, or Settings' run-again); otherwise the main screen shows */
    var active by mutableStateOf(false); private set

    var step by mutableStateOf(ObStep.WELCOME); private set

    /** The keys file: the app's own setting (the port is started with the same one) */
    var keysPath by mutableStateOf(prefs.keys); private set

    var keysOk by mutableStateOf(false); private set
    var keysChecking by mutableStateOf(false); private set
    var keysDetail by mutableStateOf(""); private set
    var keysSkipped by mutableStateOf(false); private set

    var unpacking by mutableStateOf(false); private set
    var unpackStep by mutableStateOf(""); private set
    var unpackError by mutableStateOf(""); private set

    /** Opens the flow: first run lands on Welcome; a setup that was cut short resumes at its saved step */
    fun open() {
        val saved = prefs.str(OB_STEP)
        refreshEnv()
        keysPath = prefs.keys      // the settings page may have set a keys file since the last time
        step = if (saved.isNotEmpty() && prefs.bool(OB_LATER)) {
            // 旧版本存的步名可能已经不存在了（游戏文件夹那步被删了）：认不出来就当从头开始
            OnboardingLogic.revalidate(runCatching { ObStep.valueOf(saved) }.getOrNull() ?: ObStep.WELCOME, facts())
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

    fun next() {
        if (!OnboardingLogic.canAdvance(step, facts()) || step >= ObStep.DONE) return
        step = ObStep.entries[step.ordinal + 1]
        saveStep()
        enter()
    }

    fun back() {
        if (step <= ObStep.WELCOME) return
        step = ObStep.entries[step.ordinal - 1]
        saveStep()
        enter()
    }

    /** The step is written down as the player walks it: a process killed mid-setup comes back to it */
    private fun saveStep() {
        prefs.put(OB_STEP, step.name)
        prefs.put(OB_LATER, true)
    }

    /** Arriving at a step sets it up (the keys prefill) */
    private fun enter() {
        refreshEnv()
        when (step) {
            ObStep.KEYS -> prefillKeys()
            else -> {}
        }
    }

    /**
     * Whether the environment is ready: a copy the screens can watch. The environment is the truth (it asks
     * the filesystem every time it is read), but a plain read cannot make a screen redraw itself, so the copy
     * is refreshed wherever the environment can have changed.
     */
    var envReady by mutableStateOf(env.ready); private set
    val lastCheck: String get() = env.lastCheck

    /** Throws the unpacked environment away: the next prepare does everything again */
    fun resetEnv() {
        Thread {
            env.reset()
            refreshEnv()
            envCheck.refresh()
        }.start()
    }

    fun facts(): ObFacts = ObFacts(
        storageOk = storageOk, freeGB = freeGB(), neededGB = NEEDED_GB,
        envReady = envReady, keysPath = keysPath, keysOk = keysOk, keysSkipped = keysSkipped,
    )

    /** The environment's readiness, read again: the screens watch the copy, not the filesystem */
    private fun refreshEnv() {
        envReady = env.ready
    }

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
                AppLog.line(locale.str(R.string.onb_prepare_failed, e.message))
            }
            refreshEnv()
            envCheck.refresh()
        }.start()
    }

    // ---- the keys -----------------------------------------------------------------------------------------------

    fun chooseKeys(path: String) {
        keysPath = path
        keysSkipped = false
        prefs.writeKeys(path)      // the app's own setting: the port is started with this one
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

    /**
     * Where the keys picker opens: the folder of the file the player has, or the usual place. The picker
     * knows the storage volumes by their own paths, so the usual place is given in that form (the internal
     * storage's own path, which is what /sdcard points at).
     */
    val keysStartDir: File
        get() {
            val chosen = keysPath.trim()
            if (chosen.isNotEmpty()) {
                val dir = File(chosen).parentFile
                if (dir != null) return dir
            }
            return File(Environment.getExternalStorageDirectory(), USUAL_KEYS_FOLDER)
        }

    /**
     * Entering the keys step: the path the player has (or the settings page holds) is checked. The app does
     * not pick one for them - the picker opens where the keys usually are, and what the player takes is what
     * the step talks about.
     */
    private fun prefillKeys() {
        if (keysSkipped || keysOk) return
        if (keysPath.isNotBlank()) checkKeys()
    }

    /** Whether the file can be read: the port reads it, so a file that cannot be read is not good keys */
    private fun readable(path: String): Boolean = try {
        File(path).inputStream().use { it.read(ByteArray(16)); true }
    } catch (e: Exception) {
        false
    }

    /** The doctor's prod.keys row says whether the keys are good */
    fun checkKeys() {
        val p = keysPath.trim()
        if (p.isEmpty() || !File(p).isFile()) {
            keysOk = false
            keysDetail = if (p.isEmpty()) locale.str(R.string.onb_no_keys_chosen) else locale.str(R.string.onb_file_missing, p)
            return
        }
        keysChecking = true
        keysDetail = ""
        val buf = StringBuilder()
        PortRunner(env, guestLauncher, locale) { line -> buf.appendLine(line) }.doctor(p, json = true) {
            keysChecking = false
            // the port reads the file: a file that cannot be read is not good keys
            if (!readable(p)) {
                keysOk = false
                keysDetail = locale.str(R.string.onb_keys_unreadable, p)
                return@doctor
            }
            // the runner's own lines are in the same stream: only the doctor's own rows are JSON
            val rows = DoctorOutput.rows(buf.toString())
            if (rows == null) {
                keysOk = false
                keysDetail = locale.str(R.string.onb_check_no_output)
                return@doctor
            }
            val r = DoctorOutput.row(rows, "prod.keys")
            if (r == null) {
                keysOk = false
                keysDetail = locale.str(R.string.onb_check_no_keys)
                return@doctor
            }
            keysOk = r.optBoolean("ok")
            keysDetail = r.optString("detail")
        }
    }
}
