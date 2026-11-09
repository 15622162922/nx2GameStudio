package com.nx.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.domain.ObStep
import com.nx.android.domain.OnboardingLogic
import com.nx.android.appstate.OnboardingState
import com.nx.android.ui.components.PickerMode
import com.nx.android.ui.components.PickerRequest
import java.io.File
import com.nx.android.ui.AppActions
import com.nx.android.ui.components.CapsuleButton
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.Section
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.theme.NxPalette

/**
 * The guided setup, five steps: the permission and the space, the environment's unpack, the keys, the games'
 * folder, done. The gates are OnboardingLogic's; this view only draws and sends intents.
 */
@Composable
fun OnboardingScreen(ob: OnboardingState, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val facts = ob.facts()
    val can = OnboardingLogic.canAdvance(ob.step, facts)

    // the back key walks the flow backwards, it does not leave the app
    BackHandler(enabled = ob.step > ObStep.WELCOME) { ob.back() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
           verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // the header: where the player is, and the way out for later
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stepTitle(ob.step), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("第 ${ob.step.ordinal + 1} 步，共 ${ObStep.entries.size - 1} 步",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { ob.skipForLater() }) { Text("稍后再说", color = NxPalette.Cream) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in ObStep.entries) {
                val i = s.ordinal
                Box(
                    Modifier.width(if (i == ob.step.ordinal) 22.dp else 8.dp).height(8.dp)
                        .background(
                            when {
                                i < ob.step.ordinal -> NxPalette.Ready
                                i == ob.step.ordinal -> NxPalette.Cream
                                else -> NxPalette.Unbuilt.copy(alpha = 0.4f)
                            },
                            RoundedCornerShape(50)
                        )
                )
            }
        }

        Box(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (ob.step) {
                    ObStep.WELCOME -> WelcomeStep(ob, actions)
                    ObStep.ENV -> EnvStep(ob)
                    ObStep.KEYS -> KeysStep(ob, actions, onRequest)
                    ObStep.GAMES -> GamesStep(ob, actions, onRequest)
                    ObStep.DONE -> DoneStep()
                }
            }
        }

        // the footer: back and the step's main action
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ob.step > ObStep.WELCOME) {
                QuietButton("返回", { ob.back() })
            }
            Spacer(Modifier.weight(1f))
            CapsuleButton(
                text = primaryLabel(ob.step, can),
                onClick = { if (ob.step == ObStep.DONE) ob.openLibrary() else ob.next() },
                enabled = can,
            )
        }
    }
}

private fun stepTitle(s: ObStep): String = when (s) {
    ObStep.WELCOME -> "欢迎来到 nx2android"
    ObStep.ENV -> "准备环境"
    ObStep.KEYS -> "prod.keys"
    ObStep.GAMES -> "游戏文件夹"
    ObStep.DONE -> "一切就绪"
}

private fun primaryLabel(s: ObStep, can: Boolean): String = when (s) {
    ObStep.WELCOME -> "开始"
    ObStep.ENV -> if (can) "继续" else "等待环境…"
    ObStep.KEYS -> if (can) "继续" else "等待校验…"
    ObStep.GAMES -> "继续"
    ObStep.DONE -> "进入游戏库"
}

// ---- the steps -----------------------------------------------------------------------------------------------

@Composable
private fun WelcomeStep(ob: OnboardingState, actions: AppActions) {
    val facts = ob.facts()
    Section("存储访问") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(if (facts.storageOk) NxPalette.Ready else NxPalette.Danger)
            Text(
                if (facts.storageOk) "已授予所有文件访问"
                else "读取游戏文件与写出移植产物需要「所有文件访问」权限",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (!facts.storageOk) QuietButton("去授权", { actions.onAskStorage() })
        }
        Hint("授权在系统的设置页完成，回来后这一步自动通过")
    }
    Section("磁盘空间") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(if (facts.freeGB >= facts.neededGB) NxPalette.Ready else NxPalette.Danger)
            Text(
                "剩余 ${facts.freeGB} GB　·　解压环境需要约 1.6 GB",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Hint(if (facts.freeGB < facts.neededGB) "空间不足：清一色删掉些东西再回来" else "空间足够，可以继续")
    }
}

@Composable
private fun EnvStep(ob: OnboardingState) {
    val ready = ob.facts().envReady
    Section("运行环境") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ob.unpacking) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
            } else {
                StatusDot(if (ready) NxPalette.Ready else NxPalette.Building)
            }
            Text(
                when {
                    ready -> "环境就绪"
                    ob.unpacking -> "正在解压：${ob.unpackStep}"
                    else -> "还没有环境（rootfs、编译工具和 .NET SDK，约 1.6 GB）"
                },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            if (!ready && !ob.unpacking) QuietButton("准备环境", { ob.unpack() })
        }
        if (ob.unpacking) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(5.dp), color = NxPalette.Building)
        }
        if (ob.unpackError.isNotEmpty()) {
            Text(
                "准备失败：${ob.unpackError}",
                color = NxPalette.Danger,
                style = MaterialTheme.typography.bodySmall,
            )
            Hint("点「准备环境」重试；失败的原因也在日志里")
        }
    }
    Hint("环境解压一次就好：之后更新 App 只带新的小工具，不再重复这一步")
}

@Composable
private fun KeysStep(ob: OnboardingState, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    Section("prod.keys（自己的 Switch 导出的）") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(
                when {
                    ob.keysOk -> NxPalette.Ready
                    ob.keysChecking -> NxPalette.Building
                    else -> NxPalette.Danger
                }
            )
            Column(Modifier.weight(1f)) {
                Text(
                    ob.keysPath.ifEmpty { "还没有选择 keys 文件" },
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                )
                if (ob.keysDetail.isNotEmpty()) {
                    Hint(ob.keysDetail)
                }
            }
            if (ob.keysChecking) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
            } else {
                QuietButton("选择文件", onClick = {
                    onRequest(PickerRequest(PickerMode.FILE, setOf("keys"), null, "选择 prod.keys",
                        onPicked = { f -> ob.chooseKeys(f.path) }))
                })
            }
        }
        Hint("常见位置（/sdcard/switch/）已有的 keys 会自动填上并校验")
    }
    if (!ob.keysOk && !ob.keysChecking) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ob.keysSkipped, onCheckedChange = { if (it) ob.skipKeys() else ob.unskipKeys() })
            Text("暂时跳过（发起移植时会再提醒）", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GamesStep(ob: OnboardingState, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    Section("游戏文件夹") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(if (ob.folder.isNotBlank()) NxPalette.Ready else NxPalette.Unbuilt)
            Text(
                ob.folder.ifEmpty { "还没有选择文件夹" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f), maxLines = 2,
            )
            QuietButton("选择文件夹", onClick = {
                onRequest(PickerRequest(PickerMode.DIR, emptySet(),
                    File(ob.folder.ifBlank { "/storage" }), "选择游戏文件夹",
                    onPicked = { dir -> ob.chooseFolder(dir.path) }))
            })
        }
        if (ob.folder.isNotBlank()) {
            Hint(if (ob.games.isEmpty()) "这里没有找到游戏文件（NSP / XCI / NSZ / XCZ）——可以直接继续，游戏库里随时能换文件夹"
                 else "找到 ${ob.games.size} 个游戏文件；同目录的更新和 DLC 会在移植时自动带上")
            if (ob.games.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (g in ob.games.take(30)) {
                        Text(
                            "· ${g.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                    }
                    if (ob.games.size > 30) Hint("…以及另外 ${ob.games.size - 30} 个")
                }
            }
        }
    }
}

@Composable
private fun DoneStep() {
    Section("完成了") {
        Text(
            "环境就绪、钥匙校验过、游戏也找齐了。\n" +
                "接下来在游戏库里挑一个游戏发起移植，移植在后台跑，跑完在库里就能玩。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Hint("有跳过的步骤也不要紧：设置里随时能补，库里的入口会提醒缺什么")
    }
}
