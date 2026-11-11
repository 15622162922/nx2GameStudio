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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.PortState
import com.nx.android.data.AppLocale
import com.nx.android.domain.ALIAS_AUTO
import com.nx.android.domain.FolderScanner
import com.nx.android.domain.GAME_FILE_EXTENSIONS
import com.nx.android.domain.GameStatus
import com.nx.android.ui.AppActions
import com.nx.android.ui.LocalAppLocale
import com.nx.android.ui.components.CapsuleButton
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.PickerMode
import com.nx.android.ui.components.PickerRequest
import com.nx.android.ui.components.PickRow
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
    val locale = LocalAppLocale.current
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
            Section(stringResource(R.string.builds_start_title)) {
                Text(
                    stringResource(R.string.builds_start_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                CapsuleButton(stringResource(R.string.builds_choose_game_file), onClick = {
                    onRequest(PickerRequest(PickerMode.FILE, GAME_FILE_EXTENSIONS,
                        File("/storage"), locale.str(R.string.builds_choose_game_file),
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
    val locale = LocalAppLocale.current
    val status = lib.statusOf(f)
    var cfg by remember(f.absolutePath) { mutableStateOf(lib.configOf(f)) }
    val storageOk = actions.storageOk()
    val canStart = envReady && storageOk && !port.running && keys.isNotBlank() && status != GameStatus.PORTING
    val titleId = FolderScanner.titleIdIn(f.name)

    Section(stringResource(R.string.builds_target)) {
        // 文件行：更换 / 清除（NX2Apple FilesCard 的 choose/change/clear）
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.builds_game_file), style = MaterialTheme.typography.bodyMedium)
                Hint(f.absolutePath, size = 11.sp)
            }
            QuietButton(stringResource(R.string.builds_change), onClick = {
                onRequest(PickerRequest(PickerMode.FILE, GAME_FILE_EXTENSIONS,
                    File("/storage"), locale.str(R.string.builds_choose_game_file),
                    onPicked = { nf ->
                        lib.registerExtra(nf)
                        lib.buildsTarget = nf
                    }))
            })
            QuietButton(stringResource(R.string.builds_clear), onClick = { lib.buildsTarget = null })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(statusColor(status))
            Column(Modifier.weight(1f)) {
                Text(
                    FolderScanner.labelFor(f, lib.portOf(f)),
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
                Text(stringResource(R.string.common_output_dir), style = MaterialTheme.typography.bodyMedium)
                Hint(actions.out(), size = 11.sp)
            }
            QuietButton(stringResource(R.string.common_modify), onClick = {
                onRequest(PickerRequest(PickerMode.DIR, emptySet(), File(actions.out()), locale.str(R.string.builds_choose_output_dir),
                    onPicked = { dir ->
                        actions.onSetOut(dir.path)
                        lib.rescan()
                    }))
            })
        }
        Hint(stringResource(R.string.builds_updates_taken))

        // 该游戏自己的参数：出发前最后确认，改动即时保存
        ConfigRow(stringResource(R.string.builds_touch_game), cfg.touch) { cfg = cfg.copy(touch = it); lib.setConfig(f, cfg) }
        ConfigRow(stringResource(R.string.builds_60fps), cfg.fps60) { cfg = cfg.copy(fps60 = it); lib.setConfig(f, cfg) }
        ConfigRow(stringResource(R.string.builds_experimental), cfg.experimental) { cfg = cfg.copy(experimental = it); lib.setConfig(f, cfg) }
        AliasRow(cfg.aliasHeadroom) { cfg = cfg.copy(aliasHeadroom = it); lib.setConfig(f, cfg) }
        Hint(stringResource(R.string.builds_alias_hint), size = 11.sp)

        if (!canStart && status != GameStatus.DONE) {
            Text(
                when {
                    !envReady -> stringResource(R.string.builds_need_env)
                    !storageOk -> stringResource(R.string.builds_need_storage)
                    keys.isBlank() -> stringResource(R.string.builds_need_keys)
                    port.running -> stringResource(R.string.builds_one_at_a_time, File(port.gameFile).name)
                    else -> ""
                },
                color = NxPalette.Building, style = MaterialTheme.typography.bodySmall,
            )
        }
        if (status != GameStatus.PORTING) {
            CapsuleButton(stringResource(R.string.builds_start_action), {
                actions.onStartPort(f, cfg)
            }, enabled = canStart, modifier = Modifier.fillMaxWidth())
        } else {
            Hint(stringResource(R.string.builds_porting))
        }
    }
}

/** 运行中：目标卡让位，配置收起为一行，点开能看/改 */
@Composable
private fun ConfigDisclosure(lib: LibraryState, f: File) {
    var open by remember { mutableStateOf(false) }
    Section(stringResource(R.string.builds_config_of, FolderScanner.labelFor(f, lib.portOf(f)))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
        ) {
            val cfg = lib.configOf(f)
            Text(
                listOf(
                    if (cfg.touch) stringResource(R.string.builds_touch) else null,
                    if (cfg.fps60) stringResource(R.string.builds_60fps) else null,
                    if (cfg.experimental) stringResource(R.string.builds_experimental_short) else null,
                    if (cfg.aliasHeadroom >= 0) stringResource(R.string.builds_alias_short, cfg.aliasHeadroom) else null,
                ).filterNotNull().ifEmpty { listOf(stringResource(R.string.builds_default_options)) }.joinToString(" · "),
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
            ConfigRow(stringResource(R.string.builds_touch_game), cfg.touch) { lib.setConfig(f, cfg.copy(touch = it)) }
            ConfigRow(stringResource(R.string.builds_60fps), cfg.fps60) { lib.setConfig(f, cfg.copy(fps60 = it)) }
            ConfigRow(stringResource(R.string.builds_experimental), cfg.experimental) { lib.setConfig(f, cfg.copy(experimental = it)) }
            AliasRow(cfg.aliasHeadroom) { lib.setConfig(f, cfg.copy(aliasHeadroom = it)) }
        }
    }
}

// ---- 进度卡（运行时） -------------------------------------------------------------------------------------------

@Composable
private fun ProgressCard(port: PortState, actions: AppActions) {
    val secs = elapsedSecs(port.startedAt)
    Section(stringResource(R.string.builds_port_action)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PhaseChip(port.phase)
            Text(
                File(port.gameFile).name,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 2,
            )
            if (port.startedAt > 0) {
                Text(stringResource(R.string.builds_minutes_seconds, secs / 60, secs % 60),
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
                    stringResource(R.string.port_stage_of, port.stageOf, port.stages, port.stageName) +
                        (if (port.stageStep.isEmpty()) "" else " · ${port.stageStep}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (port.phase == PortState.Phase.RUNNING) {
            QuietButton(stringResource(R.string.builds_cancel_port), { actions.onCancelPort() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---- 日志卡（可展开） -------------------------------------------------------------------------------------------

@Composable
private fun LogCard(log: List<String>, expandedHeight: androidx.compose.ui.unit.Dp) {
    var open by remember { mutableStateOf(false) }
    Section(stringResource(R.string.common_log)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.builds_log_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            QuietButton(if (open) stringResource(R.string.builds_collapse) else stringResource(R.string.builds_expand), { open = !open })
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
                    Text(stringResource(R.string.builds_no_output), style = MaterialTheme.typography.bodySmall,
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
    val locale = LocalAppLocale.current
    val status = lib.statusOf(f)
    when (status) {
        GameStatus.DONE -> {
            val ported = lib.portOf(f) ?: return
            Section(locale.str(R.string.builds_artifact)) {
                Hint(locale.str(R.string.builds_artifact_path, ported.pack.absolutePath), size = 11.sp)
                CapsuleButton(locale.str(R.string.builds_run_action), { actions.onRun(ported.pack, FolderScanner.labelFor(f, ported)) }, modifier = Modifier.fillMaxWidth())
            }
        }
        else -> {
            val last = port.history.firstOrNull { it.game == f.absolutePath }
            if (last != null && last.phase != PortState.Phase.DONE) {
                Section(locale.str(R.string.builds_result)) {
                    Text(
                        when (last.phase) {
                            PortState.Phase.FAILED -> locale.str(R.string.builds_prev_failed)
                            PortState.Phase.CANCELLED -> locale.str(R.string.builds_prev_cancelled)
                            PortState.Phase.INTERRUPTED -> locale.str(R.string.builds_prev_interrupted)
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
        PortState.Phase.RUNNING -> stringResource(R.string.common_porting) to NxPalette.Building
        PortState.Phase.DONE -> stringResource(R.string.common_done) to NxPalette.Ready
        PortState.Phase.FAILED -> stringResource(R.string.common_failed) to NxPalette.Danger
        PortState.Phase.CANCELLED -> stringResource(R.string.common_cancelled) to NxPalette.Unbuilt
        PortState.Phase.INTERRUPTED -> stringResource(R.string.common_interrupted) to NxPalette.Danger
        PortState.Phase.IDLE -> stringResource(R.string.common_idle) to NxPalette.Unbuilt
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
    GameStatus.NOT_PORTED -> stringResource(R.string.common_not_ported)
    GameStatus.PORTING -> stringResource(R.string.common_porting)
    GameStatus.DONE -> stringResource(R.string.common_done)
}

@Composable
private fun ConfigRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 额外地址空间（别名区）：Auto 或玩家手动指定的 GB 数。Auto 不带数值，移植器自己决定
 * （档案调通过的版本不加，没调通过的加 8 GB）；它编译进宿主的内存布局，改了要重新构建。
 */
private val ALIAS_CHOICES = listOf(ALIAS_AUTO, 0, 8, 16, 24, 32, 48)

@Composable
private fun AliasRow(headroom: Int, onChange: (Int) -> Unit) {
    val auto = stringResource(R.string.builds_alias_auto)
    PickRow(
        label = stringResource(R.string.builds_alias_headroom),
        options = ALIAS_CHOICES.map { it.toString() to (if (it < 0) auto else "$it GB") },
        current = headroom.toString(),
        onSelect = { onChange(it.toIntOrNull() ?: ALIAS_AUTO) },
        fallback = auto,
    )
}

/** The ports that ran before: the result, how long it took, and the done ones' run button */
@Composable
private fun History(lib: LibraryState, port: PortState, actions: AppActions) {
    Section(stringResource(R.string.builds_history)) {
        for (r in port.history) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(File(r.game).name, style = MaterialTheme.typography.bodyMedium,
                         maxLines = 1, fontWeight = FontWeight.Bold)
                    Hint(stringResource(R.string.builds_history_seconds, r.seconds / 60, r.seconds % 60) + " · " + when (r.phase) {
                        PortState.Phase.DONE -> stringResource(R.string.common_complete)
                        PortState.Phase.FAILED -> stringResource(R.string.common_failed)
                        PortState.Phase.CANCELLED -> stringResource(R.string.common_cancelled)
                        PortState.Phase.INTERRUPTED -> stringResource(R.string.common_interrupted)
                        else -> ""
                    })
                }
                // 再次构建（NX2Apple rebuild）：源文件还在才能再来
                if (File(r.game).isFile()) {
                    QuietButton(stringResource(R.string.builds_build_again), onClick = { lib.buildsTarget = File(r.game) })
                }
                // 完成记录的「▶ 运行」：与库页相同地启动（数据包还在才能跑）
                if (r.phase == PortState.Phase.DONE && r.out.isNotEmpty() && FolderScanner.portFromPack(File(r.out)) != null) {
                    QuietButton(stringResource(R.string.builds_run_action), {
                        FolderScanner.portFromPack(File(r.out))?.let { actions.onRun(it.pack, FolderScanner.labelFor(File(r.game), it)) }
                    })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}
