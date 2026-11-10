package com.nx.android.ui.env

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.AppLog
import com.nx.android.data.AppLocale
import com.nx.android.data.Env
import com.nx.android.data.PrefsStore
import com.nx.android.data.PortRunner
import com.nx.android.domain.DoctorOutput
import com.nx.android.domain.EnvCheck
import java.io.File

/**
 * The environment's state: the two cheap facts the top bar's chip shows, and the doctor's rows.
 *
 * The facts are read from the filesystem, so they are read again whenever the screen comes back and whenever
 * the player changed one of them (the environment was prepared, the keys were replaced). The doctor runs a
 * python inside the rootfs - seconds, not milliseconds - so it only runs when the player asks for it, and its
 * rows stay until the next run.
 */
class EnvCheckState(private val env: Env, private val prefs: PrefsStore, private val locale: AppLocale) {

    /** The environment is prepared (the rootfs, the tool and the compiler are there) */
    var envReady by mutableStateOf(false); private set

    /** The keys file the app uses (the setup and the settings page write the same one) */
    var keysPath by mutableStateOf(""); private set

    /** The keys file is there */
    var keysOk by mutableStateOf(false); private set

    /** The facts have been read: before that, the chip says it is checking */
    var known by mutableStateOf(false); private set

    /** The chip's verdict */
    var chip by mutableStateOf(EnvCheck.Chip.CHECKING); private set

    /** The doctor is running */
    var running by mutableStateOf(false); private set

    /** The doctor has run: its rows are below */
    var checked by mutableStateOf(false); private set

    /** The rows the doctor printed */
    var rows by mutableStateOf(listOf<EnvCheck.Row>()); private set

    /** The doctor ran, but nothing came out that could be read */
    var noOutput by mutableStateOf(false); private set

    /** Reads the two facts again */
    fun refresh() {
        envReady = env.ready
        keysPath = prefs.keys
        keysOk = keysPath.isNotBlank() && File(keysPath).isFile()
        known = true
        chip = EnvCheck.chip(envReady, keysOk)
    }

    /**
     * Runs the doctor's check and puts its rows here.
     *
     * The raw output goes to the log: the settings page's own output box is gone, and the log is where the
     * output of a broken environment can be looked at (and shared).
     */
    fun runDoctor() {
        if (running) return
        running = true
        checked = false
        noOutput = false
        rows = listOf()
        val out = StringBuilder()
        PortRunner(env, locale) { line -> out.appendLine(line); AppLog.line(line) }
            .doctor(keysPath, json = true) {
                running = false
                val parsed = DoctorOutput.rows(out.toString())
                if (parsed == null) {
                    noOutput = true
                } else {
                    rows = EnvCheck.rows(parsed)
                }
                checked = true
            }
    }
}
