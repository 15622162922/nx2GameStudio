package com.nx.android.ui.builds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.appstate.PortState
import com.nx.android.ui.AppActions
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.Section
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.components.rememberPageScroll
import com.nx.android.ui.elapsedSecs
import com.nx.android.ui.theme.NxPalette
import java.io.File

/**
 * The builds page: the port that runs now (its stage, its step, the log, the cancel), and the ones that ran
 * before (their results and their APKs). The page reads PortState, like the activity bar and the notification
 * do — the numbers cannot disagree.
 */
@Composable
fun BuildsScreen(port: PortState, actions: AppActions) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberPageScroll()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (port.phase != PortState.Phase.IDLE) CurrentPort(port, actions)
        if (port.history.isNotEmpty()) History(port, actions)
        if (port.phase == PortState.Phase.IDLE && port.history.isEmpty()) {
            Section("还没有移植") {
                Text("在游戏库里挑一个游戏，点「开始移植」——移植在这里跑，可以随时回来这一页看。",
                     style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** The port of now (or the last one): state chip, stage, progress, the log's tail, the cancel */
@Composable
private fun CurrentPort(port: PortState, actions: AppActions) {
    val p = port.phase
    val secs = elapsedSecs(port.startedAt)
    Section("移植") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PhaseChip(p)
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
        if (p == PortState.Phase.RUNNING || port.progress > 0f) {
            LinearProgressIndicator(
                progress = { port.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (p == PortState.Phase.RUNNING) NxPalette.Building else NxPalette.Ready,
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
        LogTail(port.log)
        if (p == PortState.Phase.RUNNING) {
            QuietButton("取消移植", { actions.onCancelPort() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The log's last lines (mono, the failures red) */
@Composable
fun LogTail(log: List<String>, height: androidx.compose.ui.unit.Dp = 160.dp) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(log.size) { scroll.scrollTo(log.size * 1000) }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 60.dp, max = height)
                .verticalScroll(scroll).padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val lines = log.takeLast(14)
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

/** The ports that ran before: the result, how long it took, and the APK's install for the done ones */
@Composable
private fun History(port: PortState, actions: AppActions) {
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
                if (r.phase == PortState.Phase.DONE && r.apk.isNotEmpty() && File(r.apk).isFile()) {
                    QuietButton("安装", { actions.onInstall(File(r.apk)) })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}
