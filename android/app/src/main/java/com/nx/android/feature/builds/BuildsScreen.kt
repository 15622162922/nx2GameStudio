package com.nx.android.feature.builds

import com.nx.android.domain.porting.PortPhase

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.PortState
import com.nx.android.data.AppLog
import com.nx.android.domain.ALIAS_AUTO
import com.nx.android.domain.GameIdentity
import com.nx.android.domain.GAME_FILE_EXTENSIONS
import com.nx.android.domain.GameStatus
import com.nx.android.domain.LibraryLogic
import com.nx.android.app.AppActions
import com.nx.android.core.ui.LocalAppLocale
import com.nx.android.core.ui.components.CapsuleButton
import com.nx.android.core.ui.components.Hint
import com.nx.android.feature.filepicker.PickerMode
import com.nx.android.feature.filepicker.PickerRequest
import com.nx.android.core.ui.components.PickRow
import com.nx.android.core.ui.components.QuietButton
import com.nx.android.core.ui.components.Section
import com.nx.android.core.ui.components.StatusDot
import com.nx.android.core.ui.components.rememberPageScroll
import com.nx.android.core.ui.elapsedSecs
import com.nx.android.core.ui.theme.NxPalette
import java.io.File

/** A filename that includes a title ID must agree with the card being rebuilt. Renamed files are allowed. */
private fun sourceMatchesTarget(target: File, candidate: File): Boolean {
    val expected = GameIdentity.titleIdIn(target.name) ?: return true
    return LibraryLogic.sourceMatchesTitle(candidate.name, expected)
}

/**
 * 构建页：发起与跟踪移植的工作台，按移植的顺序排卡——目标（游戏与它的参数）、进度与取消、产物、日志、历史。
 * 游戏库里点「▶ 构建」把游戏带进来（buildsTarget）；开始按钮在这里，前提的检查与说明也在这里。
 */
@Composable
fun BuildsRoute(viewModel: BuildsViewModel, keys: String, envReady: Boolean,
                actions: AppActions, onRequest: (PickerRequest) -> Unit, onLog: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.running) { if (uiState.running) viewModel.buildStarted() }
    BuildsScreen(viewModel.library, viewModel, uiState, keys, envReady, actions, onRequest, onLog)
}

@Composable
private fun BuildsScreen(lib: LibraryState, viewModel: BuildsViewModel, uiState: BuildsUiState,
                         keys: String, envReady: Boolean, actions: AppActions,
                         onRequest: (PickerRequest) -> Unit, onLog: (String) -> Unit) {
    val locale = LocalAppLocale.current
    val running = uiState.running
    val target = uiState.target?.let(::File) ?: uiState.gameFile.takeIf { running }?.let { File(it) }
    val selected = uiState.history.firstOrNull { it.id == viewModel.selectedBuildId }
    val current = uiState.history.firstOrNull { it.id == viewModel.port.buildId }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberPageScroll()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            selected != null -> {
                TaskDetail(lib, selected, viewModel, actions, onLog, active = selected.id == viewModel.port.buildId && running)
                QuietButton(stringResource(R.string.common_back), { viewModel.closeBuild() })
            }
            target != null && viewModel.preparing && !running ->
                TargetCard(lib, uiState, viewModel, keys, envReady, target, actions, onRequest)
            current != null -> TaskDetail(lib, current, viewModel, actions, onLog, active = running)
            running -> ProgressCard(uiState, actions)
            else -> {
                Section(stringResource(R.string.builds_start_title)) {
                    Text(stringResource(R.string.builds_start_hint), style = MaterialTheme.typography.bodyMedium)
                    CapsuleButton(stringResource(R.string.builds_choose_game_file), onClick = {
                        onRequest(PickerRequest(PickerMode.FILE, GAME_FILE_EXTENSIONS, File("/storage"),
                            locale.str(R.string.builds_choose_game_file), onPicked = { viewModel.setTarget(it) }))
                    })
                }
                if (uiState.history.isNotEmpty()) History(uiState, viewModel)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TaskDetail(lib: LibraryState, record: PortState.Record, viewModel: BuildsViewModel,
                       actions: AppActions, onLog: (String) -> Unit, active: Boolean) {
    val lines = viewModel.logTail(record.id)
    Section(stringResource(R.string.builds_result)) {
        Text(File(record.game).name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.builds_history_seconds, (record.seconds / 60).toString(), (record.seconds % 60).toString()))
        PhaseChip(if (active) PortPhase.RUNNING else record.phase)
        if (active && viewModel.port.stageName.isNotEmpty()) {
            Text(viewModel.port.stageName, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { viewModel.port.progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
            QuietButton(stringResource(R.string.builds_cancel_port), actions.onCancelPort, modifier = Modifier.fillMaxWidth())
        }
        record.config?.let { config ->
            val options = listOfNotNull(
                stringResource(R.string.builds_touch_game).takeIf { config.touch },
                stringResource(R.string.builds_60fps).takeIf { config.fps60 },
                stringResource(R.string.builds_experimental).takeIf { config.experimental },
                if (config.aliasHeadroom >= 0) stringResource(R.string.builds_alias_short, config.aliasHeadroom) else null,
            ).ifEmpty { listOf(stringResource(R.string.builds_default_options)) }
            Hint(stringResource(R.string.builds_used_options, options.joinToString(" · ")))
        }
        if (record.legacy) Text(stringResource(R.string.builds_log_legacy), style = MaterialTheme.typography.bodySmall)
        else if (!record.logAvailable) Text(stringResource(R.string.builds_log_unavailable), style = MaterialTheme.typography.bodySmall,
            color = NxPalette.Danger)
        LogTail(lines)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onLog(record.id) }) { Text(stringResource(R.string.builds_log_view)) }
            if (record.logAvailable && lines.isNotEmpty()) TextButton(onClick = { actions.onShareBuildLog(record.id) }) {
                Text(stringResource(R.string.builds_log_share))
            }
        }
        if (!active && File(record.game).isFile()) {
            QuietButton(stringResource(R.string.builds_build_again), {
                viewModel.setTarget(File(record.game), record.config, savedConfigMissing = record.config == null)
            })
        }
        if (!active && !File(record.game).isFile()) QuietButton(stringResource(R.string.builds_build_again), {
            viewModel.setTarget(File(record.game), record.config, savedConfigMissing = record.config == null)
        })
        if (record.phase == PortPhase.DONE && record.out.isNotEmpty()) {
            val currentPack = lib.portFromPack(File(record.out))
            if (currentPack != null) QuietButton(stringResource(R.string.builds_run_action), {
                actions.onRun(currentPack.pack, GameIdentity.labelFor(File(record.game), currentPack))
            })
            else Text(stringResource(R.string.lib_artifact_missing_state), style = MaterialTheme.typography.bodySmall,
                color = NxPalette.Danger)
        }
        if (record.phase != PortPhase.DONE && lib.portOf(File(record.game)) != null)
            Hint(stringResource(R.string.builds_old_pack_still_usable))
    }
}

// ---- 目标卡（未运行时）：游戏与参数，开始按钮 ------------------------------------------------------------------

@Composable
private fun TargetCard(lib: LibraryState, port: BuildsUiState, viewModel: BuildsViewModel, keys: String, envReady: Boolean,
                       f: File, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val locale = LocalAppLocale.current
    val status = lib.statusOf(f)
    var cfg by remember(f.absolutePath, viewModel.preparedConfig) {
        mutableStateOf(viewModel.preparedConfig ?: lib.configOf(f))
    }
    var configOpen by remember(f.absolutePath) { mutableStateOf(false) }
    val storageOk = actions.storageOk()
    val canStart = f.isFile && f.canRead() && envReady && storageOk && !port.running && keys.isNotBlank() && status != GameStatus.PORTING
    val titleId = GameIdentity.titleIdIn(f.name)

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
                        if (sourceMatchesTarget(f, nf)) viewModel.setTarget(nf)
                        else actions.onToast(locale.str(R.string.lib_source_mismatch))
                    }))
            })
            QuietButton(stringResource(R.string.builds_clear), onClick = { viewModel.setTarget(null) })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(statusColor(status))
            Column(Modifier.weight(1f)) {
                Text(
                    GameIdentity.labelFor(f, lib.portOf(f)),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2,
                )
                Text(
                    listOfNotNull(titleId, statusLabel(status)).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Hint(stringResource(R.string.builds_updates_taken))
        if (viewModel.preparedConfigWasMissing) Hint(stringResource(R.string.builds_config_missing_saved))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { configOpen = !configOpen },
        ) {
            Text(stringResource(R.string.builds_config_of, GameIdentity.labelFor(f, lib.portOf(f))),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(if (configOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (configOpen) {
            ConfigRow(stringResource(R.string.builds_touch_game), cfg.touch) { cfg = cfg.copy(touch = it); lib.setConfig(f, cfg) }
            ConfigRow(stringResource(R.string.builds_60fps), cfg.fps60) { cfg = cfg.copy(fps60 = it); lib.setConfig(f, cfg) }
            ConfigRow(stringResource(R.string.builds_experimental), cfg.experimental) { cfg = cfg.copy(experimental = it); lib.setConfig(f, cfg) }
            AliasRow(cfg.aliasHeadroom) { cfg = cfg.copy(aliasHeadroom = it); lib.setConfig(f, cfg) }
            Hint(stringResource(R.string.builds_alias_hint), size = 11.sp)
        }

        if (!canStart) {
            Text(
                when {
                    !envReady -> stringResource(R.string.builds_need_env)
                    !storageOk -> stringResource(R.string.builds_need_storage)
                    keys.isBlank() -> stringResource(R.string.builds_need_keys)
                    !f.isFile -> stringResource(R.string.builds_source_missing)
                    !f.canRead() -> stringResource(R.string.builds_needs_storage)
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
    Section(stringResource(R.string.builds_config_of, GameIdentity.labelFor(f, lib.portOf(f)))) {
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
private fun ProgressCard(port: BuildsUiState, actions: AppActions) {
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
        if (port.phase == PortPhase.RUNNING) {
            QuietButton(stringResource(R.string.builds_cancel_port), { actions.onCancelPort() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---- 日志模块（尾部 + 展开 + 查看/分享/清空） -----------------------------------------------

/**
 * 日志模块：工具说过的话的尾部，加上把它带走的三个动作——查看全部、分享日志、清空日志。
 *
 * 屏幕上给的是内存里的那一份（移植进行中它最新）；App 重启之后内存里空了，就退回文件里那一份
 * （上一轮留下的）。「查看全部」与「分享日志」给的始终是文件里的内容，不只是屏幕上这些行。
 */
@Composable
private fun LogCard(port: BuildsUiState, actions: AppActions, onView: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    // 内存里没有输出时（App 重启之后），读文件里那一份
    var fileTail by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(port.log.isEmpty()) {
        if (port.log.isEmpty()) fileTail = AppLog.text(400).lines()
    }
    val lines = if (port.log.isNotEmpty()) port.log else fileTail
    Section(stringResource(R.string.common_log)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.builds_log_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            QuietButton(if (open) stringResource(R.string.builds_collapse) else stringResource(R.string.builds_expand), { open = !open })
        }
        LogTail(lines, if (open) 320.dp else 160.dp, if (open) 200 else 30)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onView) { Text(stringResource(R.string.builds_log_view)) }
            TextButton(onClick = { actions.onShareLog() }) { Text(stringResource(R.string.builds_log_share)) }
            // 清空：文件与屏幕上这份一起清（动作里清的是内存那份，这里清的是文件那份的读出）
            TextButton(onClick = { actions.onClearLog(); fileTail = listOf() }) { Text(stringResource(R.string.builds_log_clear)) }
        }
    }
}

/** The log's last lines (mono, the failures red) */
@Composable
fun LogTail(log: List<String>, height: Dp = 160.dp, lines: Int = 30) {
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
                val shown = log.takeLast(lines)
                if (shown.isEmpty()) {
                    Text(stringResource(R.string.builds_no_output), style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    for (line in shown) {
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
private fun OutcomeCard(lib: LibraryState, port: BuildsUiState, f: File, actions: AppActions) {
    val locale = LocalAppLocale.current
    val status = lib.statusOf(f)
    when (status) {
        GameStatus.DONE -> {
            val ported = lib.portOf(f) ?: return
            Section(locale.str(R.string.builds_artifact)) {
                Hint(locale.str(R.string.builds_artifact_path, ported.pack.absolutePath), size = 11.sp)
                CapsuleButton(locale.str(R.string.builds_run_action), { actions.onRun(ported.pack, GameIdentity.labelFor(f, ported)) }, modifier = Modifier.fillMaxWidth())
            }
        }
        else -> {
            val last = port.history.firstOrNull { it.game == f.absolutePath }
            if (last != null && last.phase != PortPhase.DONE) {
                Section(locale.str(R.string.builds_result)) {
                    Text(
                        when (last.phase) {
                            PortPhase.FAILED -> locale.str(R.string.builds_prev_failed)
                            PortPhase.CANCELLED -> locale.str(R.string.builds_prev_cancelled)
                            PortPhase.INTERRUPTED -> locale.str(R.string.builds_prev_interrupted)
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
fun PhaseChip(p: PortPhase) {
    val (label, color) = when (p) {
        PortPhase.RUNNING -> stringResource(R.string.common_porting) to NxPalette.Building
        PortPhase.DONE -> stringResource(R.string.common_done) to NxPalette.Ready
        PortPhase.FAILED -> stringResource(R.string.common_failed) to NxPalette.Danger
        PortPhase.CANCELLED -> stringResource(R.string.common_cancelled) to NxPalette.Unbuilt
        PortPhase.INTERRUPTED -> stringResource(R.string.common_interrupted) to NxPalette.Danger
        PortPhase.IDLE -> stringResource(R.string.common_idle) to NxPalette.Unbuilt
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
    GameStatus.NEEDS_BUILD -> NxPalette.Unbuilt
}

@Composable
private fun statusLabel(s: GameStatus): String = when (s) {
    GameStatus.NOT_PORTED -> stringResource(R.string.common_not_ported)
    GameStatus.PORTING -> stringResource(R.string.common_porting)
    GameStatus.DONE -> stringResource(R.string.common_done)
    GameStatus.NEEDS_BUILD -> stringResource(R.string.lib_needs_build_status)
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

/** Recent attempts: one row opens the result and log belonging to that attempt. */
@Composable
private fun History(port: BuildsUiState, viewModel: BuildsViewModel) {
    Section(stringResource(R.string.builds_history)) {
        for (r in port.history) {
            Column(Modifier.fillMaxWidth().clickable { viewModel.openBuild(r.id) }.padding(vertical = 8.dp)) {
                Text(File(r.game).name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, fontWeight = FontWeight.Bold)
                Hint(stringResource(R.string.builds_history_seconds, r.seconds / 60, r.seconds % 60) + " · " + when (r.phase) {
                    PortPhase.DONE -> stringResource(R.string.common_complete)
                    PortPhase.FAILED -> stringResource(R.string.common_failed)
                    PortPhase.CANCELLED -> stringResource(R.string.common_cancelled)
                    PortPhase.INTERRUPTED -> stringResource(R.string.common_interrupted)
                    else -> ""
                })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Hint(stringResource(R.string.builds_history_retention))
    }
}
