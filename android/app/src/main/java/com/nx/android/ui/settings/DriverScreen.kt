package com.nx.android.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.appstate.DriverState
import com.nx.android.data.AppLocale
import com.nx.android.data.DriverCatalog
import com.nx.android.data.DriverRelease
import com.nx.android.data.Drivers
import com.nx.android.ui.LocalAppLocale
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.failureText
import com.nx.android.ui.theme.GlassSheet
import com.nx.android.ui.theme.NxPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 驱动下载页：App 能拿到的三个构建（Adreno-Tools-Drivers 的 release v39），各自的地址是固定的。
 * 内置的那个（Turnip Gen8 V39）在 assets 里，不用网络；另外两个从 GitHub 下载。
 * 装好的驱动在设置页和这里读同一份（DriverState），两边不会不一致。
 */
@Composable
fun DriverScreen(state: DriverState, onClose: () -> Unit) {
    val locale = LocalAppLocale.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<DriverRelease?>(null) }   // 正在拿的那个
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { state.refresh(context) }   // 打开时重读 App 自己的目录

    BackHandler { onClose() }

    fun say(m: String) { message = m }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        // 这一页盖住整屏（它可能盖在设置页上），近乎不透明：底下页面的字不该透上来
        GlassSheet(modifier = Modifier.fillMaxSize(0.9f).clickable(enabled = false) {}, alpha = 0.97f) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(locale.str(R.string.driver_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    QuietButton(locale.str(R.string.common_close), onClose)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(locale.str(R.string.driver_where),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in DriverCatalog.RELEASES) {
                        val have = state.drivers.any { it.name == r.metaName }
                        val working = busy === r
                        val shipped = r.file == DriverCatalog.SHIPPED.file
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatusDot(if (have) NxPalette.Ready else NxPalette.Unbuilt)
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Hint(stringResource(r.note), size = 11.sp)
                            }
                            if (have) {
                                // 内置的那个是 App 的一部分，删不掉，也不装
                                Hint(if (shipped) locale.str(R.string.driver_builtin) else locale.str(R.string.driver_installed_short), size = 11.sp)
                            } else if (working) {
                                Hint("…", size = 11.sp)
                            } else if (shipped) {
                                // 它自己会就位（下一个启动的时候），玩家不用点
                                Hint(if (Drivers.shippedArchivePresent(context)) locale.str(R.string.driver_installed_next_start) else locale.str(R.string.driver_not_in_package), size = 11.sp)
                            } else {
                                QuietButton(locale.str(R.string.driver_download), {
                                    if (busy != null) return@QuietButton
                                    busy = r
                                    progress = 0f
                                    message = null
                                    scope.launch {
                                        val res = DriverCatalog.download(context, r) { progress = it }
                                        busy = null
                                        res.fold(
                                            onSuccess = {
                                                state.refresh(context)
                                                say(locale.str(R.string.driver_installed, it.label))
                                            },
                                            onFailure = { say(locale.str(R.string.driver_download_failed, failureText(locale, it))) },
                                        )
                                    }
                                })
                            }
                        }
                        if (working) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(),
                                color = NxPalette.Cream)
                        }
                    }
                    message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = NxPalette.Cream)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
