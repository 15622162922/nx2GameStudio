package com.nx.android.feature.library

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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.VideogameAsset
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.appstate.LibraryState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nx.android.appstate.PortState
import com.nx.android.appstate.PlayerState
import com.nx.android.domain.drivers.Driver
import com.nx.android.app.AppActions
import com.nx.android.feature.filepicker.PickerRequest
import com.nx.android.feature.library.LibraryViewModel
import com.nx.android.data.AppLocale
import com.nx.android.data.GameIcon
import com.nx.android.data.GameLog
import com.nx.android.data.Player
import com.nx.android.data.SaveBackup
import com.nx.android.data.SaveShare
import com.nx.android.data.StorageVolumes
import com.nx.android.domain.GameIdentity
import com.nx.android.domain.GAME_FILE_EXTENSIONS
import com.nx.android.domain.GameCleanup
import com.nx.android.domain.GameEntry
import com.nx.android.domain.GameStatus
import com.nx.android.domain.LibraryLogic
import com.nx.android.domain.PackMover
import com.nx.android.domain.Ported
import com.nx.android.core.ui.LocalAppLocale
import com.nx.android.core.ui.components.CapsuleButton
import com.nx.android.core.ui.components.Hint
import com.nx.android.feature.filepicker.PickerMode
import com.nx.android.core.ui.components.PickRow
import com.nx.android.core.ui.components.QuietButton
import com.nx.android.core.ui.components.StatusDot
import com.nx.android.core.ui.components.rememberGameIcon
import com.nx.android.core.ui.components.sizeText
import com.nx.android.core.ui.failureText
import com.nx.android.core.ui.theme.NxPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
fun LibraryScreen(lib: LibraryState, port: PortState, player: PlayerState, installedDrivers: List<Driver>, actions: AppActions,
                  onGoBuilds: () -> Unit, onRequest: (PickerRequest) -> Unit, uiState: LibraryUiState? = null,
                  viewModel: LibraryViewModel? = null) {

    // a port ends: the cards' statuses are re-read
    LaunchedEffect(port.phase) { viewModel?.refresh() ?: lib.rescan() }

    // 构建中：工作区是构建开始之后才有的，过一会儿再看一次，那张「构建中」的卡才会出现
    LaunchedEffect(port.running) {
        while (port.running) {
            viewModel?.refresh() ?: lib.rescan()
            delay(3000)
        }
    }

    val visible = uiState?.games ?: lib.visible()
    val heroIndex = uiState?.heroIndex ?: lib.heroIndex
    LaunchedEffect(visible.size) { viewModel?.selectHero(heroIndex, visible.size) ?: lib.selectHero(heroIndex, visible.size) }

    RowView(lib, port, player, visible, heroIndex, installedDrivers, actions, onGoBuilds, onRequest, viewModel)

    // 配置面板不在这里画：它要盖住顶栏，所以由页面的根（MainScreen）在最上层画（见 ui/App.kt）
}

@Composable
fun LibraryRoute(viewModel: LibraryViewModel, port: PortState, player: PlayerState,
                 installedDrivers: List<Driver>, actions: AppActions,
                 onGoBuilds: () -> Unit, onRequest: (PickerRequest) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(viewModel.library, port, player, installedDrivers, actions, onGoBuilds, onRequest, uiState, viewModel)
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

/**
 * 实验性移植的标记：档案还没在 Android 上调通（清单里的 status 是 experimental），玩家该知道
 * 这个游戏随时可能卡在某条没走过的路上 —— 而不是以为它坏了。
 */
@Composable
private fun experimentalTag(ported: Ported?): String? =
    if (ported?.status == "experimental") stringResource(R.string.lib_experimental) else null

/** 旧版宿主编的数据包：要用当前版本重新移植（宿主的前端变了，老的宿主没有新的入口） */
@Composable
private fun reportTag(ported: Ported?): String? =
    if (ported?.needsRePort == true) stringResource(R.string.lib_needs_report) else null

/** 清单里的显示版本（游戏自己的版本号，如 1.1.1）：老的数据包没有这一项，就不显示 */
private fun versionTag(ported: Ported?): String? =
    ported?.display?.trim()?.takeIf { it.isNotEmpty() }

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

/** 降采样读一张图，主线程之外（capyps5 rememberImage 的手法）——见 `ui/components/GameIcons.kt` */

// ---- 行视图（hero） -------------------------------------------------------------------------------------------

@Composable
private fun RowView(lib: LibraryState, port: PortState, player: PlayerState,
                    visible: List<GameEntry>, heroIndex: Int, installedDrivers: List<Driver>, actions: AppActions, onGoBuilds: () -> Unit,
                    onRequest: (PickerRequest) -> Unit, viewModel: LibraryViewModel?) {
    val sel = visible.getOrNull(heroIndex)

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (visible.isEmpty()) {
            // 空态：架子上的几个空位、一句提示、支持的格式。没有按钮——加游戏的路只有构建页那一条
            // 竖着放不下（横屏）时也能滚
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyShelf()
                    Spacer(Modifier.height(26.dp))
                    Text(
                        stringResource(R.string.lib_empty_title),
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.lib_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 340.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (f in EMPTY_FORMATS) FormatChip(f)
                    }
                }
            }
            return@Column
        }
        Spacer(Modifier.height(10.dp))
        HeroRow(lib, port, visible, heroIndex) { i ->
            if (i == heroIndex) viewModel?.selectGame(visible.getOrNull(i)) ?: run { lib.selected = visible.getOrNull(i) }
            else viewModel?.selectHero(i, visible.size) ?: lib.selectHero(i, visible.size)
        }
        Spacer(Modifier.weight(1f))
        sel?.let { HeroDetails(lib, port, player, it, actions, onGoBuilds, onRequest, viewModel) }
        Spacer(Modifier.height(18.dp))
    }
}

/** 选中游戏的全屏呈现：已完成的游戏用产物 APK 里的图标实图，其余用名字的渐变与首字母水印 */
@Composable
internal fun HeroBackground(sel: GameEntry?) {
    if (sel == null) return
    val icon by rememberGameIcon(sel.pack?.icon?.absolutePath, 1280)

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
                    Brush.linearGradient(coverColors(sel.label).map { it.copy(alpha = 0.45f) })
                )
            )
            Text(
                sel.label.take(1).ifEmpty { "?" },
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

/**
 * 空态的插画：架子上几个还没放上游戏的空位。
 *
 * 中间那张大一些、上面有一个手柄，两边两张小一点、暗一点、歪一点——看着像架子上的空位，
 * 而不是一张贴纸。底下垫一圈极淡的品牌橙光晕，纯黑的底不至于发闷。
 */
@Composable
private fun EmptyShelf() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(230.dp)) {
        Box(
            Modifier.size(210.dp).background(
                Brush.radialGradient(listOf(NxPalette.Brand.copy(alpha = 0.15f), Color.Transparent))
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmptyCard(62.dp, -11f, 0.45f)
            EmptyCard(94.dp, 0f, 1f, icon = true)
            EmptyCard(62.dp, 11f, 0.45f)
        }
    }
}

/** 空位卡：和真的游戏瓦片一个形状（14dp 圆角），只是没有封面，只有暗底与细描边 */
@Composable
private fun EmptyCard(size: Dp, tilt: Float, alpha: Float, icon: Boolean = false) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(size)
            .rotate(tilt)
            .alpha(alpha)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF26262A), Color(0xFF16161A))))
            .border(1.dp, NxPalette.Unbuilt.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            Icon(Icons.Outlined.VideogameAsset, null, Modifier.size(34.dp),
                 tint = NxPalette.Cream.copy(alpha = 0.30f))
        }
    }
}

/** 空态里的格式徽章：文件后缀是技术记号，两种语言里都是这四个 */
private val EMPTY_FORMATS = listOf("NSP", "XCI", "NSZ", "XCZ")

@Composable
private fun FormatChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** 横向瓦片行：选中的放大（88 → 128），状态点跟着瓦片走 */
@Composable
private fun HeroRow(lib: LibraryState, port: PortState, visible: List<GameEntry>, heroIndex: Int, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState()
    // 选中项移到第三格之后行才滚动，选中的前后都留在视野里
    LaunchedEffect(heroIndex) {
        if (visible.isNotEmpty()) state.animateScrollToItem((heroIndex - 2).coerceIn(0, visible.size - 1))
    }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 80.dp),
        modifier = Modifier.fillMaxWidth().height(132.dp),
    ) {
        itemsIndexed(visible, key = { _, e -> e.key }) { index, e ->
            val size by androidx.compose.animation.core.animateDpAsState(
                if (index == heroIndex) 128.dp else 88.dp, label = "tileSize"
            )
            HeroTile(lib, port, e, index == lib.heroIndex, Modifier.size(size)) { onSelect(index) }
        }
    }
}

@Composable
private fun HeroTile(lib: LibraryState, port: PortState, e: GameEntry, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val status = lib.statusOf(e)
    val shape = RoundedCornerShape(14.dp)
    val icon by rememberGameIcon(e.pack?.icon?.absolutePath, 256)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(coverColors(e.label)))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else NxPalette.Unbuilt.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = icon
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap, e.label, Modifier.padding(2.dp).fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                e.label.take(1).ifEmpty { "?" },
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
                        e: GameEntry, actions: AppActions, onGoBuilds: () -> Unit,
                        onRequest: (PickerRequest) -> Unit, viewModel: LibraryViewModel?) {
    val locale = LocalAppLocale.current
    val status = lib.statusOf(e)
    val ported = e.pack
    // Title ID：游戏的身份（还没构建过的还没有）
    val titleId = e.titleId
    // 单游戏：别的游戏在玩时，这个游戏的「▶ 运行」置灰并说明
    val locked = status == GameStatus.DONE && player.running && player.titleId != (ported?.titleId ?: "")

    /**
     * 开始构建：源文件在就直接带过去；不在了（玩家把它挪走腾空间去了）就请他重新选一个。
     */
    fun build() {
        val src = e.source
        if (src != null) {
            viewModel?.setBuildTarget(src) ?: run { lib.buildsTarget = src }
            onGoBuilds()
        } else {
            onRequest(PickerRequest(PickerMode.FILE, GAME_FILE_EXTENSIONS, File("/storage"),
                locale.str(R.string.builds_choose_game_file),
                onPicked = { f ->
                    if (LibraryLogic.sourceMatchesTitle(f.name, e.titleId)) {
                        viewModel?.setBuildTarget(f) ?: run { lib.buildsTarget = f }; onGoBuilds()
                    } else {
                        actions.onToast(locale.str(R.string.lib_source_mismatch))
                    }
                }))
        }
    }

    Column(Modifier.widthIn(max = 640.dp)) {
        Text(
            e.label,
            style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(titleId, versionTag(ported), statusLabel(status) +
                if (status == GameStatus.PORTING) " ${(port.progress * 100).toInt()}%" else "",
                experimentalTag(ported), reportTag(ported)).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // 主按钮：未移植 ▶ 构建（携带游戏与配置跳到构建页，NX2Apple 的 configureBuild），移植中变「查看构建」，
            // 已完成 ▶ 运行（App 内直接进游戏）；旧版宿主编的包要重新移植，主按钮变成「重新移植」
            val building = status == GameStatus.PORTING
            val running = status == GameStatus.DONE
            val stale = ported?.needsRePort == true
            Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (locked) NxPalette.Unbuilt else NxPalette.Cream)
                        .clickable(enabled = !locked) {
                            when {
                                // 正在构建：去构建页看进度（第一次构建还没有源文件的记忆，就带着正在跑的那个）
                                building -> {
                                    val target = e.source ?: port.gameFile.takeIf { port.running }?.let { File(it) }
                                    viewModel?.setBuildTarget(target) ?: run { lib.buildsTarget = target }
                                    onGoBuilds()
                                }
                                running && !stale -> actions.onRun(ported!!.pack, e.label)
                                else -> build()
                            }
                        }
                        .padding(horizontal = 30.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp),
                         tint = if (locked) NxPalette.Background else Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            building -> stringResource(R.string.lib_view_builds)
                            stale -> stringResource(R.string.lib_report_again)
                            running -> stringResource(R.string.set_run_onboarding)
                            else -> stringResource(R.string.common_build)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (locked) NxPalette.Background else Color.Black,
                    )
            }
            // 配置圆钮
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
                    .clickable { viewModel?.selectGame(e) ?: run { lib.selected = e } }.padding(11.dp),
            ) {
                Icon(Icons.Filled.Tune, if (running) stringResource(R.string.lib_run_config) else stringResource(R.string.lib_port_config), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        if (locked) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.lib_close_current_game, player.name), style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- 移植配置面板（右侧全高滑入） --------------------------------------------------------------------------------

/**
 * 抽屉里的两个破坏性动作：清构建缓存（游戏照常能玩）与删游戏（连存档一起删，游戏出库）。
 */
