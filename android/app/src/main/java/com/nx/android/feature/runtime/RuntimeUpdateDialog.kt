package com.nx.android.feature.runtime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.nx.android.R
import com.nx.android.appstate.RuntimeUpdatePhase
import com.nx.android.appstate.RuntimeUpdateState

@Composable
fun RuntimeUpdateDialog(state: RuntimeUpdateState, onRetry: () -> Unit,
                        onRePort: () -> Unit, onFinished: () -> Unit) {
    LaunchedEffect(state.phase) {
        if (state.phase == RuntimeUpdatePhase.SUCCEEDED) {
            state.dismiss()
            onFinished()
        }
    }
    if (state.phase != RuntimeUpdatePhase.RUNNING && state.phase != RuntimeUpdatePhase.FAILED) return
    AlertDialog(
        onDismissRequest = { state.dismiss() },
        properties = DialogProperties(dismissOnBackPress = !state.running, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.runtime_update)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(state.name)
                if (state.running) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
                    Text(stringResource(R.string.runtime_updating))
                } else {
                    Text(stringResource(if (state.interrupted) R.string.runtime_interrupted else R.string.runtime_update_failed))
                }
            }
        },
        confirmButton = {
            if (!state.running) TextButton(onClick = onRetry) { Text(stringResource(R.string.runtime_retry)) }
        },
        dismissButton = {
            if (!state.running) {
                Row {
                    TextButton(onClick = onRePort) { Text(stringResource(R.string.runtime_full_port)) }
                    TextButton(onClick = { state.dismiss() }) { Text(stringResource(R.string.common_close)) }
                }
            }
        },
    )
}
