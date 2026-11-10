package com.nx.android.ui.env

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.appstate.OnboardingState
import com.nx.android.domain.EnvCheck
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.theme.NxPalette

/**
 * The checklist behind the top bar's chip: the two cheap checks and the doctor's rows.
 *
 * Every line says what it checked and, when the check has a way out, what the player can do about it. The
 * doctor's rows are below the two cheap ones: they cost seconds (a python inside the rootfs), so they run only
 * when the player asks for them.
 */
@Composable
fun EnvChecklistDialog(
    state: EnvCheckState,
    onboarding: OnboardingState,
    portRunning: Boolean,
    onPrepareEnv: () -> Unit,
    onPickKeys: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        // The card: a plain Box, not a GlassSheet. A GlassSheet sizes itself by its modifier (the driver page and
        // the log give it fillMaxSize); with only a widthIn it has no child that sizes it, and it collapses to
        // nothing - the scrim shows, the card does not.
        Box(
            Modifier.widthIn(max = 520.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .background(NxPalette.Surface.copy(alpha = 0.9f))
                .clickable(enabled = false) {},
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.env_check_title), style = MaterialTheme.typography.titleMedium,
                         fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    QuietButton(stringResource(R.string.common_close), onDismiss)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CheckLine(
                        stringResource(R.string.env_check_env),
                        if (state.envReady) EnvCheck.Level.OK else EnvCheck.Level.BAD,
                        when {
                            onboarding.unpacking -> stringResource(R.string.onb_unpacking, onboarding.unpackStep)
                            state.envReady -> stringResource(R.string.env_ready)
                            else -> stringResource(R.string.env_not_ready)
                        },
                        action = if (state.envReady || onboarding.unpacking) null else stringResource(R.string.onb_prepare_env),
                        onAction = onPrepareEnv,
                    )
                    CheckLine(
                        "prod.keys",
                        if (state.keysOk) EnvCheck.Level.OK else EnvCheck.Level.BAD,
                        if (state.keysOk) state.keysPath else stringResource(R.string.set_keys_not_set),
                        action = if (state.keysOk) null else stringResource(R.string.common_modify),
                        onAction = onPickKeys,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    when {
                        state.running -> Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
                            Text(stringResource(R.string.runner_checking_tools), style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.checked && state.noOutput -> Text(stringResource(R.string.env_check_no_output),
                            style = MaterialTheme.typography.bodyMedium, color = NxPalette.Danger)
                        state.checked -> for (row in state.rows) DoctorLine(row)
                        else -> Text(stringResource(R.string.env_check_hint), style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.running) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
                    } else {
                        QuietButton(stringResource(R.string.env_check_recheck), { state.runDoctor() }, enabled = canRun(state, portRunning))
                    }
                    Spacer(Modifier.weight(1f))
                    // the button is off for a reason, and the reason is written down
                    val why = when {
                        portRunning -> stringResource(R.string.env_check_busy)
                        !state.envReady -> stringResource(R.string.env_check_needs_env)
                        else -> ""
                    }
                    if (why.isNotEmpty()) {
                        Text(why, style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** The doctor's check needs the environment, and it does not run next to a port */
private fun canRun(state: EnvCheckState, portRunning: Boolean): Boolean =
    state.envReady && !state.running && !portRunning

/** One of the doctor's rows: its state, what it said, and the tool's own hint when it failed */
@Composable
private fun DoctorLine(row: EnvCheck.Row) {
    CheckLine(
        rowName(row.name),
        row.level,
        row.detail,
        fix = row.fix,
    )
}

/** The doctor's rows carry the tool's own names; the two that are words get the app's own */
@Composable
private fun rowName(name: String): String = when (name) {
    "Python modules" -> stringResource(R.string.env_check_modules)
    "Disk space" -> stringResource(R.string.env_check_disk)
    else -> name
}

/** One check: the dot by its state, the name, what it said, and the way out when it has one */
@Composable
private fun CheckLine(
    name: String,
    level: EnvCheck.Level,
    detail: String,
    fix: String = "",
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusDot(
            when (level) {
                EnvCheck.Level.OK -> NxPalette.Ready
                EnvCheck.Level.HINT -> NxPalette.Building
                EnvCheck.Level.BAD -> NxPalette.Danger
            },
            10.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (fix.isNotEmpty()) {
                Text(fix, style = MaterialTheme.typography.bodySmall, color = NxPalette.Building, fontSize = 11.sp)
            }
        }
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondary)
                    .clickable { onAction() }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
