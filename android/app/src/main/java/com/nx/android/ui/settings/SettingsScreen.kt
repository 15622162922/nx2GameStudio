package com.nx.android.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.appstate.DriverState
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.OnboardingState
import com.nx.android.appstate.UpdateState
import com.nx.android.data.AppLocale
import com.nx.android.data.Drivers
import com.nx.android.data.Env
import com.nx.android.data.LosslessDll
import com.nx.android.ui.AppActions
import com.nx.android.ui.LocalAppLocale
import com.nx.android.ui.components.CapsuleButton
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.PickerMode
import com.nx.android.ui.components.PickerRequest
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.components.Section
import com.nx.android.ui.components.StatusDot
import com.nx.android.ui.components.rememberPageScroll
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
 * Settings: the environment's state and its prepare/reset, the doctor's check and the keys' path, the output
 * folder, the Vulkan drivers, the log (view, clear, share), the version and the way back into the guided setup.
 *
 * The driver page and the log cover the whole screen; they are composed above this page (App.kt), so this page
 * only asks for them.
 */
@Composable
fun SettingsScreen(ob: OnboardingState, lib: LibraryState, env: Env, drivers: DriverState, update: UpdateState,
                   actions: AppActions, onRequest: (PickerRequest) -> Unit,
                   onDrivers: () -> Unit, onLog: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberPageScroll()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 关于（含版本与检查更新）在最上面：版本与更新是玩家最先要找的两样东西
        AboutSection(ob, update, actions)
        LanguageSection()
        EnvSection(ob, actions, onRequest)
        PathsSection(lib, env, actions, onRequest)
        DriversSection(drivers, actions, onRequest, onDrivers)
        FrameGenSection(actions, onRequest)
        LogSection(actions, onLog)
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * 界面语言：玩家选的那个，没选就是跟随系统。换了立刻生效（整棵树读的资源换一份）。
 *
 * 它是 App 自己的语言，跟游戏自己的语言是两件事：游戏的语言在库页的抽屉里，一个游戏一个。
 */
@Composable
private fun LanguageSection() {
    val locale = LocalAppLocale.current
    Section(stringResource(R.string.set_section_language)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((mode, label) in listOf(
                AppLocale.Mode.SYSTEM to stringResource(R.string.set_language_system),
                AppLocale.Mode.ZH to stringResource(R.string.set_language_zh),
                AppLocale.Mode.EN to stringResource(R.string.set_language_en),
            )) {
                val on = locale.mode == mode
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (on) NxPalette.Cream else Color.White.copy(alpha = 0.16f))
                        .clickable { locale.pick(mode) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(label, fontSize = 13.sp,
                         fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                         color = if (on) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Hint(stringResource(R.string.set_language_note))
    }
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
    LaunchedEffect(Unit) { state.refresh(context) }   // 页面重新出现时重读 App 自己的目录
    val drivers = state.drivers

    Section(stringResource(R.string.driver_section)) {
        // 装好的驱动。App 自带的那一个也在这里：它是 App 的一部分，没有「装上」这一步，也删不掉
        for (d in drivers) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(NxPalette.Ready)
                Column(Modifier.weight(1f)) {
                    Text(d.label, style = MaterialTheme.typography.bodyMedium)
                    Hint(listOfNotNull(d.vendor.takeIf { it.isNotEmpty() }, d.library.name).joinToString(" · "), size = 11.sp)
                }
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
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CapsuleButton(stringResource(R.string.driver_download_action), onDrivers, modifier = Modifier.weight(1f))
            QuietButton(stringResource(R.string.driver_import_from_file), {
                onRequest(PickerRequest(PickerMode.FILE, setOf("zip", "so"), null, locale.str(R.string.driver_choose), onPicked = { f ->
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { Drivers.install(context, f) }
                        r.fold(
                            onSuccess = { state.refresh(context); actions.onToast(locale.str(R.string.driver_installed, it.label)) },
                            onFailure = { actions.onToast(locale.str(R.string.lib_import_failed, failureText(locale, it))) },
                        )
                    }
                }))
            }, modifier = Modifier.weight(1f))
        }
        Hint(stringResource(R.string.driver_installed_hint), size = 11.sp)
    }
}

/** 帧生成（Lossless.dll）：设备级的一次性配置。状态以私有副本为准，选择后校验并复制（不内置、不下载）。 */
@Composable
private fun FrameGenSection(actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val locale = LocalAppLocale.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var model by remember { mutableStateOf(LosslessDll.ensure(context)?.absolutePath) }
    Section(stringResource(R.string.fg_section)) {
        Text(
            if (model != null) stringResource(R.string.fg_ready, model!!)
            else stringResource(R.string.fg_not_found),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton(stringResource(R.string.fg_choose), onClick = {
                onRequest(PickerRequest(PickerMode.FILE, emptySet(), null, locale.str(R.string.fg_choose), onPicked = { f ->
                    if (LosslessDll.install(context, f)) {
                        model = LosslessDll.ensure(context)?.absolutePath
                        actions.onToast(locale.str(R.string.fg_ready_short))
                    } else actions.onToast(locale.str(R.string.fg_not_a_dll))
                }))
            })
            if (model != null) QuietButton(stringResource(R.string.fg_find_again), onClick = { model = LosslessDll.ensure(context)?.absolutePath })
        }
        Text(stringResource(R.string.fg_hint),
             style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EnvSection(ob: OnboardingState, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val locale = LocalAppLocale.current
    var confirmReset by remember { mutableStateOf(false) }
    Section(stringResource(R.string.env_section)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ob.unpacking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NxPalette.Cream)
            } else {
                StatusDot(if (ob.envReady) NxPalette.Ready else NxPalette.Danger)
            }
            Text(
                when {
                    ob.unpacking -> stringResource(R.string.onb_unpacking, ob.unpackStep)
                    ob.envReady -> if (ob.lastCheck.isEmpty()) stringResource(R.string.env_ready)
                        else stringResource(R.string.env_ready_detail, ob.lastCheck)
                    else -> stringResource(R.string.env_not_ready)
                },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CapsuleButton(stringResource(R.string.onb_prepare_env), { ob.unpack() }, enabled = !ob.unpacking, modifier = Modifier.weight(1f))
            QuietButton(stringResource(R.string.env_reset), { confirmReset = true }, enabled = !ob.unpacking, modifier = Modifier.weight(1f))
        }
        // 钥匙的行住在这里：环境检查的入口是顶栏的胶囊，这行是设置页里改路径的地方
        KeysRow(actions, onRequest)
    }
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

@Composable
private fun KeysRow(actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val locale = LocalAppLocale.current
    val keys = actions.keys()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusDot(if (keys.isNotBlank() && File(keys).isFile()) NxPalette.Ready else NxPalette.Danger, 8.dp)
        Column(Modifier.weight(1f)) {
            Text("prod.keys", style = MaterialTheme.typography.bodyMedium)
            Text(keys.ifEmpty { stringResource(R.string.set_keys_not_set) }, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        QuietButton(stringResource(R.string.common_modify), onClick = {
            onRequest(PickerRequest(PickerMode.FILE, emptySet(), null, locale.str(R.string.onb_choose_prod_keys),
                onPicked = { f -> actions.onSetKeys(f.path) }))
        })
    }
}

@Composable
private fun PathsSection(lib: LibraryState, env: Env, actions: AppActions, onRequest: (PickerRequest) -> Unit) {
    val locale = LocalAppLocale.current
    val out = actions.out()
    Section(stringResource(R.string.common_output_dir)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.set_output_dir_hint), style = MaterialTheme.typography.bodyMedium)
                Text(out, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            QuietButton(stringResource(R.string.common_modify), onClick = {
                onRequest(PickerRequest(PickerMode.DIR, emptySet(), File(out), locale.str(R.string.builds_choose_output_dir),
                    onPicked = { dir ->
                        actions.onSetOut(dir.path)
                        lib.rescan()
                    }))
            })
        }
        Hint(stringResource(R.string.set_output_dir_note))
    }
}

@Composable
private fun LogSection(actions: AppActions, onLog: () -> Unit) {
    Section(stringResource(R.string.common_log)) {
        Text(stringResource(R.string.set_log_hint),
             style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton(stringResource(R.string.common_view), onLog)
            QuietButton(stringResource(R.string.common_share), { actions.onShareLog() })
            QuietButton(stringResource(R.string.set_log_clear), { actions.onClearLog() })
        }
    }
}

/** 日志浮层：盖住整屏（在页面之外组合，App.kt） */
@Composable
internal fun LogOverlay(onClose: () -> Unit, text: String) {
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        GlassSheet(modifier = Modifier.fillMaxSize(0.9f).clickable(enabled = false) {}) {
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

@Composable
private fun AboutSection(ob: OnboardingState, update: UpdateState, actions: AppActions) {
    val ctx = LocalContext.current
    Section(stringResource(R.string.set_section_about)) {
        // 版本号点一下去 GitHub 仓库：下划线告诉玩家这一行能点
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().clickable { openRepo(ctx) },
        ) {
            Text(stringResource(R.string.set_version), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(actions.version(), style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 textDecoration = TextDecoration.Underline)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        // 有没有新版本：玩家自己查一次，结果当场摆出来
        UpdateCheckRow(update)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.set_rerun_onboarding), style = MaterialTheme.typography.bodyMedium)
                Hint(stringResource(R.string.set_rerun_onboarding_hint))
            }
            QuietButton(stringResource(R.string.set_run_onboarding), { ob.open() })
        }
    }
}
