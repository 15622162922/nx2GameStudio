package com.nx.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The screen: Jetpack Compose (Material 3).
 *
 * The porter is a command-line program and its output is the screen; the parts around it are the environment
 * (the rootfs and the tools), the paths (the keys, the output folder, the game file) and the log.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(win: MainActivity) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("nx2android", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EnvironmentCard(win)
            PathsCard(win)
            OptionsCard(win)
            ActionsCard(win)
            ResultCard(win)
            LogCard(win)
            Spacer(Modifier.height(24.dp))
        }
        WorkingOverlay(win)
    }
}

/**
 * The whole screen is covered while the app works: the port takes a long time, and the state has to be in
 * sight the whole time (the stage, the time it takes and what the tool says).
 */
@Composable
private fun WorkingOverlay(win: MainActivity) {
    if (!win.porting && !win.unpacking) return
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.92f).padding(24.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(modifier = Modifier.width(26.dp).height(26.dp), strokeWidth = 3.dp)
                    Column {
                        Text(
                            if (win.unpacking) "正在准备环境" else "正在移植",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (win.unpacking) "rootfs、编译工具和 .NET SDK" else "别关掉这个页面",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (win.stageText.isNotEmpty()) {
                    Text(win.stageText, style = MaterialTheme.typography.bodyMedium)
                }
                if (win.progress > 0f) {
                    LinearProgressIndicator(progress = { win.progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
                }
                HorizontalDivider()
                // what the tool says right now
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column {
                        val lines = win.log.takeLast(14)
                        if (lines.isEmpty()) {
                            Text("还没有输出", style = MaterialTheme.typography.bodySmall)
                        } else {
                            lines.forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = if (line.startsWith("[RED]")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    ),
                                )
                            }
                        }
                    }
                }
                if (win.porting) {
                    OutlinedButton(onClick = { win.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("取消移植") }
                }
            }
        }
    }
}

/** A titled card: the screen is a few sections, each one a card */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** The environment: the guest programs, the tools and the storage */
@Composable
private fun EnvironmentCard(win: MainActivity) {
    Section("环境") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (win.unpacking) {
                CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (win.envReady) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        if (win.envReady) " 就绪 " else " 未就绪 ",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(win.status, style = MaterialTheme.typography.bodyMedium)
        }
        if (!win.storageOk) {
            FilledTonalButton(onClick = { win.askStorage() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Usb, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("授予存储访问权限（读取 SD 卡）")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { win.unpack() }, enabled = !win.unpacking, modifier = Modifier.weight(1f)) {
                Text("准备环境")
            }
            OutlinedButton(onClick = { win.resetEnv() }, enabled = !win.unpacking, modifier = Modifier.weight(1f)) {
                Text("重置环境")
            }
        }
        Text("第一次要解压约 1.6 GB（rootfs、编译工具和 .NET SDK）", style = MaterialTheme.typography.bodySmall)
    }
}

/** The paths: the keys, where the port goes and the game itself */
@Composable
private fun PathsCard(win: MainActivity) {
    Section("游戏") {
        PathField("prod.keys（自己的 Switch 导出的）", win.keys, { win.keys = it }, { win.pickKeys() })
        PathField("输出目录", win.out, { win.out = it }, { win.pickOutput() })
        PathField("游戏文件（NSP / XCI / NSZ / XCZ）", win.game, { win.game = it }, { win.pickGame() },
            placeholder = "选游戏文件；同目录的更新和 DLC 会自动带上")
        PathField("Vulkan 驱动文件夹", win.driverDir, { win.driverDir = it }, { win.pickDriverDir() },
            placeholder = "里面的 .so 都会被打进游戏，游戏里可以选")
    }
}

/** A path with a button that opens the picker */
@Composable
private fun PathField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    onBrowse: () -> Unit,
    placeholder: String = "",
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder, style = MaterialTheme.typography.bodySmall) }),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            AssistChip(
                onClick = onBrowse,
                label = { Text("浏览") },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.width(18.dp)) },
            )
        }
    }
}

/** The options the porter takes */
@Composable
private fun OptionsCard(win: MainActivity) {
    Section("选项") {
        CheckRow("触屏游戏（隐藏手柄）", win.touch) { win.touch = it }
        CheckRow("60 帧", win.fps60) { win.fps60 = it }
        CheckRow("实验性移植（还没适配的游戏）", win.experimental) { win.experimental = it }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** What the app does: the tools check, the port and its progress */
@Composable
private fun ActionsCard(win: MainActivity) {
    Section("移植") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { win.tools() }, modifier = Modifier.weight(1f), enabled = win.envReady && !win.porting) {
                Text("检查工具")
            }
            if (win.porting) {
                OutlinedButton(onClick = { win.cancel() }, modifier = Modifier.weight(1f)) { Text("取消") }
            } else {
                Button(onClick = { win.port() }, modifier = Modifier.weight(1f), enabled = win.envReady && win.storageOk) {
                    Text("开始移植")
                }
            }
        }
        if (win.porting || win.progress > 0f) {
            Spacer(Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { win.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            if (win.stageText.isNotEmpty()) {
                Text(win.stageText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** What the port made */
@Composable
private fun ResultCard(win: MainActivity) {
    if (!win.hasResult) return
    Section("产物") {
        Text(win.apkPath, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { win.install() }, modifier = Modifier.weight(1f)) { Text("安装 APK") }
        }
    }
}

/** The porter's output */
@Composable
private fun LogCard(win: MainActivity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { win.clearLog() }) { Text("清空") }
            }
            HorizontalDivider()
            val scroll = rememberScrollState()
            // the newest line stays in sight
            LaunchedEffect(win.log.size) { scroll.animateScrollTo(win.log.size * 40) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .verticalScroll(scroll)
            ) {
                Column {
                    if (win.log.isEmpty()) {
                        Text("还没有输出", style = MaterialTheme.typography.bodySmall)
                    } else {
                        win.log.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = if (line.startsWith("[RED]")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
