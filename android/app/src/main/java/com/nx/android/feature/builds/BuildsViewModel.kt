package com.nx.android.feature.builds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.snapshotFlow
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.PortState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import com.nx.android.domain.porting.PortPhase
import com.nx.android.domain.GameConfig
import com.nx.android.data.porting.BuildSessionRepository
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class BuildsUiState(
    val target: String? = null,
    val phase: PortPhase = PortPhase.IDLE,
    val progress: Float = 0f,
    val gameFile: String = "",
    val output: String = "",
    val startedAt: Long = 0L,
    val stageOf: Int = 0,
    val stages: Int = 0,
    val stageName: String = "",
    val stageStep: String = "",
    val log: List<String> = emptyList(),
    val history: List<PortState.Record> = emptyList(),
    val running: Boolean = false,
)

@HiltViewModel
class BuildsViewModel @Inject constructor(
    val library: LibraryState,
    val port: PortState,
    private val sessions: BuildSessionRepository,
) : ViewModel() {
    var selectedBuildId by mutableStateOf("")
        private set
    var preparedConfig by mutableStateOf<GameConfig?>(null)
        private set
    var preparedConfigWasMissing by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(false)
        private set
    val uiState: StateFlow<BuildsUiState> = snapshotFlow {
        BuildsUiState(
            target = library.buildsTarget?.absolutePath,
            phase = port.phase,
            progress = port.progress,
            gameFile = port.gameFile,
            output = port.outPath,
            startedAt = port.startedAt,
            stageOf = port.stageOf,
            stages = port.stages,
            stageName = port.stageName,
            stageStep = port.stageStep,
            log = port.log,
            history = port.history,
            running = port.running,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuildsUiState())

    fun setTarget(file: File?, config: GameConfig? = null, savedConfigMissing: Boolean = false) {
        selectedBuildId = ""
        preparedConfig = config
        preparedConfigWasMissing = savedConfigMissing
        preparing = file != null
        library.buildsTarget = file
    }

    fun buildStarted() { preparing = false }

    fun openBuild(id: String) { selectedBuildId = id }
    fun closeBuild() { selectedBuildId = "" }
    fun logTail(id: String): List<String> = if (id == port.buildId) port.log else sessions.tail(id)
    fun fullLog(id: String): String = runCatching {
        val file = sessions.logFile(id) ?: return ""
        file.readText(Charsets.UTF_8)
    }.getOrDefault("")

    fun clearLog() = port.clearLog()
}
