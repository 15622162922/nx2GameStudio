package com.nx.android.ui.builds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.PortState
import com.nx.android.domain.GAME_FILE_EXTENSIONS
import com.nx.android.domain.FolderScanner
import com.nx.android.domain.GameStatus
import com.nx.android.ui.AppActions
import com.nx.android.ui.components.PickerMode
import com.nx.android.ui.components.PickerRequest
import com.nx.android.ui.components.CapsuleButton
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.Section
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.components.rememberPageScroll
import com.nx.android.ui.elapsedSecs
import com.nx.android.ui.theme.NxPalette
import java.io.File

/**
 * 构建页：发起与跟踪移植的工作台，按移植的顺序排卡——目标（游戏与它的参数）、进度与取消、日志、产物、历史。
 * 游戏库里点「▶ 构建」把游戏带进来（buildsTarget）；开始按钮在这里，前提的检查与说明也在这里。
 */
@Composable
fun BuildsScreen(lib: LibraryState, port: PortState, keys: String, envReady: Boolean,
                 actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val running = port.running
    val target = lib.buildsTarget ?: port.gameFile.takeIf { running }?.let { File(it) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberPageScroll()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (running) {
            ProgressCard(port, actions)
            // 运行中：目标卡让位，配置收起为可展开行
            target?.let { ConfigDisclosure(lib, it) }
            LogCard(port.log, expandedHeight = 320.dp)
        } else if (target != null) {
            TargetCard(lib, port, keys, envReady, target, actions, onRequest)
            OutcomeCard(lib, port, target, actions)
            LogCard(port.log, expandedHeight = 320.dp)
        } else {
            // 没有目标：构建页自己选一个游戏文件（NX2Apple FilesCard 的入口逻辑）
            Section("开始一次移植") {
                Text(
                    "选择一个游戏文件开始移植；同目录的更新与 DLC 会自动带上。构建完成后它会在游戏库里出现。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                CapsuleButton("选择游戏文件", onClick = {
                    onRequest(PickerRequest(PickerMode.FILE, GAME_FILE_EXTENSIONS,
                        File(lib.folder.ifBlank { "/storage" }), "选择游戏文件",
                        onPicked = { f ->
                            lib.registerExtra(f)
                            lib.buildsTarget = f
                        }))
                })
            }
            if (port.history.isNotEmpty()) History(lib, port, actions)
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---- 目标卡（未运行时）：游戏与参数，开始按钮 ------------------------------------------------------------------

@Composable
private fun TargetCard(lib: LibraryState, port: PortState, keys: String, envReady: Boolean,
                       f: File, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val status = lib.statusOf(f)
    var cfg by remember(f.absolutePath) { mutableStateOf(lib.configOf(f)) }
    val storageOk = actions.storageOk()
    val canStart = envReady && storageOk && !port.running && keys.isNotBlank() && status != GameStatus.PORTING
    val titleId = FolderScanner.titleIdIn(f.name)

    Section("目标") {
        // 文件行：更换 / 清除（NX2Apple FilesCard 的 choose/change/clear）
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("游戏文件", style = MaterialTheme.typography.bodyMedium)
                Hint(f.absolutePath, size = 11.sp)
            }
            QuietButton("更换", onClick = {
                onRequest(PickerRequest(PickerMode.FILE, GAME_FILE_EXTENSIONS,
                    File(lib.folder.ifBlank { "/storage" }), "选择游戏文件",
                    onPicked = { nf ->
                        lib.registerExtra(nf)
                        lib.buildsTarget = nf
                    }))
            })
            QuietButton("清除", onClick = { lib.buildsTarget = null })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(statusColor(status))
            Column(Modifier.weight(1f)) {
                Text(
                    FolderScanner.displayName(f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2,
                )
                Text(
                    listOfNotNull(titleId, statusLabel(status)).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 输出目录行：就地修改（与设置页同一设置）
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("输出目录", style = MaterialTheme.typography.bodyMedium)
                Hint(actions.out(), size = 11.sp)
            }
            QuietButton("修改", onClick = {
                onRequest(PickerRequest(PickerMode.DIR, emptySet(), File(actions.out()), "选择输出目录",
                    onPicked = { dir ->
                        actions.onSetOut(dir.path)
                        lib.rescan()
                    }))
            })
        }
        Hint("同目录的更新与 DLC 会自动带上")

        // 该游戏自己的参数：出发前最后确认，改动即时保存
        ConfigRow("触屏游戏（隐藏手柄）", cfg.touch) { cfg = cfg.copy(touch = it); lib.setConfig(f, cfg) }
        ConfigRow("60 帧", cfg.fps60) { cfg = cfg.copy(fps60 = it); lib.setConfig(f, cfg) }
        ConfigRow("实验性移植（还没适配的游戏）", cfg.experimental) { cfg = cfg.copy(experimental = it); lib.setConfig(f, cfg) }

        if (!canStart && status != GameStatus.DONE) {
            Text(
                when {
                    !envReady -> "先在设置里准备环境"
                    !storageOk -> "先授予存储访问权限"
                    keys.isBlank() -> "先在设置里填 prod.keys 的路径"
                    port.running -> "正在移植 ${File(port.gameFile).name}——一次只能移植一个"
                    else -> ""
                },
                color = NxPalette.Building, style = MaterialTheme.typography.bodySmall,
            )
        }
        if (status != GameStatus.PORTING) {
            CapsuleButton("▶ 开始移植", {
                actions.onStartPort(f, cfg)
            }, enabled = canStart, modifier = Modifier.fillMaxWidth())
        } else {
            Hint("正在移植中——上面的进度卡实时更新")
        }
    }
}

/** 运行中：目标卡让位，配置收起为一行，点开能看/改 */
@Composable
private fun ConfigDisclosure(lib: LibraryState, f: File) {
    var open by remember { mutableStateOf(false) }
    Section("配置：${FolderScanner.displayName(f)}") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
        ) {
            val cfg = lib.configOf(f)
            Text(
                listOf(
                    if (cfg.touch) "触屏" else null,
                    if (cfg.fps60) "60 帧" else null,
                    if (cfg.experimental) "实验性" else null,
                ).filterNotNull().ifEmpty { listOf("默认参数") }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            val cfg = lib.configOf(f)
            ConfigRow("触屏游戏（隐藏手柄）", cfg.touch) { lib.setConfig(f, cfg.copy(touch = it)) }
            ConfigRow("60 帧", cfg.fps60) { lib.setConfig(f, cfg.copy(fps60 = it)) }
            ConfigRow("实验性移植（还没适配的游戏）", cfg.experimental) { lib.setConfig(f, cfg.copy(experimental = it)) }
        }
    }
}

// ---- 进度卡（运行时） -------------------------------------------------------------------------------------------

@Composable
private fun ProgressCard(port: PortState, actions: AppActions) {
    val secs = elapsedSecs(port.startedAt)
    Section("移植") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PhaseChip(port.phase)
            Text(
                File(port.gameFile).name,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 2,
            )
            if (port.startedAt > 0) {
                Text("${secs / 60} 分 ${secs % 60} 秒",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (port.progress > 0f || port.stageName.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { port.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = NxPalette.Building,
            )
            if (port.stageName.isNotEmpty()) {
                Text(
                    "阶段 ${port.stageOf}/${port.stages}：${port.stageName}" +
                        (if (port.stageStep.isEmpty()) "" else " · ${port.stageStep}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (port.phase == PortState.Phase.RUNNING) {
            QuietButton("取消移植", { actions.onCancelPort() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---- 日志卡（可展开） -------------------------------------------------------------------------------------------

@Composable
private fun LogCard(log: List<String>, expandedHeight: androidx.compose.ui.unit.Dp) {
    var open by remember { mutableStateOf(false) }
    Section("日志") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("工具说的每一句都在这里", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            QuietButton(if (open) "收起" else "展开更多", { open = !open })
        }
        LogTail(log, if (open) expandedHeight else 160.dp)
    }
}

/** The log's last lines (mono, the failures red) */
@Composable
fun LogTail(log: List<String>, height: androidx.compose.ui.unit.Dp = 160.dp) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(log.size) { scroll.scrollTo(log.size * 1000) }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 60.dp, max = height)
                .verticalScroll(scroll).padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val lines = log.takeLast(30)
                if (lines.isEmpty()) {
                    Text("还没有输出", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    for (line in lines) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (line.startsWith("[RED]")) NxPalette.Danger else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

// ---- 产物卡（已完成）/ 结果 -------------------------------------------------------------------------------------

@Composable
private fun OutcomeCard(lib: LibraryState, port: PortState, f: File, actions: AppActions) {
    val status = lib.statusOf(f)
    when (status) {
        GameStatus.DONE -> {
            val ported = lib.portOf(f) ?: return
            Section("产物") {
                Hint("产物：${ported.pack.absolutePath}", size = 11.sp)
                CapsuleButton("▶ 运行", { actions.onRun(ported.pack, FolderScanner.displayName(f)) }, modifier = Modifier.fillMaxWidth())
            }
        }
        else -> {
            val last = port.history.firstOrNull { it.game == f.absolutePath }
            if (last != null && last.phase != PortState.Phase.DONE) {
                Section("结果") {
                    Text(
                        when (last.phase) {
                            PortState.Phase.FAILED -> "上次移植失败——原因在下面的日志里，改好参数可以重新开始"
                            PortState.Phase.CANCELLED -> "上次移植被取消——参数还在，可以重新开始"
                            PortState.Phase.INTERRUPTED -> "上次移植被系统中断——可以重新开始"
                            else -> return@Section
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = NxPalette.Building,
                    )
                }
            }
        }
    }
}

/** One color per outcome, the same the library's cards and the settings wear */
@Composable
fun PhaseChip(p: PortState.Phase) {
    val (label, color) = when (p) {
        PortState.Phase.RUNNING -> "移植中" to NxPalette.Building
        PortState.Phase.DONE -> "已完成" to NxPalette.Ready
        PortState.Phase.FAILED -> "失败" to NxPalette.Danger
        PortState.Phase.CANCELLED -> "已取消" to NxPalette.Unbuilt
        PortState.Phase.INTERRUPTED -> "被中断" to NxPalette.Danger
        PortState.Phase.IDLE -> "空闲" to NxPalette.Unbuilt
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.18f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusDot(color, 8.dp)
            Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

private fun statusColor(s: GameStatus) = when (s) {
    GameStatus.NOT_PORTED -> NxPalette.Unbuilt
    GameStatus.PORTING -> NxPalette.Building
    GameStatus.DONE -> NxPalette.Ready
}

@Composable
private fun statusLabel(s: GameStatus): String = when (s) {
    GameStatus.NOT_PORTED -> "未移植"
    GameStatus.PORTING -> "移植中"
    GameStatus.DONE -> "已完成"
}

@Composable
private fun ConfigRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The ports that ran before: the result, how long it took, and the done ones' run button */
@Composable
private fun History(lib: LibraryState, port: PortState, actions: AppActions) {
    Section("历史") {
        for (r in port.history) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(File(r.game).name, style = MaterialTheme.typography.bodyMedium,
                         maxLines = 1, fontWeight = FontWeight.Bold)
                    Hint("${r.seconds / 60} 分 ${r.seconds % 60} 秒 · " + when (r.phase) {
                        PortState.Phase.DONE -> "完成"
                        PortState.Phase.FAILED -> "失败"
                        PortState.Phase.CANCELLED -> "已取消"
                        PortState.Phase.INTERRUPTED -> "被中断"
                        else -> ""
                    })
                }
                // 再次构建（NX2Apple rebuild）：源文件还在才能再来
                if (File(r.game).isFile()) {
                    QuietButton("再次构建", onClick = { lib.buildsTarget = File(r.game) })
                }
                // 完成记录的「▶ 运行」：与库页相同地启动（数据包还在才能跑）
                if (r.phase == PortState.Phase.DONE && r.out.isNotEmpty() && FolderScanner.portFromPack(File(r.out)) != null) {
                    QuietButton("▶ 运行", {
                        FolderScanner.portFromPack(File(r.out))?.let { actions.onRun(it.pack, File(r.game).nameWithoutExtension) }
                    })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}
