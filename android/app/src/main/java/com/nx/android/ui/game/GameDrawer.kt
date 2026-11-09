package com.nx.android.ui.game

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.data.FrameGenKnobs
import com.nx.android.data.FrameGenSettings
import com.nx.android.game.Native
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.theme.GlassSheet
import com.nx.android.ui.theme.NxPalette
import kotlinx.coroutines.delay

/** 抽屉的两个标签页（存档的导出与导入在库页的抽屉里，这里不再有） */
enum class DrawerTab(@StringRes val label: Int) {
    FRAMEGEN(R.string.drawer_tab_framegen), DISPLAY(R.string.drawer_tab_display)
}

/**
 * 游戏内抽屉（:play 进程自己的 UI）：右缘把手或返回唤出，玻璃卡样式与应用一致，两个标签页 + 底部动作行。
 * 帧生成即改即生效（JNI）；画面与操作直控宿主的菜单状态（手柄、不透明度、震动、FPS 计数、掌机/底座、
 * 布局编辑器）。存档不在这里——导出与导入都在库页选中游戏后的抽屉里（下次启动游戏时生效）；
 * 宿主自己的面板（文本输入、布局编辑器、存档选择）画在游戏画面上，所以打开它们之前抽屉先收起。
 * Back 不经过这里（GameActivity 分流）。
 */
@Composable
fun GameDrawer(
    paused: Boolean,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    settings: FrameGenSettings?,
    padShown: () -> Boolean,
    onPadShown: (Boolean) -> Unit,
    padOpacity: () -> Int,
    onPadOpacity: (Int) -> Unit,
    onPauseResume: () -> Unit,
    onExit: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        // 右缘把手：半透明，往下一个屏幕高度的四分之一
        Box(
            Modifier.align(Alignment.CenterEnd).padding(top = 120.dp)
                .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                .background(Color.White.copy(alpha = 0.28f))
                .clickable { onOpenChange(true) }.padding(horizontal = 7.dp, vertical = 34.dp)
        ) { Text("‹", color = Color.Black.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Bold) }

        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { onOpenChange(false) }) {
                var status by remember { mutableStateOf("-1 not tried") }
                LaunchedEffect(open) {   // 打开期间 2 Hz 轮询宿主的状态串
                    while (open) {
                        runCatching { status = Native.frameGenStatus() }
                        delay(500)
                    }
                }
                Column(
                    Modifier.align(Alignment.CenterEnd).width(420.dp).fillMaxHeight().padding(vertical = 16.dp)
                        // 面板自己消费点击：点在行与行之间（或纯文本上）不至于冒泡到遮罩把抽屉关掉
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    GlassSheet(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
                            var tab by remember { mutableIntStateOf(0) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((i, t) in DrawerTab.entries.withIndex()) {
                                    val selected = tab == i
                                    Box(
                                        Modifier.clip(RoundedCornerShape(50))
                                            .background(if (selected) NxPalette.Cream else Color.White.copy(alpha = 0.16f))
                                            .clickable { tab = i }.padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) { Text(stringResource(t.label), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            // 内容页：滚动，占据动作行之上的全部空间（动作行在本列底部，不可能被挤出）
                            Column(
                                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                when (DrawerTab.entries[tab]) {
                                    DrawerTab.FRAMEGEN -> if (settings == null) HintRow(stringResource(R.string.drawer_no_framegen))
                                        else FrameGenRows(settings, status)
                                    DrawerTab.DISPLAY -> DisplayRows(padShown, onPadShown, padOpacity, onPadOpacity) { onOpenChange(false) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            // 底部动作行：三个标签页共用
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                QuietButton(if (paused) stringResource(R.string.drawer_resume) else stringResource(R.string.drawer_resume), onClick = onPauseResume, modifier = Modifier.weight(1f))
                                QuietButton(stringResource(R.string.drawer_exit), onClick = onExit, modifier = Modifier.weight(1f))
                                QuietButton(stringResource(R.string.drawer_back_to_game), onClick = { onOpenChange(false) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintRow(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun pushGen(settings: FrameGenSettings, knobs: FrameGenKnobs) {
    settings.save(knobs)
    if (knobs.on) Native.frameGenSet(true, knobs.mult, knobs.target, knobs.flow)
}

/** 帧生成页：开关、倍率、目标帧率、光流质量、DLL 与实时状态（即改即生效） */
@Composable
private fun FrameGenRows(settings: FrameGenSettings, status: String) {
    var knobs by remember { mutableStateOf(settings.load()) }
    val state = status.substringBefore(' ').toIntOrNull() ?: -1
    val reason = status.substringAfter(' ', "")

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.drawer_tab_framegen), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (knobs.on) NxPalette.Ready.copy(alpha = 0.25f) else NxPalette.Unbuilt.copy(alpha = 0.25f))
                .clickable { knobs = knobs.copy(on = !knobs.on); pushGen(settings, knobs) }
                .padding(horizontal = 16.dp, vertical = 7.dp),
        ) { Text(if (knobs.on) stringResource(R.string.common_on) else stringResource(R.string.common_off), color = if (knobs.on) NxPalette.Ready else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
    }
    // 状态行：宿主的判定是唯一真源（设备能力、DLL、显示节奏都在它那边）
    Text(
        when (state) {
            1 -> stringResource(R.string.drawer_generating)
            2 -> reason.ifEmpty { stringResource(R.string.drawer_waiting_stable) }
            0 -> stringResource(R.string.drawer_off)
            -1 -> reason.ifEmpty { stringResource(R.string.drawer_device_unsupported) }
            else -> stringResource(R.string.drawer_build_without_fg)
        } + if (state == -1 && reason.contains("Lossless.dll")) stringResource(R.string.drawer_choose_dll_hint) else "",
        style = MaterialTheme.typography.bodySmall,
        color = when (state) { 1 -> NxPalette.Ready; 2 -> NxPalette.Building; -1 -> NxPalette.Danger; else -> MaterialTheme.colorScheme.onSurfaceVariant },
    )
    if (state >= 0) {
        // 选项的词先取好：回调里不能取词
        val targets = targetLabels()
        val auto = stringResource(R.string.drawer_auto)
        OptionRow(stringResource(R.string.drawer_multiplier), listOf("2x", "3x", "4x"), "${knobs.mult}x") { name ->
            knobs = knobs.copy(mult = name.removeSuffix("x").toIntOrNull() ?: 2); pushGen(settings, knobs)
        }
        OptionRow(stringResource(R.string.drawer_target_fps), targets, targetLabel(knobs.target)) { name ->
            knobs = knobs.copy(target = targets.indexOf(name).let { if (it >= 0) FrameGenKnobs.TARGETS[it] else 0 }); pushGen(settings, knobs)
        }
        OptionRow(stringResource(R.string.drawer_flow_quality), listOf(auto, "25%", "50%", "75%", "100%"), flowLabel(knobs.flow)) { name ->
            knobs = knobs.copy(flow = if (name == auto) 0 else name.removeSuffix("%").toIntOrNull() ?: 0); pushGen(settings, knobs)
        }
        Text(stringResource(R.string.drawer_fg_hint),
             style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 画面与操作页：宿主菜单的行，直控（即改即生效并持久保存在包的 settings.txt） */
@Composable
private fun DisplayRows(
    padShown: () -> Boolean, onPadShown: (Boolean) -> Unit,
    padOpacity: () -> Int, onPadOpacity: (Int) -> Unit,
    onCloseDrawer: () -> Unit,
) {
    var shown by remember { mutableStateOf(padShown()) }
    var opacity by remember { mutableStateOf(padOpacity()) }
    var rumble by remember { mutableStateOf(Native.menuGet(2) != 0) }
    var fps by remember { mutableStateOf(Native.menuGet(3) != 0) }
    var docked by remember { mutableStateOf(Native.menuGet(4) != 0) }

    SwitchRow(stringResource(R.string.drawer_virtual_pad), shown) { shown = it; onPadShown(it) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.drawer_pad_opacity), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$opacity%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = opacity.toFloat(), valueRange = 10f..100f,
               onValueChange = { opacity = it.toInt(); onPadOpacity(opacity) })
    }
    SwitchRow(stringResource(R.string.drawer_rumble), rumble) { rumble = it; Native.menuSet(2, if (it) 1.0 else 0.0) }
    SwitchRow(stringResource(R.string.drawer_fps_counter), fps) { fps = it; Native.menuSet(3, if (it) 1.0 else 0.0) }
    SwitchRow(stringResource(R.string.drawer_docked), docked) { docked = it; Native.menuSet(4, if (it) 1.0 else 0.0) }
    // 布局编辑器是宿主画的，画在游戏画面上：抽屉先收起，否则它盖着编辑器
    QuietButton(stringResource(R.string.drawer_layout_editor), onClick = { onCloseDrawer(); Native.menuEditOpen() }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (checked) NxPalette.Ready.copy(alpha = 0.25f) else NxPalette.Unbuilt.copy(alpha = 0.25f))
                .clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 5.dp),
        ) { Text(if (checked) stringResource(R.string.common_on) else stringResource(R.string.common_off), fontSize = 12.sp, color = if (checked) NxPalette.Ready else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
    }
}

private val TARGET_LABELS = listOf("60", "90", "120")

@Composable
private fun targetLabels() = listOf(stringResource(R.string.drawer_follow_game)) + TARGET_LABELS

@Composable
private fun targetLabel(t: Int) =
    targetLabels().getOrNull(FrameGenKnobs.TARGETS.indexOf(t)) ?: stringResource(R.string.drawer_follow_game)

@Composable
private fun flowLabel(f: Int) = if (f == 0) stringResource(R.string.drawer_auto) else "$f%"

@Composable
private fun OptionRow(label: String, options: List<String>, current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            // 与库页的字段同一做法：按钮定宽，箭头钉在右端，几行的箭头排成一列
            QuietButton(current, onClick = { open = !open },
                modifier = Modifier.width(140.dp),
                trailing = { Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp)) })
            androidx.compose.material3.DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.width(140.dp),
            ) {
                for (o in options.distinct()) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(o, fontWeight = if (o == current) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { open = false; onPick(o) },
                    )
                }
            }
        }
    }
}
