package com.nx.android.feature.onboarding

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nx.android.appstate.OnboardingState
import com.nx.android.domain.ObFacts
import com.nx.android.domain.ObStep
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class OnboardingUiState(
    val active: Boolean = false,
    val step: ObStep = ObStep.WELCOME,
    val facts: ObFacts = ObFacts(false, 0, OnboardingState.NEEDED_GB, false, "", false, false),
    val envReady: Boolean = false,
    val unpacking: Boolean = false,
    val unpackStep: String = "",
    val unpackError: String = "",
    val keysOk: Boolean = false,
    val keysChecking: Boolean = false,
    val keysPath: String = "",
    val keysDetail: String = "",
    val keysSkipped: Boolean = false,
    val keysStartDir: File = File("/storage/switch"),
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val onboarding: OnboardingState,
) : ViewModel() {
    val uiState: StateFlow<OnboardingUiState> = snapshotFlow {
        OnboardingUiState(
            active = onboarding.active,
            step = onboarding.step,
            facts = onboarding.facts(),
            envReady = onboarding.envReady,
            unpacking = onboarding.unpacking,
            unpackStep = onboarding.unpackStep,
            unpackError = onboarding.unpackError,
            keysOk = onboarding.keysOk,
            keysChecking = onboarding.keysChecking,
            keysPath = onboarding.keysPath,
            keysDetail = onboarding.keysDetail,
            keysSkipped = onboarding.keysSkipped,
            keysStartDir = onboarding.keysStartDir,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun openLibrary() = onboarding.openLibrary()
    fun next() = onboarding.next()
    fun back() = onboarding.back()
    fun unpack() = onboarding.unpack()
    fun chooseKeys(path: String) = onboarding.chooseKeys(path)
    fun skipKeys() = onboarding.skipKeys()
    fun unskipKeys() = onboarding.unskipKeys()
}
