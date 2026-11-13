package com.nx.android.appstate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.PrefsStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class RuntimeUpdatePhase { IDLE, RUNNING, SUCCEEDED, FAILED }

/** One modal operation, independent of port progress, history and source-file associations. */
@Singleton
class RuntimeUpdateState private constructor(
    private val savePending: (String?) -> Unit,
    pending: String?,
) {
    @Inject constructor(prefs: PrefsStore) : this(
        { prefs.put(PrefsStore.RUNTIME_PENDING_PACK, it) },
        prefs.str(PrefsStore.RUNTIME_PENDING_PACK).takeIf { it.isNotBlank() },
    )

    constructor(pending: String? = null) : this({}, pending)

    var pack by mutableStateOf(pending.orEmpty()); private set
    var name by mutableStateOf(pending?.let { File(it).name }.orEmpty()); private set
    var interrupted by mutableStateOf(pending != null); private set
    var reason by mutableStateOf(""); private set
    var phase by mutableStateOf(if (pending == null) RuntimeUpdatePhase.IDLE else RuntimeUpdatePhase.FAILED); private set
    val running: Boolean get() = phase == RuntimeUpdatePhase.RUNNING

    fun begin(path: String, label: String): Boolean {
        if (running || path.isBlank()) return false
        pack = path
        name = label
        interrupted = false
        reason = ""
        savePending(path)
        phase = RuntimeUpdatePhase.RUNNING
        return true
    }

    fun finish(success: Boolean, why: String = "") {
        if (!running) return
        savePending(null)
        reason = if (success) "" else why
        phase = if (success) RuntimeUpdatePhase.SUCCEEDED else RuntimeUpdatePhase.FAILED
    }

    fun dismiss() {
        if (running) return
        savePending(null)
        phase = RuntimeUpdatePhase.IDLE
        pack = ""
        name = ""
        interrupted = false
        reason = ""
    }
}
