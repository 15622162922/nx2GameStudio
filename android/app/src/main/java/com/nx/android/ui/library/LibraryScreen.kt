package com.nx.android.ui.library

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.PlayerState
import com.nx.android.appstate.PortState
import com.nx.android.data.AppLocale
import com.nx.android.data.Drivers
import com.nx.android.data.GameIcon
import com.nx.android.data.GameLog
import com.nx.android.data.Player
import com.nx.android.data.SaveBackup
import com.nx.android.data.SaveShare
import com.nx.android.domain.FolderScanner
import com.nx.android.domain.GameStatus
import com.nx.android.domain.PackEnv
import com.nx.android.domain.Ported
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
import com.nx.android.ui.failureText
import com.nx.android.ui.theme.NxPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 游戏库：主机式行视图——横向瓦片（88/128dp），选中的放大，选中游戏铺满背景并在左下给出详情面板（大标题、
 * 状态、▶ 构建、配置与安装的圆钮）。移植配置从右侧全高滑入。
 */
@Composable
fun LibraryScreen(lib: LibraryState, port: PortState, player: PlayerState, actions: AppActions,
                  onGoBuilds: () -> Unit) {

    // a port ends: the cards' statuses are re-read
    LaunchedEffect(port.phase) { lib.rescan() }

    val visible = lib.visible()
    LaunchedEffect(visible.size) { lib.selectHero(lib.heroIndex, visible.size) }

    RowView(lib, port, player, visible, actions, onGoBuilds)

    // 配置面板不在这里画：它要盖住顶栏，所以由页面的根（MainScreen）在最上层画（见 ui/App.kt）
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

/**
 * 实验性移植的标记：档案还没在 Android 上调通（清单里的 status 是 experimental），玩家该知道
 * 这个游戏随时可能卡在某条没走过的路上 —— 而不是以为它坏了。
 */
@Composable
private fun experimentalTag(ported: Ported?): String? =
    if (ported?.status == "experimental") stringResource(R.string.lib_experimental) else null

/** The cover's two colors, picked by the name so one game always wears the same coat */
private fun coverColors(name: String): List<Color> {
    val h = name.hashCode()
    fun c(off: Int) = Color(
        red = 0.18f + ((h shr off) and 0x3) * 0.05f,
        green = 0.18f + ((h shr (off + 2)) and 0x3) * 0.05f,
        blue = 0.24f + ((h shr (off + 4)) and 0x3) * 0.06f,
        alpha = 1f,
    )
    return listOf(c(0), c(8))
}

/** 降采样读一张图，主线程之外（capyps5 rememberImage 的手法） */
@Composable
private fun rememberGameIcon(path: String?, maxSize: Int): androidx.compose.runtime.State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching { GameIcon.decode(path, maxSize)?.asImageBitmap() }.getOrNull()
        }
    }

// ---- 行视图（hero） -------------------------------------------------------------------------------------------

@Composable
private fun RowView(lib: LibraryState, port: PortState, player: PlayerState,
                    visible: List<File>, actions: AppActions, onGoBuilds: () -> Unit) {
    val sel = visible.getOrNull(lib.heroIndex)

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (visible.isEmpty()) {
            // 空态：只有一句提示，没有按钮——加游戏的路只有构建页那一条
            Spacer(Modifier.weight(1f))
            Section(stringResource(R.string.lib_empty_title)) {
                Text(stringResource(R.string.lib_empty_hint), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.weight(1f))
            return@Column
        }
        Spacer(Modifier.height(10.dp))
        HeroRow(lib, port, visible) { i ->
            if (i == lib.heroIndex) lib.selected = visible.getOrNull(i) else lib.selectHero(i, visible.size)
        }
        Spacer(Modifier.weight(1f))
        sel?.let { HeroDetails(lib, port, player, it, actions, onGoBuilds) }
        Spacer(Modifier.height(18.dp))
    }
}

/** 选中游戏的全屏呈现：已完成的游戏用产物 APK 里的图标实图，其余用名字的渐变与首字母水印 */
@Composable
internal fun HeroBackground(lib: LibraryState, port: PortState, sel: File?) {
    if (sel == null) return
    val icon by rememberGameIcon(lib.portOf(sel)?.icon?.absolutePath, 1280)

    Box(Modifier.fillMaxSize()) {
        val bitmap = icon
        if (bitmap != null) {
            // 磨砂玻璃：小图标拉满全屏（放大裁掉模糊暗角）+ 高斯模糊，压暗层保持文字可读
            androidx.compose.foundation.Image(
                bitmap, null,
                Modifier.fillMaxSize().scale(1.15f).blur(32.dp, BlurredEdgeTreatment.Unbounded),
                contentScale = ContentScale.Crop, alpha = 0.6f,
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(coverColors(sel.name).map { it.copy(alpha = 0.45f) })
                )
            )
            Text(
                FolderScanner.displayName(sel).take(1).ifEmpty { "?" },
                fontSize = 180.sp, fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // 压暗：文字要保持可读
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xEE0E0E0F), 0.65f to Color(0x220E0E0F))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to NxPalette.Background)))
    }
}

/** 横向瓦片行：选中的放大（88 → 128），状态点跟着瓦片走 */
@Composable
private fun HeroRow(lib: LibraryState, port: PortState, visible: List<File>, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState()
    // 选中项移到第三格之后行才滚动，选中的前后都留在视野里
    LaunchedEffect(lib.heroIndex) {
        if (visible.isNotEmpty()) state.animateScrollToItem((lib.heroIndex - 2).coerceIn(0, visible.size - 1))
    }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 80.dp),
        modifier = Modifier.fillMaxWidth().height(132.dp),
    ) {
        itemsIndexed(visible, key = { _, f -> f.absolutePath }) { index, f ->
            val size by androidx.compose.animation.core.animateDpAsState(
                if (index == lib.heroIndex) 128.dp else 88.dp, label = "tileSize"
            )
            HeroTile(lib, port, f, index == lib.heroIndex, Modifier.size(size)) { onSelect(index) }
        }
    }
}

@Composable
private fun HeroTile(lib: LibraryState, port: PortState, f: File, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val status = lib.statusOf(f)
    val shape = RoundedCornerShape(14.dp)
    val icon by rememberGameIcon(lib.portOf(f)?.icon?.absolutePath, 256)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(coverColors(f.name)))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else NxPalette.Unbuilt.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = icon
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap, FolderScanner.displayName(f), Modifier.padding(2.dp).fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                FolderScanner.displayName(f).take(1).ifEmpty { "?" },
                fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f),
            )
        }
        Surface(shape = RoundedCornerShape(50), color = NxPalette.Background.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusDot(statusColor(status), 6.dp)
                if (status == GameStatus.PORTING) {
                    Text("${(port.progress * 100).toInt()}%", fontSize = 10.sp, color = statusColor(status))
                }
            }
        }
    }
}

/** 左下详情：大标题、Title ID · 状态、主按钮（未移植 ▶ 构建 / 已完成 ▶ 运行）与圆形次级钮 */
@Composable
private fun HeroDetails(lib: LibraryState, port: PortState, player: PlayerState,
                        f: File, actions: AppActions, onGoBuilds: () -> Unit) {
    val status = lib.statusOf(f)
    val ported = lib.portOf(f)
    // Title ID：文件名里的优先；文件名没有（多半是下载器改过名），就用数据包清单里的
    val titleId = FolderScanner.titleIdIn(f.name) ?: ported?.titleId?.takeIf { it.isNotEmpty() }?.uppercase()
    // 单游戏：别的游戏在玩时，这个游戏的「▶ 运行」置灰并说明
    val locked = status == GameStatus.DONE && player.running && player.titleId != (ported?.titleId ?: "")

    Column(Modifier.widthIn(max = 640.dp)) {
        Text(
            FolderScanner.displayName(f),
            style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(titleId, statusLabel(status) +
                if (status == GameStatus.PORTING) " ${(port.progress * 100).toInt()}%" else "",
                experimentalTag(ported)).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // 主按钮：未移植 ▶ 构建（携带游戏与配置跳到构建页，NX2Apple 的 configureBuild），移植中变「查看构建」，
            // 已完成 ▶ 运行（App 内直接进游戏）
            val building = status == GameStatus.PORTING
            val running = status == GameStatus.DONE
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (locked) NxPalette.Unbuilt else NxPalette.Cream)
                    .clickable(enabled = !locked) {
                        if (running) actions.onRun(ported!!.pack, FolderScanner.displayName(f))
                        else {
                            lib.buildsTarget = f
                            onGoBuilds()
                        }
                    }
                    .padding(horizontal = 30.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp),
                     tint = if (locked) NxPalette.Background else Color.Black)
                Spacer(Modifier.width(6.dp))
                Text(
                    when { building -> stringResource(R.string.lib_view_builds); running -> stringResource(R.string.set_run_onboarding); else -> stringResource(R.string.common_build) },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (locked) NxPalette.Background else Color.Black,
                )
            }
            // 配置圆钮
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
                    .clickable { lib.selected = f }.padding(11.dp),
            ) {
                Icon(Icons.Filled.Tune, if (running) stringResource(R.string.lib_run_config) else stringResource(R.string.lib_port_config), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        if (locked) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.lib_close_current_game, player.name), style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status == GameStatus.DONE && player.running && player.titleId == (ported?.titleId ?: "")) {
            // 这个游戏正在玩：回到游戏与退出直达
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton(stringResource(R.string.drawer_back_to_game), onClick = { actions.onResumeGame() })
                QuietButton(stringResource(R.string.drawer_exit), onClick = { actions.onStopGame() })
            }
        }
    }
}

// ---- 移植配置面板（右侧全高滑入） --------------------------------------------------------------------------------

/**
 * 移植配置面板：文件路径、状态与该游戏自己的选项，开始移植与安装也从这里走。
 * 由页面的根在最上层画（MainScreen），它的遮罩要盖住顶栏。
 */
@Composable
internal fun ConfigPanel(lib: LibraryState, port: PortState, player: PlayerState,
                        f: File, actions: AppActions, onGoBuilds: () -> Unit,
                        onRequest: (PickerRequest) -> Unit, onClose: () -> Unit) {
    val locale = LocalAppLocale.current
    BackHandler { onClose() }
    val status = lib.statusOf(f)
    // Title ID：文件名里的优先，没有就用数据包清单里的
    val titleId = FolderScanner.titleIdIn(f.name)
        ?: lib.portOf(f)?.titleId?.takeIf { it.isNotEmpty() }?.uppercase()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // 完成的移植：数据包里带着宿主写的运行日志（log.txt）
    val ported = lib.portOf(f)

    /** 游戏运行日志：宿主把它写在了数据包里，拷进下载目录再发出去（整份文件，不截断） */
    fun shareGameLog(target: Ported) {
        if (!actions.storageOk()) { actions.onToast(locale.str(R.string.lib_log_needs_storage)); return }
        val game = FolderScanner.displayName(f)
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { GameLog.copyTo(target.pack, SaveShare.packagesDir(context), game) }
            }
            r.onSuccess { copied ->
                if (copied == null) {
                    actions.onToast(locale.str(R.string.lib_no_runtime_log))
                } else {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(SaveShare.textIntent(context, copied), locale.str(R.string.main_share_log)))
                    }.onFailure { e ->
                        actions.onToast(if (e is ActivityNotFoundException) locale.str(R.string.lib_no_receiver)
                        else locale.str(R.string.lib_share_failed, failureText(locale, e)))
                    }
                }
            }.onFailure {
                actions.onToast(locale.str(R.string.lib_log_export_failed, failureText(locale, it)))
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            // 遮罩：左边留出游戏自己的画面，往右渐渐暗下去接到面板（不是一块死黑）
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.28f to Color(0x8C0E0E0F),
                    0.44f to Color(0xD90E0E0F),
                    1f to Color(0xF20E0E0F),
                )
            )
            .clickable { onClose() },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .fillMaxHeight()
                .background(NxPalette.Surface)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        FolderScanner.displayName(f),
                        style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(titleId, statusLabel(status), experimentalTag(ported)).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 已完成的游戏才有可管理的产物
                if (status == GameStatus.DONE) {
                    Box {
                        QuietButton(locale.str(R.string.lib_manage), onClick = { menuOpen = !menuOpen },
                            modifier = Modifier.width(112.dp),
                            trailing = { Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp)) })
                        androidx.compose.material3.DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(locale.str(R.string.main_share_log)) },
                                leadingIcon = { Icon(Icons.Filled.Description, null, Modifier.size(18.dp)) },
                                onClick = {
                                    menuOpen = false
                                    val p = ported
                                    if (p == null) actions.onToast(locale.str(R.string.lib_artifact_missing))
                                    else shareGameLog(p)
                                },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(locale.str(R.string.lib_delete_artifact), color = NxPalette.Danger) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = NxPalette.Danger) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }
            Hint(f.absolutePath, size = 11.sp)

            // 内容自上而下顺排：不用弹性 spacer 把它压到底（那会在路径与「产物：」之间开一大片空白）；
            // 列本身可滚，存档多时也够得着。

            when {
                status == GameStatus.PORTING -> {
                    Hint(locale.str(R.string.lib_porting_see_builds))
                    Spacer(Modifier.height(6.dp))
                    CapsuleButton(locale.str(R.string.lib_view_builds), {
                        lib.buildsTarget = f
                        onClose()
                        onGoBuilds()
                    }, modifier = Modifier.fillMaxWidth())
                }
                status == GameStatus.DONE -> {
                    if (ported == null) {
                        Hint(locale.str(R.string.lib_artifact_missing_state))
                    } else {
                        Hint(locale.str(R.string.builds_artifact_path, ported.pack.absolutePath), size = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        RunOptions(ported)
                        Spacer(Modifier.height(10.dp))
                        SaveRows(ported, FolderScanner.displayName(f), player, actions, onRequest, actions.onToast)
                        Spacer(Modifier.height(6.dp))
                        val playing = player.running && player.titleId == ported.titleId
                        if (playing) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CapsuleButton(locale.str(R.string.drawer_back_to_game), { actions.onResumeGame() }, modifier = Modifier.weight(1f))
                                CapsuleButton(locale.str(R.string.drawer_exit), { actions.onStopGame() }, modifier = Modifier.weight(1f))
                            }
                        } else {
                            val locked = player.running
                            if (locked) Hint(locale.str(R.string.lib_close_current_game, player.name))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CapsuleButton(locale.str(R.string.builds_run_action), {
                                    actions.onRun(ported.pack, FolderScanner.displayName(f))
                                }, modifier = Modifier.weight(1f), enabled = !locked)
                                CapsuleButton(locale.str(R.string.lib_build_action), {
                                    lib.buildsTarget = f
                                    onClose()
                                    onGoBuilds()
                                }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                else -> {
                    // ▶ 构建：参数已即时保存，带参跳转，开始按钮在构建页上
                    CapsuleButton(locale.str(R.string.lib_build_action), {
                        lib.buildsTarget = f
                        onClose()
                        onGoBuilds()
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(locale.str(R.string.lib_delete_artifact_confirm)) },
            text = { Text(locale.str(R.string.lib_delete_artifact_what)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = false
                    val gone = lib.deleteOutcome(f)
                    actions.onToast(if (gone) locale.str(R.string.lib_artifact_deleted) else locale.str(R.string.lib_delete_failed_busy))
                }) { Text(locale.str(R.string.common_delete), color = NxPalette.Danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text(locale.str(R.string.common_cancel)) }
            },
        )
    }
}

/**
 * 运行前配置（数据包的 env.txt，下次运行生效）：游戏语言与 Vulkan 驱动，选项来自包内的清单文件。
 * 驱动有两个来源：数据包自己带的（写在 drivers.txt 里，宿主在自己旁边找它）和玩家装在 App 里的
 * （在 App 的私有目录里，按绝对路径写进 env.txt）。
 */
@Composable
private fun RunOptions(ported: com.nx.android.domain.Ported) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val langs = remember { PackEnv.languages(ported.pack) }
    val installed = remember { Drivers.list(context) }
    var current by remember { mutableStateOf(PackEnv.current(ported.pack)) }

    PickRow(stringResource(R.string.lib_section_language), langs.map { it.first to it.second }, current.first,
        onSelect = { code ->
            PackEnv.set(ported.pack, code, current.second)
            current = PackEnv.current(ported.pack)
        }, fallback = stringResource(R.string.driver_system_default))
    // 设备自己的驱动在 domain 层没有名字：这里用 App 自己的词给它起一个
    val systemDriver = stringResource(R.string.driver_system_default)
    PickRow(stringResource(R.string.driver_section),
        PackEnv.driverChoices(ported.pack, installed.map { it.library.absolutePath to it.label })
            .map { (value, name) -> value to (name ?: systemDriver) }, current.second,
        onSelect = { value ->
            PackEnv.set(ported.pack, current.first, value.takeIf { it.isNotEmpty() })
            current = PackEnv.current(ported.pack)
        }, fallback = systemDriver)
}

/**
 * 存档（库页侧）：导出成一个可以发出去的包，导入把别人发来的包换进去。
 *
 * 活存档是应用自己的文件（宿主把它挂在 filesDir/save/<titleId>），所以这里直接动它：不写请求文件，
 * 也不用等下次启动游戏。游戏在跑的时候不动它——那会儿它正往存档里写。
 */
@Composable
private fun SaveRows(ported: Ported, gameName: String, player: PlayerState, actions: AppActions,
                     onRequest: (PickerRequest) -> Unit, onToast: (String) -> Unit) {
    val locale = LocalAppLocale.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val titleId = ported.titleId
    val live = remember(titleId) { SaveBackup.liveDir(context.filesDir, titleId) }
    val liveDevice = remember(titleId) { SaveBackup.deviceDir(context.filesDir, titleId) }
    val packSaves = remember(ported.pack) { File(ported.pack, "saves") }
    val packagesDir = remember { SaveShare.packagesDir(context) }

    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }          // 上一次做了什么（留在面板里，不只弹一下）
    var askClose by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<File?>(null) }
    var pendingRestore by remember { mutableStateOf<File?>(null) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }

    // 下载目录里的包，和数据包 saves/ 里的快照（备份）
    val packages = remember(reload, packagesDir) {
        packagesDir.listFiles()?.filter { it.isFile && it.name.endsWith(".${SaveBackup.EXT}") }
            ?.sortedByDescending { it.name }.orEmpty()
    }
    val backups = remember(reload, packSaves) {
        packSaves.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && !it.name.endsWith(".device") }
            ?.sortedByDescending { it.name }.orEmpty()
    }

    fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    // 游戏在跑的时候它的存档正被写着：这时不碰它
    fun gameRunning(): Boolean = player.running || Player.playProcessAlive(context)

    fun sharePackage(file: File) {
        runCatching { context.startActivity(Intent.createChooser(SaveShare.intent(context, file), locale.str(R.string.lib_share_saves))) }
            .onFailure { onToast(locale.str(R.string.lib_no_receiver)) }
    }

    fun doExport() {
        if (gameRunning()) { askClose = true; return }
        if (!actions.storageOk()) { onToast(locale.str(R.string.lib_saves_need_storage)); return }
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val name = SaveBackup.packageName(gameName, titleId, stamp())
                    SaveBackup.export(live, liveDevice, File(packagesDir, name))
                        ?: error(locale.str(R.string.lib_no_saves))
                }
            }
            busy = false
            reload++
            r.onSuccess {
                note = locale.str(R.string.lib_exported_to, it.parentFile?.absolutePath)
                onToast(note)
            }.onFailure {
                note = locale.str(R.string.lib_export_failed, failureText(locale, it))
                onToast(note)
            }
        }
    }

    fun doImport(pkg: File) {
        if (gameRunning()) { askClose = true; return }
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(context.cacheDir, "save-import")
                    tmp.deleteRecursively()
                    val unpacked = SaveBackup.unpack(pkg, tmp)
                    val backup = SaveBackup.backup(live, liveDevice, packSaves, stamp())   // 换掉的进度先留着
                    SaveBackup.swapIn(unpacked.tree, unpacked.device, live, liveDevice)
                    tmp.deleteRecursively()
                    backup?.name
                }
            }
            busy = false
            reload++
            r.onSuccess {
                note = if (it != null) locale.str(R.string.lib_imported_backup, it) else locale.str(R.string.lib_imported)
                onToast(note)
            }.onFailure {
                note = locale.str(R.string.lib_import_failed, failureText(locale, it))
                onToast(note)
            }
        }
    }

    fun doRestore(backup: File) {
        if (gameRunning()) { askClose = true; return }
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val kept = SaveBackup.backup(live, liveDevice, packSaves, stamp())
                    val dev = File(backup.parentFile, backup.name + ".device").takeIf { it.isDirectory }
                    SaveBackup.swapIn(backup, dev, live, liveDevice)
                    kept?.name
                }
            }
            busy = false
            reload++
            r.onSuccess {
                note = if (it != null) locale.str(R.string.lib_restored_backup, it) else locale.str(R.string.lib_restored)
                onToast(note)
            }.onFailure {
                note = locale.str(R.string.lib_restore_failed, failureText(locale, it))
                onToast(note)
            }
        }
    }

    fun shareBackup(backup: File) {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val name = SaveBackup.packageName(gameName, titleId, stamp())
                    val dest = SaveBackup.freeName(File(packagesDir, name))
                    val dev = File(backup.parentFile, backup.name + ".device").takeIf { it.isDirectory }
                    SaveBackup.writePackage(backup, dev, dest.name.removeSuffix(".${SaveBackup.EXT}"), dest)
                    dest
                }
            }
            busy = false
            reload++
            r.onSuccess { sharePackage(it) }.onFailure { onToast(locale.str(R.string.lib_package_failed, failureText(locale, it))) }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Text(locale.str(R.string.lib_section_saves), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        QuietButton(locale.str(R.string.lib_export_saves), onClick = { doExport() }, modifier = Modifier.weight(1f), enabled = !busy)
        QuietButton(locale.str(R.string.lib_import_saves), onClick = {
            onRequest(PickerRequest(PickerMode.FILE, setOf(SaveBackup.EXT), packagesDir, locale.str(R.string.lib_choose_save_package), onPicked = { pkg ->
                val id = SaveBackup.titleIdIn(pkg.name)
                if (id != null && !id.equals(titleId, ignoreCase = true)) {
                    note = locale.str(R.string.lib_other_games_save, id)
                    onToast(note)
                } else {
                    pendingImport = pkg
                }
            }))
        }, modifier = Modifier.weight(1f), enabled = !busy)
    }
    Hint(locale.str(R.string.lib_packages_where, packagesDir.absolutePath), size = 11.sp)
    if (note.isNotEmpty()) Hint(note, size = 11.sp)

    if (packages.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(locale.str(R.string.lib_section_packages), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        for (p in packages) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${p.name}  ·  ${sizeText(p.length())}",
                     style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                     maxLines = 1, overflow = TextOverflow.Ellipsis)
                QuietButton(locale.str(R.string.common_share), onClick = { sharePackage(p) }, enabled = !busy)
                QuietButton(locale.str(R.string.common_delete), onClick = { pendingDelete = p }, enabled = !busy)
            }
        }
    }

    if (backups.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(locale.str(R.string.lib_section_backups), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        for (b in backups) {
            val dev = File(b.parentFile, b.name + ".device")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${b.name}  ·  ${sizeText(treeSize(b) + if (dev.isDirectory) treeSize(dev) else 0)}",
                     style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                     maxLines = 1, overflow = TextOverflow.Ellipsis)
                QuietButton(locale.str(R.string.common_restore), onClick = { pendingRestore = b }, enabled = !busy)
                QuietButton(locale.str(R.string.common_share), onClick = { shareBackup(b) }, enabled = !busy)
                QuietButton(locale.str(R.string.common_delete), onClick = { pendingDelete = b }, enabled = !busy)
            }
        }
    }

    // 游戏还在跑：存档正被它写着，先让它退出
    if (askClose) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askClose = false },
            title = { Text(locale.str(R.string.lib_game_running)) },
            text = { Text(locale.str(R.string.lib_game_running_hint)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    askClose = false
                    actions.onStopGame()
                }) { Text(locale.str(R.string.drawer_exit), color = NxPalette.Building) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { askClose = false }) { Text(locale.str(R.string.common_cancel)) } },
        )
    }

    pendingImport?.let { pkg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(locale.str(R.string.lib_import_confirm)) },
            text = { Text(locale.str(R.string.lib_import_confirm_body, pkg.name)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingImport = null
                    doImport(pkg)
                }) { Text(locale.str(R.string.common_import), color = NxPalette.Building) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingImport = null }) { Text(locale.str(R.string.common_cancel)) } },
        )
    }

    pendingRestore?.let { backup ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(locale.str(R.string.lib_restore_confirm)) },
            text = { Text(locale.str(R.string.lib_restore_confirm_body, backup.name)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingRestore = null
                    doRestore(backup)
                }) { Text(locale.str(R.string.common_restore), color = NxPalette.Building) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingRestore = null }) { Text(locale.str(R.string.common_cancel)) } },
        )
    }

    pendingDelete?.let { file ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(locale.str(R.string.lib_delete_confirm)) },
            text = { Text(locale.str(R.string.lib_delete_confirm_body, file.name)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingDelete = null
                    // 一份备份还有它的设备存档在旁边：一起删
                    val dev = File(file.parentFile, file.name + ".device")
                    val gone = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (dev.isDirectory) dev.deleteRecursively()
                    reload++
                    onToast(if (gone) locale.str(R.string.lib_deleted, file.name) else locale.str(R.string.lib_delete_failed))
                }) { Text(locale.str(R.string.common_delete), color = NxPalette.Danger) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) { Text(locale.str(R.string.common_cancel)) } },
        )
    }
}

/** 一棵目录树里的文件加起来多大 */
private fun treeSize(dir: File): Long =
    if (!dir.isDirectory) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

/** 一个人看得懂的大小 */
private fun sizeText(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
