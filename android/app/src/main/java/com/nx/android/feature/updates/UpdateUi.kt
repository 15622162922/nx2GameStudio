package com.nx.android.feature.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nx.android.R
import com.nx.android.domain.UpdatePhase
import com.nx.android.feature.updates.UpdateViewModel
import com.nx.android.domain.UpdateVersion
import com.nx.android.core.ui.components.Hint
import com.nx.android.core.ui.components.QuietButton
import com.nx.android.core.ui.theme.NxPalette

/**
 * 最新 release 的页面：浏览器里下那个带版本号的 APK。App 自己不下载、不装——装自己要「未知来源」
 * 的授权和一整套下载的流程；页面交给浏览器，玩家在那里下、自己装。
 */
fun openReleases(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateVersion.RELEASES_LATEST_URL)))
    } catch (e: Exception) {
        // 没有浏览器：App 里没有别的路可以走到那里，安静地过去
    }
}

/**
 * 仓库主页：源码、issue 与 release 都在那里。设置页的版本号与开源声明都跳到这里。
 */
fun openRepo(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateVersion.REPO_URL)))
    } catch (e: Exception) {
        // 没有浏览器：App 里没有别的路可以走到那里，安静地过去
    }
}

/**
 * 有新版本时的弹框：新版本号、那个 release 自己的更新说明、两个按钮。
 *
 * 弹框是玩家冷启动后看到的第一个东西，所以它得说得清：哪个版本、比自己新在哪、去哪拿。
 * 「稍后」＝这一次启动不再问（下次冷启动还会问）；「去下载」＝浏览器打开 release 页。
 */
@Composable
fun UpdatePopup(viewModel: UpdateViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.phase != UpdatePhase.AVAILABLE || state.later) return
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { viewModel.postpone() },
        // 小屏上平台默认的弹框宽度太窄（正文都是会换行的文字，弹框就贴着那个窄宽度排）。
        // 这里让它按可用宽度铺开、两侧留 16dp 边距；大屏上封顶 520dp。
        // 顺序要紧：边距在外、封顶在中间、铺满在内——这样弹框的窗口按内容收缩（平台居中），
        // 卡片本身落在边距里面，宽屏上也不会被拉到 520dp 之外。
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 520.dp).fillMaxWidth(),
        title = {
            Text(stringResource(R.string.update_available, state.latest), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.mine.isNotEmpty()) {
                    Text(stringResource(R.string.update_your_version, state.mine),
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.notes.isNotEmpty()) ReleaseNotes(state.notes)
            }
        },
        confirmButton = {
            TextButton(onClick = { openReleases(ctx) }) {
                Text(stringResource(R.string.update_download), color = NxPalette.Building)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.postpone() }) { Text(stringResource(R.string.update_later)) }
        },
    )
}

/**
 * 那个 release 的更新说明：release 页自己的正文（GitHub 渲染好的 HTML）。
 *
 * 用系统的 TextView 画，不自己读 HTML：正文是 release 作者写的样子（标题、列表、代码、链接），
 * 系统自己的读者认得这些，App 不需要再懂一遍 Markdown 或 HTML。
 */
@Composable
private fun ReleaseNotes(html: String) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = NxPalette.Building.toArgb()
    // 太长的正文要能滚：弹框里的正文最多占屏幕的一半，下面两个按钮一直看得见
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
    val spanned = remember(html) {
        // <code> 系统不认（它只认 <tt>），换了它代码块才是等宽的
        Html.fromHtml(html.replace("<code>", "<tt>").replace("</code>", "</tt>"), Html.FROM_HTML_MODE_LEGACY)
    }
    Column(
        Modifier
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                TextView(ctx).apply {
                    setTextColor(color)
                    setLinkTextColor(linkColor)
                    textSize = 14f
                    setLineSpacing(ctx.dp(2f), 1f)
                    // 链接点得开：release 的说明里常有指向别处的地址
                    movementMethod = LinkMovementMethod.getInstance()
                }
            },
            update = { it.text = spanned },
        )
    }
}

/** 一个 dp 在这个屏幕上是几个像素 */
private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

/**
 * 设置页里的那一条：玩家自己查一次，结果当场摆出来——已是最新、有新版本（带去拿它的入口）、
 * 还是查不成。玩家主动问的，就给他真答案。
 */
@Composable
fun UpdateCheckRow(viewModel: UpdateViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val checking = state.phase == UpdatePhase.CHECKING
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_check), style = MaterialTheme.typography.bodyMedium)
                Hint(stringResource(R.string.update_check_hint))
            }
            QuietButton(
                stringResource(if (checking) R.string.update_checking else R.string.update_check),
                { viewModel.checkNow() },
                enabled = !checking,
            )
        }
        when (state.phase) {
            UpdatePhase.UP_TO_DATE ->
                Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            UpdatePhase.AVAILABLE ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.update_available, state.latest),
                         style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                         modifier = Modifier.weight(1f))
                    QuietButton(stringResource(R.string.update_download), { openReleases(ctx) })
                }
            UpdatePhase.FAILED ->
                Text(stringResource(R.string.update_failed), style = MaterialTheme.typography.bodySmall,
                     color = NxPalette.Danger)
            else -> {}
        }
    }
}
