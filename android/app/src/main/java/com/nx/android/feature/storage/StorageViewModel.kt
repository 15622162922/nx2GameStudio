package com.nx.android.feature.storage

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nx.android.appstate.StorageUsageState
import com.nx.android.domain.StorageUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StorageUiState(val snapshot: StorageUsage.Snapshot = StorageUsage.Snapshot())

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val usage: StorageUsageState,
) : ViewModel() {
    val uiState: StateFlow<StorageUiState> = snapshotFlow { StorageUiState(usage.snapshot) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUiState())

    private var visible = false
    private var foreground = true
    private var job: Job? = null

    fun setVisible(value: Boolean) {
        visible = value
        restartScan()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        restartScan()
    }

    fun measureNow() {
        if (!visible || !foreground) return
        job?.cancel()
        job = viewModelScope.launch { usage.load(); usage.prepare(); usage.scan(force = true) }
    }

    private fun restartScan() {
        job?.cancel()
        if (!visible || !foreground) return
        job = viewModelScope.launch {
            usage.load()
            usage.prepare()
            delay(StorageUsageState.GATE_MS)
            usage.scan()
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
