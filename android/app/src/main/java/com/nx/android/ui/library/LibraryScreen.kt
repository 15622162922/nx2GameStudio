package com.nx.android.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Checkbox
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
import com.nx.android.appstate.PortState
import com.nx.android.data.GameIcon
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
import com.nx.android.ui.theme.NxPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 游戏库：主机式行视图——横向瓦片（88/128dp），选中的放大，选中游戏铺满背景并在左下给出详情面板（大标题、
 * 状态、▶ 构建、配置与安装的圆钮）。移植配置从右侧全高滑入。
 */
@Composable
fun LibraryScreen(lib: LibraryState, port: PortState, actions: AppActions,
                  onGoBuilds: () -> Unit, onRequest: (PickerRequest) -> Unit) {

    // the folder changes, a port ends, or the page comes back: the cards' statuses are re-read
    LaunchedEffect(lib.folder, port.phase) { lib.rescan() }

    val visible = lib.visible()
    LaunchedEffect(visible.size) { lib.selectHero(lib.heroIndex, visible.size) }

    RowView(lib, port, visible, actions, onGoBuilds, onRequest)

    // the back key closes the panel first, the app second
    BackHandler(enabled = lib.selected != null) { lib.selected = null }

    // the panel's game outlives the closing animation
    val panelFile = remember { mutableStateOf<File?>(null) }
    LaunchedEffect(lib.selected) { if (lib.selected != null) panelFile.value = lib.selected }
    AnimatedVisibility(
        visible = lib.selected != null,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        panelFile.value?.let { f ->
            ConfigPanel(lib, port, f, actions, onGoBuilds, onClose = { lib.selected = null })
        }
    }
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
private fun RowView(lib: LibraryState, port: PortState,
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
        sel?.let { HeroDetails(lib, port, it, actions, onGoBuilds) }
        Spacer(Modifier.height(18.dp))
    }
}

/** 选中游戏的全屏呈现：已完成的游戏用产物 APK 里的图标实图，其余用名字的渐变与首字母水印 */
@Composable
internal fun HeroBackground(lib: LibraryState, port: PortState, sel: File?) {
    if (sel == null) return
    val apk = lib.apkOf(sel)
    val icon by rememberGameIcon(apk?.absolutePath, 1280)

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
    val icon by rememberGameIcon(lib.apkOf(f)?.absolutePath, 256)
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

/** 左下详情：大标题、Title ID · 状态、▶ 开始移植与圆形次级钮 */
@Composable
private fun HeroDetails(lib: LibraryState, port: PortState,
                        f: File, actions: AppActions, onGoBuilds: () -> Unit) {
    val status = lib.statusOf(f)
    val titleId = FolderScanner.titleIdIn(f.name)
    val apk = lib.apkOf(f)

    Column(Modifier.widthIn(max = 640.dp)) {
        Text(
            FolderScanner.displayName(f),
            style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(titleId ?: "（无 Title ID）", statusLabel(status) +
                if (status == GameStatus.PORTING) " ${(port.progress * 100).toInt()}%" else "").joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // ▶ 构建：携带游戏与配置跳到构建页（不直接开跑，NX2Apple 的 configureBuild）；运行中变「查看构建」
            val building = status == GameStatus.PORTING
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NxPalette.Cream)
                    .clickable {
                        lib.buildsTarget = f
                        onGoBuilds()
                    }
                    .padding(horizontal = 30.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp), tint = Color.Black)
                Spacer(Modifier.width(6.dp))
                Text(if (building) "查看构建" else "构建", style = MaterialTheme.typography.titleMedium, color = Color.Black)
            }
            // 配置圆钮
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
                    .clickable { lib.selected = f }.padding(11.dp),
            ) {
                Icon(Icons.Filled.Tune, "移植配置", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onBackground)
            }
            // 安装圆钮（已完成才有）
            if (status == GameStatus.DONE && apk != null) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
                        .clickable { actions.onInstall(apk) }.padding(11.dp),
                ) {
                    Icon(Icons.Filled.Download, "安装 APK", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
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

/** 移植配置面板：文件路径、状态与该游戏自己的选项，开始移植与安装也从这里走 */
@Composable
private fun ConfigPanel(lib: LibraryState, port: PortState,
                        f: File, actions: AppActions, onGoBuilds: () -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    val status = lib.statusOf(f)
    val titleId = FolderScanner.titleIdIn(f.name)
    val context = androidx.compose.ui.platform.LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { onClose() },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .fillMaxHeight()
                .background(NxPalette.Surface)
                .clickable(enabled = false) {}
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
                        QuietButton("管理 ⌄", onClick = { menuOpen = !menuOpen })
                        androidx.compose.material3.DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("分享 APK") },
                                leadingIcon = { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)) },
                                onClick = {
                                    menuOpen = false
                                    lib.apkOf(f)?.let { apk ->
                                        val uri = com.nx.android.data.ApkProvider.uriFor(context, apk)
                                        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                            .setType("application/vnd.android.package-archive")
                                            .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        runCatching {
                                            context.startActivity(android.content.Intent.createChooser(send, "分享 APK"))
                                        }.onFailure { actions.onToast("分享失败：${it.message}") }
                                    }
                                },
                            )
                            HorizontalDivider()
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("删除产物 APK", color = NxPalette.Danger) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = NxPalette.Danger) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }
            Hint(f.absolutePath, size = 11.sp)

            Spacer(Modifier.weight(1f))

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
                    val apk = lib.apkOf(f)
                    if (apk == null) {
                        Hint("产物 APK 不见了（输出目录里没找到）——状态回落为未移植")
                    } else {
                        Hint("产物：${apk.absolutePath}", size = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CapsuleButton("安装 APK", {
                            lib.apkOf(f)?.let { actions.onInstall(it) }
                        }, modifier = Modifier.weight(1f))
                        CapsuleButton("▶ 构建", {
                            lib.buildsTarget = f
                            onClose()
                            onGoBuilds()
                        }, modifier = Modifier.weight(1f))
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
            text = { Text("会删除产物 APK 和数据包（存档在数据包里，会一并消失），不可恢复。游戏源文件不受影响。") },
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

@Composable
private fun ConfigRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
