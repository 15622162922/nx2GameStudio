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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.alpha
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
import com.nx.android.ui.components.CapsuleButton
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.Section
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.components.rememberLibraryGrid
import com.nx.android.ui.theme.NxPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 游戏库：主机式双视图。**行视图**——横向瓦片，选中的放大，选中游戏铺满背景并在左下给出详情面板（大标题、
 * 状态、▶ 开始移植、配置与安装的圆钮）；**网格视图**——搜索、过滤与卡片网格。移植配置从右侧全高滑入。
 */
@Composable
fun LibraryScreen(lib: LibraryState, port: PortState, keys: String, envReady: Boolean, actions: AppActions) {

    // the folder changes, a port ends, or the page comes back: the cards' statuses are re-read
    LaunchedEffect(lib.folder, port.phase, lib.search) { lib.rescan() }

    val visible = lib.visible()
    LaunchedEffect(visible.size) { lib.selectHero(lib.heroIndex, visible.size) }

    Box(Modifier.fillMaxSize()) {
        if (lib.view == LibraryState.View.ROW) {
            RowView(lib, port, keys, envReady, visible, actions)
        } else {
            GridView(lib, port, visible, actions)
        }
    }

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
            ConfigPanel(lib, port, keys, envReady, f, actions, onClose = { lib.selected = null })
        }
    }
}

private fun filterLabel(f: LibraryState.Filter): String = when (f) {
    LibraryState.Filter.ALL -> "全部"
    LibraryState.Filter.NOT_PORTED -> "未移植"
    LibraryState.Filter.DONE -> "已完成"
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
private fun rememberGameIcon(path: String?): androidx.compose.runtime.State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching { GameIcon.decode(path, 1280)?.asImageBitmap() }.getOrNull()
        }
    }

// ---- 行视图（hero） -------------------------------------------------------------------------------------------

@Composable
private fun RowView(lib: LibraryState, port: PortState, keys: String, envReady: Boolean,
                    visible: List<File>, actions: AppActions) {
    val sel = visible.getOrNull(lib.heroIndex)

    Box(Modifier.fillMaxSize()) {
        HeroBackground(lib, port, sel)

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth()) {
                HeroRow(lib, port, visible) { i ->
                    if (i == lib.heroIndex) lib.selected = visible.getOrNull(i) else lib.selectHero(i, visible.size)
                }
                Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ViewSwitch(lib)
                }
            }
            visible.getOrNull(lib.heroIndex)?.let {
                Text(
                    FolderScanner.displayName(it),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            sel?.let { HeroDetails(lib, port, keys, envReady, it, actions) }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** 选中游戏的全屏呈现：已完成的游戏用产物 APK 里的图标实图，其余用名字的渐变与首字母水印 */
@Composable
private fun HeroBackground(lib: LibraryState, port: PortState, sel: File?) {
    if (sel == null) return
    val apk = lib.apkOf(sel)
    val icon by rememberGameIcon(apk?.absolutePath)

    Box(Modifier.fillMaxSize()) {
        val bitmap = icon
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap, null, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, alpha = 0.5f,
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
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(coverColors(f.name)))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else NxPalette.Unbuilt.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            FolderScanner.displayName(f).take(1).ifEmpty { "?" },
            fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f),
        )
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
private fun HeroDetails(lib: LibraryState, port: PortState, keys: String, envReady: Boolean,
                        f: File, actions: AppActions) {
    val status = lib.statusOf(f)
    val titleId = FolderScanner.titleIdIn(f.name)
    val storageOk = actions.storageOk()
    val canPort = envReady && storageOk && !port.running && keys.isNotBlank() && status != GameStatus.PORTING
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
            // ▶ 开始移植：奶油胶囊
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NxPalette.Cream)
                    .clickable(enabled = canPort) { actions.onStartPort(f, lib.configOf(f)) }
                    .padding(horizontal = 30.dp, vertical = 11.dp)
                    .alpha(if (canPort) 1f else 0.45f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp), tint = Color.Black)
                Spacer(Modifier.width(6.dp))
                Text("开始移植", style = MaterialTheme.typography.titleMedium, color = Color.Black)
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
        if (!canPort && status == GameStatus.NOT_PORTED) {
            Text(
                when {
                    !envReady -> "先在设置里准备环境"
                    !storageOk -> "先授予存储访问权限"
                    keys.isBlank() -> "先在设置里填 prod.keys 的路径"
                    port.running -> "正在移植 ${File(port.gameFile).name}——一次只能移植一个"
                    else -> ""
                },
                color = NxPalette.Building, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ---- 网格视图 ---------------------------------------------------------------------------------------------------

@Composable
private fun GridView(lib: LibraryState, port: PortState, visible: List<File>, actions: AppActions) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = lib.search,
                onValueChange = { lib.search = it },
                placeholder = { Text("搜索游戏…", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = NxPalette.Surface.copy(alpha = 0.6f),
                    focusedContainerColor = NxPalette.Surface.copy(alpha = 0.8f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = NxPalette.Cream.copy(alpha = 0.4f),
                ),
                modifier = Modifier.weight(1f).height(54.dp),
            )
            for (f in LibraryState.Filter.entries) {
                FilterChip(
                    selected = lib.filter == f,
                    onClick = { lib.chooseFilter(f) },
                    label = { Text(filterLabel(f), fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = NxPalette.Surface.copy(alpha = 0.6f),
                        selectedContainerColor = NxPalette.Cream.copy(alpha = 0.18f),
                        selectedLabelColor = NxPalette.Cream,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
            ViewSwitch(lib)
        }

        if (lib.folder.isBlank()) {
            EmptyLibrary(onPick = { actions.onPickLibraryFolder { lib.chooseFolder(it) } })
        } else if (visible.isEmpty() && !lib.scanning) {
            Section("没有游戏") {
                Text("这个文件夹里没有${if (lib.search.isNotBlank()) "匹配的游戏" else "游戏文件（NSP / XCI / NSZ / XCZ）"}",
                     style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuietButton("换个文件夹", { actions.onPickLibraryFolder { lib.chooseFolder(it) } })
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = rememberLibraryGrid(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visible, key = { it.absolutePath }) { f ->
                    GameCard(lib, port, f, onClick = { lib.selected = f })
                }
            }
        }
    }
}

/** 行/网格切换：两个小图标，当前视图亮色 */
@Composable
private fun ViewSwitch(lib: LibraryState) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ViewSwitchIcon(Icons.Filled.ViewAgenda, "行视图", lib.view == LibraryState.View.ROW) { lib.chooseView(LibraryState.View.ROW) }
        ViewSwitchIcon(Icons.Filled.GridView, "网格视图", lib.view == LibraryState.View.GRID) { lib.chooseView(LibraryState.View.GRID) }
    }
}

@Composable
private fun ViewSwitchIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String,
                           selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(20.dp),
             tint = if (selected) NxPalette.Cream else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A card: the cover (a color from the game's name, its first letter), the status dot, the name below */
@Composable
private fun GameCard(lib: LibraryState, port: PortState, f: File, onClick: () -> Unit) {
    val status = lib.statusOf(f)
    Column(Modifier.clickable { onClick() }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.8f)
                .background(Brush.linearGradient(coverColors(f.name)), RoundedCornerShape(14.dp))
        ) {
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Surface(shape = RoundedCornerShape(50), color = NxPalette.Background.copy(alpha = 0.6f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        StatusDot(statusColor(status), 8.dp)
                        Text(
                            when (status) {
                                GameStatus.NOT_PORTED -> "未移植"
                                GameStatus.PORTING -> "${(port.progress * 100).toInt()}%"
                                GameStatus.DONE -> "已完成"
                            },
                            fontSize = 11.sp, color = statusColor(status),
                        )
                    }
                }
            }
            Text(
                FolderScanner.displayName(f).take(1).ifEmpty { "?" },
                fontSize = 42.sp, fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
            )
        }
        Text(
            FolderScanner.displayName(f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The empty folder: the way to choose one */
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
private fun ConfigPanel(lib: LibraryState, port: PortState, keys: String, envReady: Boolean,
                        f: File, actions: AppActions, onClose: () -> Unit) {
    BackHandler { onClose() }
    val status = lib.statusOf(f)
    var cfg by remember(f.absolutePath) { mutableStateOf(lib.configOf(f)) }
    val storageOk = actions.storageOk()
    val canPort = envReady && storageOk && !port.running && keys.isNotBlank() && status != GameStatus.PORTING
    val titleId = FolderScanner.titleIdIn(f.name)

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
                QuietButton("关闭", onClose)
            }
            Hint(f.absolutePath, size = 11.sp)

            Section("移植选项（跟着这个游戏）") {
                ConfigRow("触屏游戏（隐藏手柄）", cfg.touch) { cfg = cfg.copy(touch = it); lib.setConfig(f, cfg) }
                ConfigRow("60 帧", cfg.fps60) { cfg = cfg.copy(fps60 = it); lib.setConfig(f, cfg) }
                ConfigRow("实验性移植（还没适配的游戏）", cfg.experimental) { cfg = cfg.copy(experimental = it); lib.setConfig(f, cfg) }
            }

            Spacer(Modifier.weight(1f))

            when {
                status == GameStatus.PORTING -> Hint("正在移植中——进度在下面的活动条和构建页里")
                status == GameStatus.DONE -> {
                    val apk = lib.apkOf(f)
                    if (apk == null) {
                        Hint("产物 APK 不见了（输出目录里没找到）——状态回落为未移植")
                    } else {
                        Hint("产物：${apk.absolutePath}", size = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        CapsuleButton("安装 APK", { actions.onInstall(apk) }, modifier = Modifier.fillMaxWidth())
                    }
                }
                else -> {
                    if (!canPort) {
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
                    CapsuleButton("开始移植", {
                        actions.onStartPort(f, cfg)
                        onClose()
                    }, enabled = canPort, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
