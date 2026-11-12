package com.nx.android.feature.builds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.snapshotFlow
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.PortState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import com.nx.android.domain.porting.PortPhase
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
) : ViewModel() {
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

    fun setTarget(file: File?) { library.buildsTarget = file }

    fun clearLog() = port.clearLog()
}
