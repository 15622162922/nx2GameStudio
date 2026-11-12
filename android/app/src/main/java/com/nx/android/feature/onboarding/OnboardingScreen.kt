package com.nx.android.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nx.android.R
import com.nx.android.app.AppActions
import com.nx.android.core.ui.LocalAppLocale
import com.nx.android.core.ui.components.CapsuleButton
import com.nx.android.core.ui.components.Hint
import com.nx.android.core.ui.components.QuietButton
import com.nx.android.core.ui.components.Section
import com.nx.android.core.ui.components.StatusDot
import com.nx.android.core.ui.theme.NxPalette
import com.nx.android.domain.ObStep
import com.nx.android.domain.OnboardingLogic
import com.nx.android.feature.filepicker.PickerMode
import com.nx.android.feature.filepicker.PickerRequest

/**
 * The guided setup, five steps: the permission and the space, the environment's unpack, the keys, the games'
 * folder, done. The gates are OnboardingLogic's; this view only draws and sends intents.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val can = OnboardingLogic.canAct(uiState.step, uiState.facts)

    // the back key walks the flow backwards, it does not leave the app
    BackHandler(enabled = uiState.step > ObStep.WELCOME) { viewModel.back() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
           verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // the header: where the player is
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stepTitle(uiState.step), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.onb_step_of, uiState.step.ordinal + 1, ObStep.entries.size - 1),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in ObStep.entries) {
                val i = s.ordinal
                Box(
                Modifier.width(if (i == uiState.step.ordinal) 22.dp else 8.dp).height(8.dp)
                        .background(
                            when {
                                i < uiState.step.ordinal -> NxPalette.Ready
                                i == uiState.step.ordinal -> NxPalette.Cream
                                else -> NxPalette.Unbuilt.copy(alpha = 0.4f)
                            },
                            RoundedCornerShape(50)
                        )
                )
            }
        }

        Box(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (uiState.step) {
                    ObStep.WELCOME -> WelcomeStep(uiState.facts, actions)
                    ObStep.ENV -> EnvStep(uiState, viewModel)
                    ObStep.KEYS -> KeysStep(uiState, viewModel, actions, onRequest)
                    ObStep.DONE -> DoneStep()
                }
            }
        }

        // the footer: back and the step's main action
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (uiState.step > ObStep.WELCOME) {
                QuietButton(stringResource(R.string.common_back), { viewModel.back() })
            }
            Spacer(Modifier.weight(1f))
            CapsuleButton(
                text = primaryLabel(uiState.step, can),
                onClick = { if (uiState.step == ObStep.DONE) viewModel.openLibrary() else viewModel.next() },
                enabled = can,
            )
        }
    }
}

@Composable
private fun stepTitle(s: ObStep): String = when (s) {
    ObStep.WELCOME -> stringResource(R.string.onb_welcome, stringResource(R.string.app_name))
    ObStep.ENV -> stringResource(R.string.onb_prepare_env)
    ObStep.KEYS -> "prod.keys"
    ObStep.DONE -> stringResource(R.string.onb_all_ready)
}

@Composable
private fun primaryLabel(s: ObStep, can: Boolean): String = when (s) {
    ObStep.WELCOME -> stringResource(R.string.common_start)
    ObStep.ENV -> if (can) stringResource(R.string.common_continue) else stringResource(R.string.onb_waiting_env)
    ObStep.KEYS -> if (can) stringResource(R.string.common_continue) else stringResource(R.string.onb_waiting_check)
    ObStep.DONE -> stringResource(R.string.onb_enter_library)
}

// ---- the steps -----------------------------------------------------------------------------------------------

@Composable
private fun WelcomeStep(facts: com.nx.android.domain.ObFacts, actions: AppActions) {
    Section(stringResource(R.string.onb_storage_access)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(if (facts.storageOk) NxPalette.Ready else NxPalette.Danger)
            Text(
                if (facts.storageOk) stringResource(R.string.onb_storage_granted)
                else stringResource(R.string.onb_storage_why),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (!facts.storageOk) QuietButton(stringResource(R.string.onb_grant_access), { actions.onAskStorage() })
        }
        Hint(stringResource(R.string.onb_grant_access_where))
    }
    Section(stringResource(R.string.onb_disk_space)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(if (facts.freeGB >= facts.neededGB) NxPalette.Ready else NxPalette.Danger)
            Text(
                stringResource(R.string.onb_free_space, facts.freeGB),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Hint(if (facts.freeGB < facts.neededGB) stringResource(R.string.onb_space_short) else stringResource(R.string.onb_space_enough))
    }
}

@Composable
private fun EnvStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel) {
    val ready = uiState.envReady
    Section(stringResource(R.string.onb_runtime)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (uiState.unpacking) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
            } else {
                StatusDot(if (ready) NxPalette.Ready else NxPalette.Building)
            }
            Text(
                when {
                    ready -> stringResource(R.string.env_ready)
                    uiState.unpacking -> stringResource(R.string.onb_unpacking, uiState.unpackStep)
                    else -> stringResource(R.string.onb_no_env)
                },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            if (!ready && !uiState.unpacking) QuietButton(stringResource(R.string.onb_prepare_env), { viewModel.unpack() })
        }
        if (uiState.unpacking) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(5.dp), color = NxPalette.Building)
        }
        if (uiState.unpackError.isNotEmpty()) {
            Text(
                stringResource(R.string.onb_prepare_failed, uiState.unpackError),
                color = NxPalette.Danger,
                style = MaterialTheme.typography.bodySmall,
            )
            Hint(stringResource(R.string.onb_retry_hint))
        }
    }
    Hint(stringResource(R.string.onb_unpack_once))
}

@Composable
private fun KeysStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val locale = LocalAppLocale.current
    Section(stringResource(R.string.onb_prod_keys)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(
                when {
                    uiState.keysOk -> NxPalette.Ready
                    uiState.keysChecking -> NxPalette.Building
                    uiState.keysPath.isNotBlank() -> NxPalette.Danger
                    else -> NxPalette.Unbuilt
                }
            )
            Column(Modifier.weight(1f)) {
                Text(
                    uiState.keysPath.ifEmpty { stringResource(R.string.onb_no_keys_file) },
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                )
                if (uiState.keysDetail.isNotEmpty()) {
                    Hint(uiState.keysDetail)
                }
            }
            if (uiState.keysChecking) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
            } else {
                QuietButton(stringResource(R.string.onb_choose_file), onClick = {
                    onRequest(PickerRequest(PickerMode.FILE, setOf("keys"), uiState.keysStartDir, locale.str(R.string.onb_choose_prod_keys),
                        onPicked = { f -> viewModel.chooseKeys(f.path) }))
                })
            }
        }
        Hint(stringResource(R.string.onb_keys_common_places))
    }
    if (!uiState.keysOk && !uiState.keysChecking) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.keysSkipped, onCheckedChange = { if (it) viewModel.skipKeys() else viewModel.unskipKeys() })
            Text(stringResource(R.string.onb_skip_keys), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DoneStep() {
    Section(stringResource(R.string.onb_done)) {
        Text(
            stringResource(R.string.onb_done_text) + "\n" +
                stringResource(R.string.onb_done_next),
            style = MaterialTheme.typography.bodyMedium,
        )
        Hint(stringResource(R.string.onb_skipped_steps))
    }
}
