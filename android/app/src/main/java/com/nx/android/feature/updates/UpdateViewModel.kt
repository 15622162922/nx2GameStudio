package com.nx.android.feature.updates

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nx.android.appstate.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class UpdateUiState(
    val phase: UpdateState.Phase = UpdateState.Phase.IDLE,
    val latest: String = "",
    val mine: String = "",
    val notes: String = "",
    val later: Boolean = false,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    val update: UpdateState,
) : ViewModel() {
    val uiState: StateFlow<UpdateUiState> = snapshotFlow {
        UpdateUiState(update.phase, update.latest, update.mine, update.notes, update.later)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateUiState())

    fun checkNow() = update.checkNow()
    fun postpone() = update.postpone()
}
