package com.nx.android.feature.filepicker

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Note
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.core.ui.components.CapsuleButton
import com.nx.android.core.ui.components.QuietButton
import com.nx.android.core.ui.theme.NxPalette
import java.io.File

/** 选择模式：选一个文件，或选一个目录 */
enum class PickerMode { FILE, DIR }

/** 一次文件/目录选择请求：模式、后缀白名单、起始目录、标题与结果回调 */
data class PickerRequest(
    val mode: PickerMode,
    val extensions: Set<String>,
    val initial: File?,
    val title: String,
    val onPicked: (File) -> Unit,
)

/** One tile of the grid: a folder the user may enter, or a file the user may pick. */
private data class PickerEntry(val file: File, val isDirectory: Boolean, val actionable: Boolean)

/**
 * 内置文件管理器（移植自 nixport 的 FilePickerScreen）：自绘的文件浏览界面，不依赖
 * 系统文件选择器组件——PRC ROM 的文件栈对 SAF 支持参差，这里用 java.io 直读（全文件访问已在手）。
 *
 * 存储卷（内部存储 / SD 卡）从顶栏右侧的下拉切换——比 DocumentsUI 的侧栏少一层导航。
 * 当前位置在确认按钮返回：浏览中它是一个目录；选定文件后它就是那个文件，网格继续显示
 * 所在目录，用户随时可以改主意。
 */
@Composable
fun FilePickerScreen(req: PickerRequest, volumes: List<Pair<String, File>>,
                     onPicked: (File) -> Unit, onCancel: () -> Unit) {
    var current by remember(req, volumes) { mutableStateOf(nearestExisting(req.initial, volumes)) }
    val currentIsDir = remember(current) { current.isDirectory }
    // 选中的文件仍留在它所在目录的列表里，用户可以改主意
    val browsed = remember(current, currentIsDir) {
        if (currentIsDir) current else current.parentFile ?: current
    }
    val children = remember(browsed) { browsed.listFiles()?.toList().orEmpty() }
    val entries = remember(children, req.mode, req.extensions) { buildEntries(children, req.mode, req.extensions) }
    // 当前所在的卷；在卷根时上一级不可用（卷切换走顶栏）
    val currentVolume = volumes.firstOrNull {
        current.path == it.second.path || current.path.startsWith(it.second.path + "/")
    }
    val atVolumeRoot = currentVolume != null && current.path == currentVolume.second.path
    val canConfirm = if (req.mode == PickerMode.FILE) !currentIsDir else currentIsDir
    val canGoUp = !atVolumeRoot

    BackHandler { onCancel() }

    Column(Modifier.fillMaxSize().background(NxPalette.Background)) {
        PickerTopBar(req.title, current, volumes, onUp = {
            current = current.parentFile ?: currentVolume?.second ?: current
        }, onVolume = { current = it })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                children.isEmpty() -> PickerEmpty(stringResource(R.string.picker_no_files))
                entries.isEmpty() -> PickerEmpty(stringResource(R.string.picker_no_game_files))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(entries) { entry ->
                        PickerTile(
                            label = entry.file.name,
                            directory = entry.isDirectory,
                            actionable = entry.actionable,
                            picked = entry.file == current && !currentIsDir,
                        ) { current = entry.file }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            QuietButton(stringResource(R.string.common_cancel), onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp))
            CapsuleButton(
                if (req.mode == PickerMode.FILE) stringResource(R.string.picker_pick_file) else stringResource(R.string.picker_pick_folder),
                onClick = { onPicked(current) },
                enabled = canConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** Nothing to show here: say so instead of an empty grid. */
@Composable
private fun PickerEmpty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = NxPalette.Unbuilt)
    }
}

/** 顶栏：上一级、标题与当前路径；右侧是存储卷切换（多于一卷才出现） */
@Composable
private fun PickerTopBar(title: String, current: File, volumes: List<Pair<String, File>>,
                          onUp: () -> Unit, onVolume: (File) -> Unit) {
    val currentVolume = volumes.firstOrNull {
        current.path == it.second.path || current.path.startsWith(it.second.path + "/")
    }
    var volumeMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(NxPalette.Surface)
                .clickable(enabled = !atVolumeRoot(current, volumes), onClick = onUp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.picker_up), Modifier.size(22.dp),
                tint = if (!atVolumeRoot(current, volumes)) MaterialTheme.colorScheme.onBackground
                else NxPalette.Unbuilt)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Text(current.path, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (volumes.size > 1) {
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(NxPalette.Surface)
                        .clickable { volumeMenu = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        currentVolume?.first ?: stringResource(R.string.picker_section_storage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.picker_switch_volume), Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                DropdownMenu(expanded = volumeMenu, onDismissRequest = { volumeMenu = false }) {
                    volumes.forEach { (label, root) ->
                        val on = current.path == root.path || current.path.startsWith(root.path + "/")
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (on) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = NxPalette.Ready)
                                    Text(label, color = if (on) NxPalette.Cream else MaterialTheme.colorScheme.onBackground)
                                }
                            },
                            onClick = {
                                volumeMenu = false
                                onVolume(root)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun atVolumeRoot(current: File, volumes: List<Pair<String, File>>): Boolean =
    volumes.any { current.path == it.second.path }

/** One grid tile: the icon and the name. The whole tile is the target. */
@Composable
private fun PickerTile(
    label: String,
    directory: Boolean,
    actionable: Boolean,
    picked: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
            .background(NxPalette.Surface)
            .clickable(enabled = actionable, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(pickerIcon(directory), null, Modifier.size(20.dp),
                tint = if (actionable) NxPalette.Cream else NxPalette.Unbuilt)
            if (picked) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = NxPalette.Ready)
            }
        }
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = if (actionable) MaterialTheme.colorScheme.onBackground else NxPalette.Unbuilt,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun pickerIcon(directory: Boolean): ImageVector =
    if (directory) Icons.Filled.Folder else Icons.Filled.Note

/**
 * The entries of one directory: folders always, files when they may be picked. A
 * directory listing shows its files too, so the user sees what is in the folder they
 * are about to choose. Folders come first, then the files, and the dot-prefixed ones
 * last.
 */
private fun buildEntries(
    children: List<File>,
    mode: PickerMode,
    extensions: Set<String>,
): List<PickerEntry> {
    val entries = children.mapNotNull { f ->
        val isDirectory = f.isDirectory
        when {
            isDirectory -> PickerEntry(f, true, actionable = true)
            mode == PickerMode.FILE && matchesExtension(f.name, extensions) ->
                PickerEntry(f, false, actionable = true)
            mode == PickerMode.FILE -> null
            else -> PickerEntry(f, false, actionable = false)
        }
    }
    return entries.sortedWith(
        compareBy({ it.file.name.startsWith(".") }, { !it.isDirectory }, { it.file.name.lowercase() })
    )
}

/**
 * Whether a file may be picked: its name ends with the wanted extension. A compound
 * extension works too ("tar.gz" matches "game.tar.gz").
 */
private fun matchesExtension(name: String, extensions: Set<String>): Boolean {
    if (extensions.isEmpty()) return true
    val lower = name.lowercase()
    return extensions.any { lower.endsWith("." + it.trim().removePrefix(".").lowercase()) }
}

/** 起始目录不存在时，退到最近可用的祖先；一路都不可用就从第一个卷开始 */
internal fun nearestExisting(dir: File?, volumes: List<Pair<String, File>>): File {
    val roots = volumes.map { it.second }
    var f = dir
    while (f != null && !f.isDirectory) f = f.parentFile
    if (f != null && roots.any { f.path == it.path || f.path.startsWith(it.path + "/") }) return f
    return roots.firstOrNull() ?: f ?: STORAGE_ROOT_FALLBACK
}

private val STORAGE_ROOT_FALLBACK = File("/storage/emulated/0")
