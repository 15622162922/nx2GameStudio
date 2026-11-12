package com.nx.android.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.snapshotFlow
import com.nx.android.appstate.LibraryState
import com.nx.android.domain.GameEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Restorable-sized view of library navigation and scan state. */
data class LibraryUiState(
    val games: List<GameEntry> = emptyList(),
    val scanning: Boolean = false,
    val heroIndex: Int = 0,
    val selectedTitleId: String? = null,
    val buildsTarget: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    val library: LibraryState,
) : ViewModel() {
    val uiState: StateFlow<LibraryUiState> = snapshotFlow {
        LibraryUiState(
            games = library.games,
            scanning = library.scanning,
            heroIndex = library.heroIndex,
            selectedTitleId = library.selected?.titleId,
            buildsTarget = library.buildsTarget?.absolutePath,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun refresh() = library.rescan()

    fun selectHero(index: Int, count: Int) = library.selectHero(index, count)

    fun selectGame(game: GameEntry?) { library.selected = game }

    fun setBuildTarget(file: File?) { library.buildsTarget = file }
}
