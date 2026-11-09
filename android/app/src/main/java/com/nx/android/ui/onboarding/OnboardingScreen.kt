package com.nx.android.ui.onboarding

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.appstate.OnboardingState
import com.nx.android.data.AppLocale
import com.nx.android.domain.ObStep
import com.nx.android.domain.OnboardingLogic
import com.nx.android.ui.AppActions
import com.nx.android.ui.LocalAppLocale
import com.nx.android.ui.components.CapsuleButton
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.PickerMode
import com.nx.android.ui.components.PickerRequest
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.Section
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.theme.NxPalette
import java.io.File

/**
 * The guided setup, five steps: the permission and the space, the environment's unpack, the keys, the games'
 * folder, done. The gates are OnboardingLogic's; this view only draws and sends intents.
 */
@Composable
fun OnboardingScreen(ob: OnboardingState, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val facts = ob.facts()
    val can = OnboardingLogic.canAct(ob.step, facts)

    // the back key walks the flow backwards, it does not leave the app
    BackHandler(enabled = ob.step > ObStep.WELCOME) { ob.back() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
           verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // the header: where the player is, and the way out for later
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stepTitle(ob.step), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.onb_step_of, ob.step.ordinal + 1, ObStep.entries.size - 1),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { ob.skipForLater() }) { Text(stringResource(R.string.onb_later), color = NxPalette.Cream) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in ObStep.entries) {
                val i = s.ordinal
                Box(
                    Modifier.width(if (i == ob.step.ordinal) 22.dp else 8.dp).height(8.dp)
                        .background(
                            when {
                                i < ob.step.ordinal -> NxPalette.Ready
                                i == ob.step.ordinal -> NxPalette.Cream
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
                when (ob.step) {
                    ObStep.WELCOME -> WelcomeStep(ob, actions)
                    ObStep.ENV -> EnvStep(ob)
                    ObStep.KEYS -> KeysStep(ob, actions, onRequest)
                    ObStep.GAMES -> GamesStep(ob, actions, onRequest)
                    ObStep.DONE -> DoneStep()
                }
            }
        }

        // the footer: back and the step's main action
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ob.step > ObStep.WELCOME) {
                QuietButton(stringResource(R.string.common_back), { ob.back() })
            }
            Spacer(Modifier.weight(1f))
            CapsuleButton(
                text = primaryLabel(ob.step, can),
                onClick = { if (ob.step == ObStep.DONE) ob.openLibrary() else ob.next() },
                enabled = can,
            )
        }
    }
}

@Composable
private fun stepTitle(s: ObStep): String = when (s) {
    ObStep.WELCOME -> stringResource(R.string.onb_welcome)
    ObStep.ENV -> stringResource(R.string.onb_prepare_env)
    ObStep.KEYS -> "prod.keys"
    ObStep.GAMES -> stringResource(R.string.onb_game_folder)
    ObStep.DONE -> stringResource(R.string.onb_all_ready)
}

@Composable
private fun primaryLabel(s: ObStep, can: Boolean): String = when (s) {
    ObStep.WELCOME -> stringResource(R.string.common_start)
    ObStep.ENV -> if (can) stringResource(R.string.common_continue) else stringResource(R.string.onb_waiting_env)
    ObStep.KEYS -> if (can) stringResource(R.string.common_continue) else stringResource(R.string.onb_waiting_check)
    ObStep.GAMES -> stringResource(R.string.common_continue)
    ObStep.DONE -> stringResource(R.string.onb_enter_library)
}

// ---- the steps -----------------------------------------------------------------------------------------------

@Composable
private fun WelcomeStep(ob: OnboardingState, actions: AppActions) {
    val facts = ob.facts()
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
private fun EnvStep(ob: OnboardingState) {
    val ready = ob.envReady
    Section(stringResource(R.string.onb_runtime)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ob.unpacking) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
            } else {
                StatusDot(if (ready) NxPalette.Ready else NxPalette.Building)
            }
            Text(
                when {
                    ready -> stringResource(R.string.env_ready)
                    ob.unpacking -> stringResource(R.string.onb_unpacking, ob.unpackStep)
                    else -> stringResource(R.string.onb_no_env)
                },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            if (!ready && !ob.unpacking) QuietButton(stringResource(R.string.onb_prepare_env), { ob.unpack() })
        }
        if (ob.unpacking) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(5.dp), color = NxPalette.Building)
        }
        if (ob.unpackError.isNotEmpty()) {
            Text(
                stringResource(R.string.onb_prepare_failed, ob.unpackError),
                color = NxPalette.Danger,
                style = MaterialTheme.typography.bodySmall,
            )
            Hint(stringResource(R.string.onb_retry_hint))
        }
    }
    Hint(stringResource(R.string.onb_unpack_once))
}

@Composable
private fun KeysStep(ob: OnboardingState, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val locale = LocalAppLocale.current
    Section(stringResource(R.string.onb_prod_keys)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(
                when {
                    ob.keysOk -> NxPalette.Ready
                    ob.keysChecking -> NxPalette.Building
                    else -> NxPalette.Danger
                }
            )
            Column(Modifier.weight(1f)) {
                Text(
                    ob.keysPath.ifEmpty { stringResource(R.string.onb_no_keys_file) },
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                )
                if (ob.keysDetail.isNotEmpty()) {
                    Hint(ob.keysDetail)
                }
            }
            if (ob.keysChecking) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
            } else {
                QuietButton(stringResource(R.string.onb_choose_file), onClick = {
                    onRequest(PickerRequest(PickerMode.FILE, setOf("keys"), null, locale.str(R.string.onb_choose_prod_keys),
                        onPicked = { f -> ob.chooseKeys(f.path) }))
                })
            }
        }
        Hint(stringResource(R.string.onb_keys_common_places))
    }
    if (!ob.keysOk && !ob.keysChecking) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ob.keysSkipped, onCheckedChange = { if (it) ob.skipKeys() else ob.unskipKeys() })
            Text(stringResource(R.string.onb_skip_keys), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GamesStep(ob: OnboardingState, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val locale = LocalAppLocale.current
    Section(stringResource(R.string.onb_game_folder)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(if (ob.folder.isNotBlank()) NxPalette.Ready else NxPalette.Unbuilt)
            Text(
                ob.folder.ifEmpty { stringResource(R.string.onb_no_folder) },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f), maxLines = 2,
            )
            QuietButton(stringResource(R.string.onb_choose_folder), onClick = {
                onRequest(PickerRequest(PickerMode.DIR, emptySet(),
                    File(ob.folder.ifBlank { "/storage" }), locale.str(R.string.app_choose_game_folder),
                    onPicked = { dir -> ob.chooseFolder(dir.path) }))
            })
        }
        if (ob.folder.isNotBlank()) {
            Hint(if (ob.games.isEmpty()) stringResource(R.string.onb_no_games_found)
                 else stringResource(R.string.onb_games_found, ob.games.size))
            if (ob.games.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (g in ob.games.take(30)) {
                        Text(
                            "· ${g.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                    }
                    if (ob.games.size > 30) Hint(stringResource(R.string.onb_and_more, ob.games.size - 30))
                }
            }
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
