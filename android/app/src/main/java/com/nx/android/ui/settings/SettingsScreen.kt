package com.nx.android.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.OnboardingState
import com.nx.android.data.Env
import com.nx.android.data.LosslessDll
import com.nx.android.data.PortRunner
import com.nx.android.ui.AppActions
import com.nx.android.ui.components.PickerMode
import com.nx.android.ui.components.PickerRequest
import com.nx.android.ui.components.CapsuleButton
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.Section
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.components.rememberPageScroll
import com.nx.android.ui.theme.GlassSheet
import com.nx.android.ui.theme.NxPalette
import java.io.File

/**
 * Settings: the environment's state and its prepare/reset, the doctor's check and the keys' path, the output
 * folder, the log (view, clear, share), the version and the way back into the guided setup.
 */
@Composable
fun SettingsScreen(ob: OnboardingState, lib: LibraryState, env: Env,
                   actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberPageScroll()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EnvSection(ob, actions)
        DoctorSection(env, actions, onRequest)
        PathsSection(lib, env, actions, onRequest)
        FrameGenSection(actions, onRequest)
        LogSection(actions)
        AboutSection(ob, actions)
        Spacer(Modifier.height(12.dp))
    }
}

/** 帧生成（Lossless.dll）：设备级的一次性配置。状态以私有副本为准，选择后校验并复制（不内置、不下载）。 */
@Composable
private fun FrameGenSection(actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var model by remember { mutableStateOf(LosslessDll.ensure(context)?.absolutePath) }
    Section("帧生成（Lossless.dll）") {
        Text(
            if (model != null) "已就绪：$model"
            else "还没有 Lossless.dll。放在常见位置（Documents/Emulation/bios、Switch、Download）会自动发现，或在这里选一个。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton("选择 Lossless.dll", onClick = {
                onRequest(PickerRequest(PickerMode.FILE, emptySet(), null, "选择 Lossless.dll", onPicked = { f ->
                    if (LosslessDll.install(context, f)) {
                        model = LosslessDll.ensure(context)?.absolutePath
                        actions.onToast("帧生成已就绪")
                    } else actions.onToast("这个文件不像 Lossless.dll（PE 镜像，至少 100 KB）")
                }))
            })
            if (model != null) QuietButton("重新发现", onClick = { model = LosslessDll.ensure(context)?.absolutePath })
        }
        Text("游戏运行中在抽屉里打开帧生成即可使用；DLL 的更新会自动拷到私有目录。",
             style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EnvSection(ob: OnboardingState, actions: AppActions) {
    var confirmReset by remember { mutableStateOf(false) }
    Section("环境") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ob.unpacking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
            } else {
                StatusDot(if (ob.envReady) NxPalette.Ready else NxPalette.Danger)
            }
            Text(
                when {
                    ob.unpacking -> "正在解压：${ob.unpackStep}"
                    ob.envReady -> "环境就绪${if (ob.lastCheck.isEmpty()) "" else "（${ob.lastCheck}）"}"
                    else -> "环境还没准备好（第一次要解压约 1.6 GB）"
                },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CapsuleButton("准备环境", { ob.unpack() }, enabled = !ob.unpacking, modifier = Modifier.weight(1f))
            QuietButton("重置环境", { confirmReset = true }, enabled = !ob.unpacking, modifier = Modifier.weight(1f))
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("重置环境？") },
            text = { Text("解压好的 rootfs、编译工具和 .NET SDK 会被删掉（约 1.6 GB），下次移植前要重新解压。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    ob.resetEnv()
                    actions.onToast("已重置：环境会在下次准备时重新解压")
                }) { Text("重置", color = NxPalette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun DoctorSection(env: Env, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    var running by remember { mutableStateOf(false) }
    var out by remember { mutableStateOf(listOf<String>()) }
    Section("自检") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "检查移植需要的工具：rootfs、编译器、.NET SDK、prod.keys",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            if (running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
            else QuietButton("检查工具", {
                running = true
                out = listOf()
                PortRunner(env) { line -> out = (out + line).takeLast(60) }
                    .doctor(actions.keys(), json = false) { running = false }
            })
        }
        KeysRow(actions, onRequest)
        if (out.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
            ) {
                for (line in out) {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (line.startsWith("!!") || line.startsWith("[RED]")) NxPalette.Danger
                        else MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeysRow(actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val keys = actions.keys()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusDot(if (keys.isNotBlank() && File(keys).isFile()) NxPalette.Ready else NxPalette.Danger, 8.dp)
        Column(Modifier.weight(1f)) {
            Text("prod.keys", style = MaterialTheme.typography.bodyMedium)
            Text(keys.ifEmpty { "还没有设置" }, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        QuietButton("修改", onClick = {
            onRequest(PickerRequest(PickerMode.FILE, emptySet(), null, "选择 prod.keys",
                onPicked = { f -> actions.onSetKeys(f.path) }))
        })
    }
}

@Composable
private fun PathsSection(lib: LibraryState, env: Env, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val out = actions.out()
    Section("输出目录") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("移植产物（数据包）写在这里", style = MaterialTheme.typography.bodyMedium)
                Text(out, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            QuietButton("修改", onClick = {
                onRequest(PickerRequest(PickerMode.DIR, emptySet(), File(out), "选择输出目录",
                    onPicked = { dir ->
                        actions.onSetOut(dir.path)
                        lib.rescan()
                    }))
            })
        }
        Hint("输出目录一换，游戏库的「已完成」就按新目录重新算")
    }
}

@Composable
private fun LogSection(actions: AppActions) {
    var show by remember { mutableStateOf(false) }
    Section("日志") {
        Text("工具说的每一句都在一个文件里：出问题时可以从这里分享出去。",
             style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton("查看", { show = true })
            QuietButton("分享", { actions.onShareLog() })
            QuietButton("清空", { actions.onClearLog() })
        }
    }
    if (show) {
        BackHandler { show = false }
        LogOverlay(onClose = { show = false }, text = remember { actions.readLog() })
    }
}

@Composable
private fun LogOverlay(onClose: () -> Unit, text: String) {
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        GlassSheet(modifier = Modifier.fillMaxSize(0.9f).clickable(enabled = false) {}) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                         modifier = Modifier.weight(1f))
                    QuietButton("关闭", onClose)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text.ifEmpty { "日志还是空的" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSection(ob: OnboardingState, actions: AppActions) {
    Section("关于") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("版本", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(actions.version(), style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("重新运行引导", style = MaterialTheme.typography.bodyMedium)
                Hint("已完成的部分（权限、环境、keys）会被认出来，直接呈现为完成")
            }
            QuietButton("运行", { ob.open() })
        }
    }
}
