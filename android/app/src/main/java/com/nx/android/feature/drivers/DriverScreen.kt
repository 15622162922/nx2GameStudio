package com.nx.android.feature.drivers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nx.android.R
import com.nx.android.domain.drivers.DriverRelease
import com.nx.android.domain.drivers.DriverNote
import com.nx.android.ui.LocalAppLocale
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.failureText
import com.nx.android.ui.theme.GlassSheet
import com.nx.android.ui.theme.NxPalette

@Composable
fun DriverRoute(onClose: () -> Unit, viewModel: DriverViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler { onClose() }
    DriverScreen(state, onClose, viewModel::download, viewModel::dismissMessage)
}

@Composable
fun DriverScreen(
    state: DriverUiState,
    onClose: () -> Unit,
    onDownload: (DriverRelease) -> Unit,
    onDismissMessage: () -> Unit,
) {
    val locale = LocalAppLocale.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        GlassSheet(modifier = Modifier.fillMaxSize(0.9f).clickable(enabled = false) {}, alpha = 0.97f) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(locale.str(R.string.driver_section), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    QuietButton(locale.str(R.string.common_close), onClose)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(locale.str(R.string.driver_where), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (release in state.releases) {
                        val installed = state.installed.any { it.name == release.metaName }
                        val working = state.workingFile == release.file
                        val shipped = release.file == state.shippedReleaseFile
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatusDot(if (installed) NxPalette.Ready else NxPalette.Unbuilt)
                            Column(Modifier.weight(1f)) {
                                Text(release.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Hint(stringResource(release.note.toResource()), size = 11.sp)
                            }
                            if (installed) {
                                Hint(if (shipped) locale.str(R.string.driver_builtin)
                                    else locale.str(R.string.driver_installed_short), size = 11.sp)
                            } else if (working) {
                                Hint("…", size = 11.sp)
                            } else if (shipped) {
                                Hint(if (state.shippedFileAvailable) locale.str(R.string.driver_installed_next_start)
                                    else locale.str(R.string.driver_not_in_package), size = 11.sp)
                            } else {
                                QuietButton(locale.str(R.string.driver_download), onClick = { onDownload(release) })
                            }
                        }
                        if (working) {
                            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(),
                                color = NxPalette.Cream)
                        }
                    }
                    state.message?.let { result ->
                        val text = when (result) {
                            is DriverOperationMessage.Installed -> locale.str(R.string.driver_installed, result.label)
                            is DriverOperationMessage.Failed -> locale.str(R.string.driver_download_failed,
                                failureText(locale, result.cause))
                        }
                        Text(text, Modifier.clickable(onClick = onDismissMessage),
                            style = MaterialTheme.typography.bodySmall, color = NxPalette.Cream)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun DriverNote.toResource(): Int = when (this) {
    DriverNote.GPU_8XX -> R.string.driver_note_8xx
    DriverNote.GPU_7XX -> R.string.driver_note_7xx
    DriverNote.GPU_7XX_ONE_UI -> R.string.driver_note_7xx_oneui
}
