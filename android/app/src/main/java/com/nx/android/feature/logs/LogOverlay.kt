package com.nx.android.feature.logs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.core.ui.components.QuietButton
import com.nx.android.core.ui.theme.GlassSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_BYTES = 64 * 1024

@Composable
internal fun LogOverlay(
    onClose: () -> Unit,
    text: String,
    title: String? = null,
    buildId: String? = null,
    initialOffset: Long = 0,
    initialLength: Long = 0,
    active: Boolean = false,
    emptyText: String? = null,
    readRange: suspend (Long, Int) -> String = { _, _ -> "" },
    latestLength: suspend () -> Long = { initialLength },
) {
    var content by remember(buildId) { mutableStateOf(text) }
    var offset by remember(buildId) { mutableLongStateOf(initialOffset) }
    var fileLength by remember(buildId) { mutableLongStateOf(initialLength) }
    var following by remember(buildId) { mutableStateOf(active) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(buildId, text, initialOffset, initialLength) {
        content = text
        offset = initialOffset
        fileLength = initialLength
    }
    LaunchedEffect(active, buildId) {
        if (buildId != null && active) {
            while (true) {
                delay(800)
                val length = withContext(Dispatchers.IO) { latestLength() }
                fileLength = length
                if (following && length > 0) {
                    val start = (length - PAGE_BYTES).coerceAtLeast(0)
                    val latest = withContext(Dispatchers.IO) { readRange(start, PAGE_BYTES) }
                    content = latest
                    offset = start
                    scroll.scrollTo(Int.MAX_VALUE)
                }
            }
        }
    }
    LaunchedEffect(scroll, buildId) {
        snapshotFlow { scroll.value to scroll.maxValue }.collect { (value, max) ->
            if (max > 0) following = value >= max - 24
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        GlassSheet(modifier = Modifier.fillMaxSize(0.9f).clickable(enabled = false) {}, alpha = 0.97f) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title ?: stringResource(R.string.common_log), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    QuietButton(stringResource(R.string.common_close), onClose)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (buildId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (offset > 0) TextButton(onClick = {
                            val start = (offset - PAGE_BYTES).coerceAtLeast(0)
                            scope.launch {
                                val earlier = withContext(Dispatchers.IO) { readRange(start, (offset - start).toInt()) }
                                following = false
                                content = earlier + content
                                offset = start
                            }
                        }) { Text(stringResource(R.string.builds_log_earlier)) }
                        Spacer(Modifier.weight(1f))
                        if (active && !following) TextButton(onClick = {
                            scope.launch {
                                val length = withContext(Dispatchers.IO) { latestLength() }
                                val start = (length - PAGE_BYTES).coerceAtLeast(0)
                                content = withContext(Dispatchers.IO) { readRange(start, PAGE_BYTES) }
                                offset = start
                                fileLength = length
                                following = true
                                scroll.scrollTo(Int.MAX_VALUE)
                            }
                        }) { Text(stringResource(R.string.builds_log_latest)) }
                    }
                }
                Box(
                    Modifier.weight(1f).verticalScroll(scroll)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(10.dp)
                ) {
                    Text(
                        content.ifEmpty { emptyText ?: stringResource(R.string.main_log_empty) },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 11.sp,
                    )
                }
                if (buildId != null && offset + content.toByteArray(Charsets.UTF_8).size < fileLength) {
                    TextButton(onClick = {
                        val nextOffset = offset + content.toByteArray(Charsets.UTF_8).size
                        val size = (fileLength - nextOffset).coerceAtMost(PAGE_BYTES.toLong()).toInt()
                        scope.launch {
                            val later = withContext(Dispatchers.IO) { readRange(nextOffset, size) }
                            following = false
                            content += later
                        }
                    }) { Text(stringResource(R.string.builds_log_later)) }
                }
            }
        }
    }
}
