package com.nx.android.feature.drivers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nx.android.domain.drivers.Driver
import com.nx.android.domain.drivers.DriverRelease
import com.nx.android.domain.drivers.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DriverOperationMessage {
    data class Installed(val label: String) : DriverOperationMessage
    data class Failed(val cause: Throwable) : DriverOperationMessage
}

data class DriverUiState(
    val installed: List<Driver> = emptyList(),
    val releases: List<DriverRelease> = emptyList(),
    val shippedReleaseFile: String = "",
    val shippedFileAvailable: Boolean = false,
    val workingFile: String? = null,
    val progress: Float = 0f,
    val message: DriverOperationMessage? = null,
)

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val repository: DriverRepository,
) : ViewModel() {
    private val workingFile = MutableStateFlow<String?>(null)
    private val progress = MutableStateFlow(0f)
    private val message = MutableStateFlow<DriverOperationMessage?>(null)
    private val shippedFileAvailable = MutableStateFlow(false)

    val uiState: StateFlow<DriverUiState> = combine(
        repository.drivers, workingFile, progress, message, shippedFileAvailable,
    ) { installed, working, amount, result, shippedAvailable ->
        DriverUiState(installed, repository.releases, repository.shippedRelease.file, shippedAvailable, working, amount, result)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriverUiState(
        releases = repository.releases, shippedReleaseFile = repository.shippedRelease.file,
    ))

    init {
        viewModelScope.launch {
            repository.refresh()
            shippedFileAvailable.value = repository.shippedArchivePresent()
        }
    }

    fun download(release: DriverRelease) {
        if (workingFile.value != null) return
        viewModelScope.launch {
            workingFile.value = release.file
            progress.value = 0f
            message.value = null
            val result = repository.download(release) { progress.value = it }
            message.value = result.fold(
                onSuccess = { DriverOperationMessage.Installed(it.label) },
                onFailure = { DriverOperationMessage.Failed(it) },
            )
            workingFile.value = null
        }
    }

    fun dismissMessage() { message.value = null }
}
