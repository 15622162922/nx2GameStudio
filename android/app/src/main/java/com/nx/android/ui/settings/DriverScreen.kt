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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.appstate.DriverState
import com.nx.android.data.DriverCatalog
import com.nx.android.data.DriverRelease
import com.nx.android.data.Drivers
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.StatusDot
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
        GlassSheet(modifier = Modifier.fillMaxSize(0.9f).clickable(enabled = false) {}) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Vulkan 驱动", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    QuietButton("关闭", onClose)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("装好的驱动在游戏库页的抽屉里选：游戏的运行选项里。",
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
                                Hint(r.note, size = 11.sp)
                            }
                            if (have) {
                                // 内置的那个是 App 的一部分，删不掉，也不装
                                Hint(if (shipped) "内置" else "已装上", size = 11.sp)
                            } else if (working) {
                                Hint("…", size = 11.sp)
                            } else if (shipped) {
                                // 它自己会就位（下一个启动的时候），玩家不用点
                                Hint(if (Drivers.shippedArchivePresent(context)) "下次启动会装好" else "这个包里没有", size = 11.sp)
                            } else {
                                QuietButton("下载", {
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
                                                say("已安装：${it.label}")
                                            },
                                            onFailure = { say("下载失败：${it.message.orEmpty()}") },
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
