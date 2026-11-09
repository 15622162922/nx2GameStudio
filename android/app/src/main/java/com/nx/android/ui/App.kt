package com.nx.android.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.AppContainer
import com.nx.android.R
import com.nx.android.appstate.PortState
import com.nx.android.data.AppLocale
import com.nx.android.data.StorageVolumes
import com.nx.android.domain.GAME_FILE_EXTENSIONS
import com.nx.android.domain.GameConfig
import com.nx.android.ui.builds.BuildsScreen
import com.nx.android.ui.components.CapsuleButton
import com.nx.android.ui.components.FilePickerScreen
import com.nx.android.ui.components.PickerMode
import com.nx.android.ui.components.PickerRequest
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.library.ConfigPanel
import com.nx.android.ui.library.HeroBackground
import com.nx.android.ui.library.LibraryScreen
import com.nx.android.ui.onboarding.OnboardingScreen
import com.nx.android.ui.settings.DriverScreen
import com.nx.android.ui.settings.LogOverlay
import com.nx.android.ui.settings.SettingsScreen
import com.nx.android.ui.theme.GlassSheet
import com.nx.android.ui.theme.NxPalette
import com.nx.android.ui.theme.NxTheme
import com.nx.android.ui.update.UpdateBanner
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The main screen's pages */
enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    LIBRARY(R.string.app_tab_library, Icons.Filled.SportsEsports),
    BUILDS(R.string.app_tab_builds, Icons.Filled.Inventory2),
    SETTINGS(R.string.app_tab_settings, Icons.Filled.Settings),
}

/** The screens' way back to the activity's own privileges: what they may ask for, nothing else */
data class AppActions(
    val onAskStorage: () -> Unit,
    val storageOk: () -> Boolean,
    val onSetKeys: (String) -> Unit,
    val onStartPort: (file: File, cfg: GameConfig) -> Unit,
    val onCancelPort: () -> Unit,
    val onRun: (pack: File, name: String) -> Unit,
    val onResumeGame: () -> Unit,
    val onStopGame: () -> Unit,
    val onToast: (String) -> Unit,
    val version: () -> String,
    val keys: () -> String,
    val out: () -> String,
    val onSetOut: (String) -> Unit,
    val onShareLog: () -> Unit,
    val onClearLog: () -> Unit,
    val readLog: () -> String,
)

/** The app's root: the guided setup until it is done, the main shell after */
/**
 * The app's root: the guided setup until it is done, the main shell after.
 *
 * The pages that cover the whole screen (the file picker, the driver page, the log) are composed here, above
 * the pages themselves: a page's own content sits in a scrolling column, and a cover inside one never fills
 * the screen — it is laid out in the column's flow instead.
 */
@Composable
fun AppRoot(container: AppContainer, actions: AppActions, startTab: Int) {
    NxTheme {
        // 界面语言：整棵树读同一份资源，玩家在设置里换语言时这里跟着换
        ProvideAppLocale(container.locale) {
        var pickerReq by remember { mutableStateOf<PickerRequest?>(null) }
        var driversOpen by remember { mutableStateOf(false) }
        var logOpen by remember { mutableStateOf(false) }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Box(Modifier.fillMaxSize().background(NxPalette.Background)) {
            if (container.onboarding.active) {
                OnboardingScreen(container.onboarding, actions) { pickerReq = it }
            } else {
                MainScreen(container, actions, startTab,
                    onRequest = { pickerReq = it },
                    onDrivers = { driversOpen = true },
                    onLog = { logOpen = true })
            }
            val volumes = remember(ctx, container.locale.mode) { StorageVolumes.list(ctx, container.locale) }
            pickerReq?.let { req ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                    if (!actions.storageOk()) {
                        // 内置文件管理器读不了没有全文件访问的存储：给授权入口
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        ) {
                            Spacer(Modifier.weight(1f))
                            Text(stringResource(R.string.app_storage_permission_needed),
                                 style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.app_storage_permission_picker),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Spacer(Modifier.weight(1f))
                                QuietButton(stringResource(R.string.common_cancel), onClick = { pickerReq = null })
                                CapsuleButton(stringResource(R.string.app_grant_permission), onClick = {
                                    pickerReq = null
                                    actions.onAskStorage()
                                })
                            }
                        }
                    } else {
                        FilePickerScreen(
                            req = req,
                            volumes = volumes,
                            onPicked = { f ->
                                pickerReq = null
                                req.onPicked(f)
                            },
                            onCancel = { pickerReq = null },
                        )
                    }
                }
            }
            // 驱动页：盖住整屏，在页面之外
            if (driversOpen) {
                DriverScreen(container.drivers, onClose = { driversOpen = false })
            }
            // 日志浮层：同样盖住整屏
            if (logOpen) {
                BackHandler { logOpen = false }
                LogOverlay(onClose = { logOpen = false }, text = remember { actions.readLog() })
            }
        }
        }
    }
}

@Composable
fun MainScreen(container: AppContainer, actions: AppActions, startTab: Int,
               onRequest: (PickerRequest) -> Unit,
               onDrivers: () -> Unit,
               onLog: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    val port = container.port
    val lib = container.library

    // a finished port goes into the library's records (the source and its APK stay together)
    LaunchedEffect(port.phase) {
        if (port.phase == PortState.Phase.DONE) lib.noteDone(port.gameFile, port.dataPath)
    }

    // 选中游戏的背景铺满整块屏幕：顶栏透明画在图上，三个 tab 共用这层背景
    Box(Modifier.fillMaxSize()) {
        HeroBackground(lib, port, lib.visible().getOrNull(lib.heroIndex))
        Column(Modifier.fillMaxSize()) {
        // 文件选择器的标题：回调里不能取词，先在外面取好
        val chooseFolder = stringResource(R.string.app_choose_game_folder)
        TopBar(
            selected = tab,
            onSelect = { tab = it.ordinal },
            onAddGameFolder = {
                onRequest(PickerRequest(PickerMode.DIR, emptySet(),
                    File(lib.folder.ifBlank { "/storage" }), chooseFolder,
                    onPicked = { dir -> lib.chooseFolder(dir.path) }))
            },
            onRefresh = { lib.rescan() },
        )
        // 有新版本时的提示条：在顶栏下面、内容上面，不挡事，可以关掉
        UpdateBanner(container.update)
        Box(Modifier.weight(1f)) {
            when (Tab.entries[tab]) {
                Tab.LIBRARY -> LibraryScreen(lib, port, container.player, actions, onGoBuilds = { tab = Tab.BUILDS.ordinal }, onRequest = onRequest)
                Tab.BUILDS -> BuildsScreen(lib, port, container.prefs.keys, container.env.ready, actions, onRequest)
                Tab.SETTINGS -> SettingsScreen(container.onboarding, lib, container.env, container.drivers, container.update, actions,
                    onRequest, onDrivers, onLog)
            }
        }
        if (port.running) ActivityBar(port, openBuilds = { tab = Tab.BUILDS.ordinal })
        }
        // 配置面板画在最上层：它的遮罩要盖住顶栏与整块页面
        val panelFile = remember { mutableStateOf<File?>(null) }
        LaunchedEffect(lib.selected) { if (lib.selected != null) panelFile.value = lib.selected }
        AnimatedVisibility(
            visible = lib.selected != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            panelFile.value?.let { f ->
                ConfigPanel(lib, port, container.player, f, actions,
                    onGoBuilds = { tab = Tab.BUILDS.ordinal }, onRequest = onRequest,
                    onClose = { lib.selected = null })
            }
        }
    }
}

/** 一行式顶栏：品牌 + 三个纯文字 tab（选中亮/未选灰）+ 右侧图标与时钟（capyps5 的骨架，我们的颜色） */
@Composable
private fun TopBar(selected: Int, onSelect: (Tab) -> Unit, onAddGameFolder: () -> Unit, onRefresh: () -> Unit) {
    val time by produceState(initialValue = "") {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            value = format.format(Date())
            delay(10_000)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Image(painterResource(R.drawable.ic_launcher_mark), null, Modifier.size(32.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) { append("nx2") }
                withStyle(SpanStyle(color = NxPalette.Cream)) { append("android") }
            },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 8.dp, end = 30.dp),
        )
        Tab.entries.forEachIndexed { i, t ->
            Text(
                stringResource(t.label),
                style = MaterialTheme.typography.titleLarge,
                color = if (i == selected) MaterialTheme.colorScheme.onBackground else NxPalette.Unbuilt,
                modifier = Modifier.clickable { onSelect(t) }.padding(end = 26.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        TopIcon(Icons.Filled.Add, stringResource(R.string.app_add_game_folder), onAddGameFolder)
        TopIcon(Icons.Filled.Refresh, stringResource(R.string.app_refresh_library), onRefresh)
        Text(time, style = MaterialTheme.typography.titleMedium,
             color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun TopIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onBackground)
    }
}

/** The port's progress, always in sight at the bottom while it runs; tapping opens the builds page */
@Composable
private fun ActivityBar(port: PortState, openBuilds: () -> Unit) {
    val secs = elapsedSecs(port.startedAt)
    GlassSheet(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().clickable { openBuilds() }.padding(horizontal = 14.dp, vertical = 10.dp),
               verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NxPalette.Building,
                )
                Text(
                    File(port.gameFile).name,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), maxLines = 1,
                )
                Text(
                    stringResource(R.string.app_stage_and_time,
                        port.stageOf, port.stages, secs / 60, secs % 60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (port.stageStep.isNotEmpty()) {
                Text(
                    "${port.stageName} · ${port.stageStep}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            LinearProgressIndicator(
                progress = { port.progress },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = NxPalette.Building,
            )
        }
    }
}

/** A second-by-second count while a port runs (the elapsed time moves on its own) */
@Composable
fun elapsedSecs(startedAt: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (startedAt > 0) { delay(1000); now = System.currentTimeMillis() }
    }
    return if (startedAt > 0) ((now - startedAt) / 1000).coerceAtLeast(0) else 0
}
