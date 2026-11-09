package com.nx.android.ui.library

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.PlayerState
import com.nx.android.appstate.PortState
import com.nx.android.data.Drivers
import com.nx.android.data.GameIcon
import com.nx.android.domain.FolderScanner
import com.nx.android.domain.GameStatus
import com.nx.android.domain.PackEnv
import com.nx.android.ui.AppActions
import com.nx.android.ui.components.PickerMode
import com.nx.android.ui.components.PickerRequest
import com.nx.android.ui.components.CapsuleButton
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.Section
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.theme.NxPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 游戏库：主机式行视图——横向瓦片（88/128dp），选中的放大，选中游戏铺满背景并在左下给出详情面板（大标题、
 * 状态、▶ 构建、配置与安装的圆钮）。移植配置从右侧全高滑入。
 */
@Composable
fun LibraryScreen(lib: LibraryState, port: PortState, player: PlayerState, actions: AppActions,
                  onGoBuilds: () -> Unit, onRequest: (PickerRequest) -> Unit) {

    // the folder changes, a port ends, or the page comes back: the cards' statuses are re-read
    LaunchedEffect(lib.folder, port.phase) { lib.rescan() }

    val visible = lib.visible()
    LaunchedEffect(visible.size) { lib.selectHero(lib.heroIndex, visible.size) }

    RowView(lib, port, player, visible, actions, onGoBuilds, onRequest)

    // 配置面板不在这里画：它要盖住顶栏，所以由页面的根（MainScreen）在最上层画（见 ui/App.kt）
}

private fun statusColor(s: GameStatus) = when (s) {
    GameStatus.NOT_PORTED -> NxPalette.Unbuilt
    GameStatus.PORTING -> NxPalette.Building
    GameStatus.DONE -> NxPalette.Ready
}

private fun statusLabel(s: GameStatus): String = when (s) {
    GameStatus.NOT_PORTED -> "未移植"
    GameStatus.PORTING -> "移植中"
    GameStatus.DONE -> "已完成"
}

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
                    visible: List<File>, actions: AppActions, onGoBuilds: () -> Unit,
                    onRequest: (PickerRequest) -> Unit) {
    val sel = visible.getOrNull(lib.heroIndex)

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (visible.isEmpty()) {
            // 空态：连文件夹都没有 → 引导选择；有文件夹但没扫到 → 提示
            Spacer(Modifier.weight(1f))
            if (lib.folder.isBlank() && lib.extras.isEmpty()) {
                EmptyLibrary(onPick = {
                onRequest(PickerRequest(PickerMode.DIR, emptySet(),
                    File(lib.folder.ifBlank { "/storage" }), "选择游戏文件夹",
                    onPicked = { dir -> lib.chooseFolder(dir.path) }))
            })
            } else {
                Section("没有游戏") {
                    Text("这个文件夹里没有游戏文件（NSP / XCI / NSZ / XCZ）。", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        QuietButton("换个文件夹", onClick = {
                            onRequest(PickerRequest(PickerMode.DIR, emptySet(),
                                File(lib.folder.ifBlank { "/storage" }), "选择游戏文件夹",
                                onPicked = { dir -> lib.chooseFolder(dir.path) }))
                        })
                    }
                }
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
                if (status == GameStatus.PORTING) " ${(port.progress * 100).toInt()}%" else "").joinToString("  ·  "),
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
                    when { building -> "查看构建"; running -> "运行"; else -> "构建" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (locked) NxPalette.Background else Color.Black,
                )
            }
            // 配置圆钮
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
                    .clickable { lib.selected = f }.padding(11.dp),
            ) {
                Icon(Icons.Filled.Tune, if (running) "运行配置" else "移植配置", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        if (locked) {
            Spacer(Modifier.height(6.dp))
            Text("先退出当前游戏（${player.name}）再运行这个", style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status == GameStatus.DONE && player.running && player.titleId == (ported?.titleId ?: "")) {
            // 这个游戏正在玩：回到游戏与退出直达
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton("回到游戏", onClick = { actions.onResumeGame() })
                QuietButton("退出游戏", onClick = { actions.onStopGame() })
            }
        }
        if (status == GameStatus.DONE && player.exited) {
            Spacer(Modifier.height(6.dp))
            Text("游戏已退出", style = MaterialTheme.typography.bodySmall, color = NxPalette.Building)
        }
    }
}

// ---- 网格视图 ---------------------------------------------------------------------------------------------------

@Composable
private fun EmptyLibrary(onPick: () -> Unit) {
    Section("游戏库是空的") {
        Text("选一个放游戏文件的文件夹（NSP / XCI / NSZ / XCZ），子目录也会被扫到。",
             style = MaterialTheme.typography.bodyMedium)
        Row { CapsuleButton("选择游戏文件夹", onPick) }
    }
}

// ---- 移植配置面板（右侧全高滑入） --------------------------------------------------------------------------------

/**
 * 移植配置面板：文件路径、状态与该游戏自己的选项，开始移植与安装也从这里走。
 * 由页面的根在最上层画（MainScreen），它的遮罩要盖住顶栏。
 */
@Composable
internal fun ConfigPanel(lib: LibraryState, port: PortState, player: PlayerState,
                        f: File, actions: AppActions, onGoBuilds: () -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    val status = lib.statusOf(f)
    // Title ID：文件名里的优先，没有就用数据包清单里的
    val titleId = FolderScanner.titleIdIn(f.name)
        ?: lib.portOf(f)?.titleId?.takeIf { it.isNotEmpty() }?.uppercase()
    val context = androidx.compose.ui.platform.LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

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
                        listOfNotNull(titleId, statusLabel(status)).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 已完成的游戏才有可管理的产物
                if (status == GameStatus.DONE) {
                    Box {
                        QuietButton("管理", onClick = { menuOpen = !menuOpen },
                            modifier = Modifier.width(112.dp),
                            trailing = { Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp)) })
                        androidx.compose.material3.DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("删除产物", color = NxPalette.Danger) },
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
                    Hint("正在移植中——去构建页看进度，或点下面的「查看构建」")
                    Spacer(Modifier.height(6.dp))
                    CapsuleButton("查看构建", {
                        lib.buildsTarget = f
                        onClose()
                        onGoBuilds()
                    }, modifier = Modifier.fillMaxWidth())
                }
                status == GameStatus.DONE -> {
                    val ported = lib.portOf(f)
                    if (ported == null) {
                        Hint("产物不见了（输出目录里没找到）——状态回落为未移植")
                    } else {
                        Hint("产物：${ported.pack.absolutePath}", size = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        RunOptions(ported)
                        Spacer(Modifier.height(10.dp))
                        SaveImportRows(ported.pack, onToast = actions.onToast)
                        Spacer(Modifier.height(6.dp))
                        val playing = player.running && player.titleId == ported.titleId
                        if (playing) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CapsuleButton("回到游戏", { actions.onResumeGame() }, modifier = Modifier.weight(1f))
                                CapsuleButton("退出游戏", { actions.onStopGame() }, modifier = Modifier.weight(1f))
                            }
                        } else {
                            val locked = player.running
                            if (locked) Hint("先退出当前游戏（${player.name}）再运行这个")
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CapsuleButton("▶ 运行", {
                                    actions.onRun(ported.pack, FolderScanner.displayName(f))
                                }, modifier = Modifier.weight(1f), enabled = !locked)
                                CapsuleButton("▶ 构建", {
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
                    CapsuleButton("▶ 构建", {
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
            title = { Text("删除产物？") },
            text = { Text("会删除整个数据包（宿主、图标，存档在数据包里，会一并消失），不可恢复。游戏源文件不受影响。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = false
                    val gone = lib.deleteOutcome(f)
                    actions.onToast(if (gone) "产物已删除" else "删除失败：文件可能正被占用")
                }) { Text("删除", color = NxPalette.Danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("取消") }
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

    PickRow("语言", langs.map { it.first to it.second }, current.first,
        onSelect = { code ->
            PackEnv.set(ported.pack, code, current.second)
            current = PackEnv.current(ported.pack)
        })
    PickRow("Vulkan 驱动",
        PackEnv.driverChoices(ported.pack, installed.map { it.library.absolutePath to it.label }), current.second,
        onSelect = { value ->
            PackEnv.set(ported.pack, current.first, value.takeIf { it.isNotEmpty() })
            current = PackEnv.current(ported.pack)
        })
}

/**
 * 存档（库页侧）：导出与导入都在这里，两个都是写请求——宿主在下次启动游戏时做。
 * 导出写 saves/export.txt（带 request=export 的记号），宿主做完把结果写回 saves/result.txt。
 */
@Composable
private fun SaveImportRows(pack: File, onToast: (String) -> Unit) {
    var listing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val savesDir = File(pack, "saves")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Text("存档", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        QuietButton("导出存档", onClick = { requestExport(savesDir, onToast) }, modifier = Modifier.weight(1f))
        QuietButton("导入存档…", onClick = { listing = !listing }, modifier = Modifier.weight(1f))
    }
    Hint(exportNote(savesDir), size = 11.sp)
    if (!listing) return
    val exports = remember(listing) { savesDir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: emptyList() }
    if (exports.isEmpty()) {
        Hint("还没有导出的存档。导出在下次启动游戏时生效，之后在这里导入。")
        return
    }
    for (d in exports) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(d.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            QuietButton("导入", onClick = { confirm = d.name })
        }
    }
    confirm?.let { name ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("导入这份存档？") },
            text = { Text("$name\n\n下次启动游戏时换入（当前进度会先被导出保留）。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirm = null
                    val ok = runCatching {
                        File(savesDir, "import.txt").writeText(name + "\n")
                        true
                    }.getOrDefault(false)
                    onToast(if (ok) "已选「$name」：下次启动游戏时导入" else "写入导入请求失败")
                }) { Text("导入", color = NxPalette.Building) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text("取消") } },
        )
    }
}

/** 写导出的请求：宿主在下次启动游戏时导出这份存档，并把结果写回 saves/result.txt */
private fun requestExport(savesDir: File, onToast: (String) -> Unit) {
    val ok = runCatching {
        savesDir.mkdirs()
        File(savesDir, "export.txt").writeText("request=export\n")
        true
    }.getOrDefault(false)
    onToast(if (ok) "已请求导出：下次启动游戏时导出一份存档" else "写入导出请求失败")
}

/** 导出现在是什么状态：等着的请求、上一次导出的结果（成功或失败），或者只是一句说明 */
private fun exportNote(savesDir: File): String {
    if (File(savesDir, "export.txt").isFile) return "已请求导出：下次启动游戏时导出一份存档"
    val lines = runCatching { File(savesDir, "result.txt").readText() }.getOrDefault("").lines()
    val fields = lines.mapNotNull { line ->
        val i = line.indexOf('=')
        if (i < 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
    }.toMap()
    if (fields["request"] != "export") return "导出与导入都在下次启动游戏时生效。"
    return when (fields["result"]) {
        "ok" -> "上次导出：${fields["name"] ?: "存档"}"
        "error" -> "上次导出失败：${fields["error"] ?: "原因不明"}"
        else -> "导出与导入都在下次启动游戏时生效。"
    }
}

/**
 * 一个选项行：左边是名字，右边是一个按钮，点了展开选项。每一项是一对：值（写进 env.txt 的）和
 * 名字（玩家看到的）——两者不一样的时候（装在 App 目录里的驱动，值是它的路径），按钮上显示的是名字。
 */
@Composable
private fun PickRow(label: String, options: List<Pair<String, String>>, current: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // 现在的值不在选项里（驱动被删了、包里的文件没了）：显示它最后的名字，别把整条路径摊出来
    val shown = options.firstOrNull { it.first == current }?.second
        ?: current?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
        ?: "系统默认"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
        Box {
            // 按钮定宽：箭头钉在右端，两个字段的箭头排成一列（值的长度不再影响它的位置）
            QuietButton(shown, onClick = { open = !open },
                modifier = Modifier.width(200.dp),
                trailing = { Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp)) })
            androidx.compose.material3.DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.width(200.dp),
            ) {
                for ((value, name) in options.distinctBy { it.first }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name, fontWeight = if (value == current) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { open = false; onSelect(value) },
                    )
                }
            }
        }
    }
}
