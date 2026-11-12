package com.nx.android.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** A second-by-second count while a task runs. */
@Composable
fun elapsedSecs(startedAt: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (startedAt > 0) { delay(1000); now = System.currentTimeMillis() }
    }
    return if (startedAt > 0) ((now - startedAt) / 1000).coerceAtLeast(0) else 0
}
