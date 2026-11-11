package com.nx.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.appstate.DriverState
import com.nx.android.appstate.OnboardingState
import com.nx.android.appstate.UpdateState
import com.nx.android.data.AppLocale
import com.nx.android.data.Drivers
import com.nx.android.data.LosslessDll
import com.nx.android.ui.AppActions
import com.nx.android.ui.LocalAppLocale
import com.nx.android.ui.components.ActionRow
import com.nx.android.ui.components.ChoiceRow
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.InfoRow
import com.nx.android.ui.components.NoteRow
import com.nx.android.ui.components.PickerMode
import com.nx.android.ui.components.PickerRequest
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.components.SubtitleRow
import com.nx.android.ui.components.rememberPageScroll
import com.nx.android.ui.components.step
import com.nx.android.ui.failureText
import com.nx.android.ui.theme.GlassSheet
import com.nx.android.ui.theme.NxPalette
import com.nx.android.ui.update.UpdateCheckRow
import com.nx.android.ui.update.openRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 设置页：左边一栏分区，右边一栏内容。
 *
 * 它是一整页，盖住顶栏与下面的页面（从顶栏的设置图标进来，关掉才回到底下的页面）。
 * 分区固定五个（见 [SettingsSection]），顺序不变，内容按分区给。左栏选中的那一个在右边
 * 展开，右边自己滚；分区换了，右边的滚动位置回到顶上。
 *
 * 设置的内容还是原来那些：环境的状态与准备/重置、prod.keys 的路径、Vulkan 驱动（装/删/
 * 下载/导入）、帧生成的 Lossless.dll、日志（看/分享/清空）、版本与检查更新、界面语言、
 * 重跑引导。每一项占一行（见 `ui/components/SettingsRows.kt` 的行）。
 *
 * 分区是外面给的状态（[section] 与 [onSelectSection]）：关掉这一页再打开，玩家上次看的
 * 那一区还在；从别处带着一个分区进来也可以。
 *
 * 驱动下载页与日志浮层盖住整屏，它们在更上面组合（App.kt），所以这个页面只请它们。
 */
enum class SettingsSection(@StringRes val title: Int) {
    ENVIRONMENT(R.string.env_section),
    DRIVERS(R.string.driver_section),
    DISPLAY(R.string.set_section_display),
    LOG(R.string.common_log),
    ABOUT(R.string.set_section_about),
}

@Composable
fun SettingsScreen(ob: OnboardingState, drivers: DriverState, update: UpdateState,
                   actions: AppActions, onRequest: (PickerRequest) -> Unit,
                   onDrivers: () -> Unit, onLog: () -> Unit,
                   section: SettingsSection, onSelectSection: (SettingsSection) -> Unit,
                   onClose: () -> Unit) {
    val scroll = rememberPageScroll()
    LaunchedEffect(section) { scroll.scrollTo(0) }
    BackHandler { onClose() }

    // 它是一页，不是一个浮在上面的东西：整块盖住，连边框圆角都不留
    Box(Modifier.fillMaxSize().background(NxPalette.Surface)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 页头：这一页自己的名字与出去的入口
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.app_tab_settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                CloseIcon(onClose)
            }
            Row(Modifier.fillMaxSize()) {
                // 左栏：分区条目（圆角 10，选中是奶油底 + 深色字，与 App 里的选择药丸同一套）
                Column(
                    Modifier.width(150.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsSection.entries.forEach { s ->
                        RailItem(stringResource(s.title), s == section) { onSelectSection(s) }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    when (section) {
                        SettingsSection.ENVIRONMENT -> EnvironmentSection(ob, actions, onRequest)
                        SettingsSection.DRIVERS -> DriversSection(drivers, actions, onRequest, onDrivers)
                        SettingsSection.DISPLAY -> DisplaySection(actions, onRequest)
                        SettingsSection.LOG -> LogSection(actions, onLog)
                        SettingsSection.ABOUT -> AboutSection(ob, update, actions)
                    }
                }
            }
        }
    }
}

/** 页头的关闭：一个圆按钮里的叉 */
@Composable
private fun CloseIcon(onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Close,
            stringResource(R.string.common_close),
            Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** 左栏的一个分区条目 */
@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) NxPalette.Background else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) NxPalette.Cream else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/**
 * 环境：移植环境的状态、准备与重置、prod.keys 的路径。
 *
 * 环境检查的入口是顶栏的胶囊（另有一处），这一行是设置页里改路径的地方。
 */
@Composable
private fun EnvironmentSection(ob: OnboardingState, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val locale = LocalAppLocale.current
    var confirmReset by remember { mutableStateOf(false) }

    InfoRow(
        title = stringResource(R.string.env_check_env),
        note = when {
            ob.unpacking -> stringResource(R.string.onb_unpacking, ob.unpackStep)
            ob.envReady -> if (ob.lastCheck.isEmpty()) stringResource(R.string.env_ready)
                           else stringResource(R.string.env_ready_detail, ob.lastCheck)
            else -> stringResource(R.string.env_not_ready)
        },
        leading = {
            if (ob.unpacking) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
            } else {
                StatusDot(if (ob.envReady) NxPalette.Ready else NxPalette.Danger)
            }
        },
    )
    ActionRow(
        title = stringResource(R.string.onb_prepare_env),
        label = stringResource(R.string.common_prepare),
        enabled = !ob.unpacking,
    ) { ob.unpack() }
    ActionRow(
        title = stringResource(R.string.env_reset),
        label = stringResource(R.string.env_reset_action),
        enabled = !ob.unpacking,
    ) { confirmReset = true }
    KeysRow(actions, onRequest)

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(locale.str(R.string.env_reset_confirm)) },
            text = { Text(locale.str(R.string.env_reset_what)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    ob.resetEnv()
                    actions.onToast(locale.str(R.string.env_reset_done))
                }) { Text(locale.str(R.string.env_reset_action), color = NxPalette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(locale.str(R.string.common_cancel)) } },
        )
    }
}

/** prod.keys 的路径行：状态点 + 路径 + 修改 */
@Composable
private fun KeysRow(actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val locale = LocalAppLocale.current
    val keys = actions.keys()
    InfoRow(
        title = "prod.keys",
        value = keys.ifEmpty { stringResource(R.string.set_keys_not_set) },
        leading = { StatusDot(if (keys.isNotBlank() && File(keys).isFile()) NxPalette.Ready else NxPalette.Danger, 8.dp) },
        trailing = {
            QuietButton(stringResource(R.string.common_modify), onClick = {
                onRequest(PickerRequest(PickerMode.FILE, emptySet(), null, locale.str(R.string.onb_choose_prod_keys),
                    onPicked = { f -> actions.onSetKeys(f.path) }))
            })
        },
    )
}

/**
 * Vulkan 驱动：设备级的驱动在这里装、删、下载。游戏用哪一个驱动是每个游戏自己的事，在库页的
 * 抽屉里选（那里写的是数据包的 env.txt）。
 */
@Composable
private fun DriversSection(state: DriverState, actions: AppActions, onRequest: (PickerRequest) -> Unit,
                           onDrivers: () -> Unit) {
    val locale = LocalAppLocale.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { state.refresh(context) }   // 这一区被打开时重读 App 自己的目录
    val drivers = state.drivers

    // 装好的驱动。App 自带的那一个也在这里：它是 App 的一部分，没有「装上」这一步，也删不掉
    for (d in drivers) {
        InfoRow(
            title = d.label,
            note = listOfNotNull(d.vendor.takeIf { it.isNotEmpty() }, d.library.name).joinToString(" · "),
            leading = { StatusDot(NxPalette.Ready) },
            trailing = {
                if (d.shipped) {
                    Hint(stringResource(R.string.driver_builtin), size = 11.sp)
                } else {
                    QuietButton(stringResource(R.string.common_delete), {
                        if (Drivers.remove(context, d.id)) {
                            state.refresh(context)
                            actions.onToast(locale.str(R.string.driver_deleted, d.name))
                        } else actions.onToast(locale.str(R.string.lib_delete_failed_busy))
                    })
                }
            },
        )
    }
    ActionRow(title = stringResource(R.string.driver_download_action), label = stringResource(R.string.common_download)) {
        onDrivers()
    }
    ActionRow(title = stringResource(R.string.driver_import_from_file), label = stringResource(R.string.common_import)) {
        onRequest(PickerRequest(PickerMode.FILE, setOf("zip", "so"), null, locale.str(R.string.driver_choose), onPicked = { f ->
            scope.launch {
                val r = withContext(Dispatchers.IO) { Drivers.install(context, f) }
                r.fold(
                    onSuccess = { state.refresh(context); actions.onToast(locale.str(R.string.driver_installed, it.label)) },
                    onFailure = { actions.onToast(locale.str(R.string.lib_import_failed, failureText(locale, it))) },
                )
            }
        }))
    }
    NoteRow(stringResource(R.string.driver_installed_hint))
}

/**
 * 画面：帧生成（Lossless.dll）。设备级的一次性配置，状态以私有副本为准，
 * 选择后校验并复制（不内置、不下载）。
 */
@Composable
private fun DisplaySection(actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val locale = LocalAppLocale.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var model by remember { mutableStateOf(LosslessDll.ensure(context)?.absolutePath) }

    SubtitleRow(stringResource(R.string.fg_section))
    InfoRow(
        title = "Lossless.dll",
        note = if (model != null) stringResource(R.string.fg_ready, model!!) else stringResource(R.string.fg_not_found),
        trailing = if (model != null) {
            { QuietButton(stringResource(R.string.fg_find_again), onClick = { model = LosslessDll.ensure(context)?.absolutePath }) }
        } else null,
    )
    ActionRow(title = stringResource(R.string.fg_choose), label = stringResource(R.string.common_choose)) {
        onRequest(PickerRequest(PickerMode.FILE, emptySet(), null, locale.str(R.string.fg_choose), onPicked = { f ->
            if (LosslessDll.install(context, f)) {
                model = LosslessDll.ensure(context)?.absolutePath
                actions.onToast(locale.str(R.string.fg_ready_short))
            } else actions.onToast(locale.str(R.string.fg_not_a_dll))
        }))
    }
    NoteRow(stringResource(R.string.fg_hint))
}

/** 日志：工具说的每一句都在一个文件里，这里是看它、分享它、清掉它。 */
@Composable
private fun LogSection(actions: AppActions, onLog: () -> Unit) {
    NoteRow(stringResource(R.string.set_log_hint))
    ActionRow(title = stringResource(R.string.set_log_view), label = stringResource(R.string.common_view)) { onLog() }
    ActionRow(title = stringResource(R.string.set_log_share), label = stringResource(R.string.common_share)) { actions.onShareLog() }
    ActionRow(title = stringResource(R.string.set_log_clear_title), label = stringResource(R.string.set_log_clear)) { actions.onClearLog() }
}

/**
 * 关于：版本号（点一下去 GitHub 仓库）、检查更新、界面的语言、重跑引导。
 *
 * 语言是 App 自己的语言，跟游戏自己的语言是两件事：游戏的语言在库页的抽屉里，一个游戏一个。
 */
@Composable
private fun AboutSection(ob: OnboardingState, update: UpdateState, actions: AppActions) {
    val ctx = LocalContext.current
    val locale = LocalAppLocale.current

    // 版本号点一下去 GitHub 仓库
    InfoRow(title = stringResource(R.string.set_version), value = actions.version(), onClick = { openRepo(ctx) })
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    // 有没有新版本：玩家自己查一次，结果当场摆出来
    UpdateCheckRow(update)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    // 界面语言：玩家选的那个，没选就是跟随系统。换了立刻生效（整棵树读的资源换一份）
    val modes = AppLocale.Mode.entries.toList()
    ChoiceRow(
        title = stringResource(R.string.set_language_title),
        value = languageLabel(locale.mode),
        note = stringResource(R.string.set_language_note),
        onPrev = { locale.pick(step(modes, locale.mode, -1)) },
        onNext = { locale.pick(step(modes, locale.mode, +1)) },
    )
    ActionRow(
        title = stringResource(R.string.set_rerun_onboarding),
        label = stringResource(R.string.set_run_onboarding),
        note = stringResource(R.string.set_rerun_onboarding_hint),
    ) { ob.open() }
}

/** 语言那一行中间的值：当前是哪一档 */
@Composable
private fun languageLabel(mode: AppLocale.Mode): String = when (mode) {
    AppLocale.Mode.SYSTEM -> stringResource(R.string.set_language_system)
    AppLocale.Mode.ZH -> stringResource(R.string.set_language_zh)
    AppLocale.Mode.EN -> stringResource(R.string.set_language_en)
}

/** 日志浮层：盖住整屏（在页面之外组合，App.kt） */
@Composable
internal fun LogOverlay(onClose: () -> Unit, text: String) {
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        // 这一页盖住整屏（它可能盖在设置页上），近乎不透明：底下页面的字不该透上来
        GlassSheet(modifier = Modifier.fillMaxSize(0.9f).clickable(enabled = false) {}, alpha = 0.97f) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.common_log), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                         modifier = Modifier.weight(1f))
                    QuietButton(stringResource(R.string.common_close), onClose)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text.ifEmpty { stringResource(R.string.main_log_empty) },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
