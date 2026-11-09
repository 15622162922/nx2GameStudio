package com.nx.android.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nx.android.R
import com.nx.android.appstate.UpdateState
import com.nx.android.domain.UpdateVersion
import com.nx.android.ui.components.CapsuleButton
import com.nx.android.ui.components.Hint
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.theme.GlassSheet
import com.nx.android.ui.theme.NxPalette

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
 * 主页上的那条提示：有新版本时出现。不挡事（不是弹窗），可以关掉——关掉之后这个版本不再提。
 */
@Composable
fun UpdateBanner(state: UpdateState, modifier: Modifier = Modifier) {
    if (state.phase != UpdateState.Phase.AVAILABLE || state.dismissed) return
    val ctx = LocalContext.current
    GlassSheet(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.update_available, state.latest),
                 style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CapsuleButton(stringResource(R.string.update_download), { openReleases(ctx) },
                              modifier = Modifier.weight(1f))
                QuietButton(stringResource(R.string.common_close), { state.dismiss() },
                            modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * 设置页里的那一条：玩家自己查一次，结果当场摆出来——已是最新、有新版本（带去拿它的入口）、
 * 还是查不成。玩家主动问的，就给他真答案（不管他之前关掉过哪一版的提示）。
 */
@Composable
fun UpdateCheckRow(state: UpdateState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val checking = state.phase == UpdateState.Phase.CHECKING
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_check), style = MaterialTheme.typography.bodyMedium)
                Hint(stringResource(R.string.update_check_hint))
            }
            QuietButton(
                stringResource(if (checking) R.string.update_checking else R.string.update_check),
                { state.checkNow() },
                enabled = !checking,
            )
        }
        when (state.phase) {
            UpdateState.Phase.UP_TO_DATE ->
                Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            UpdateState.Phase.AVAILABLE ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.update_available, state.latest),
                         style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                         modifier = Modifier.weight(1f))
                    QuietButton(stringResource(R.string.update_download), { openReleases(ctx) })
                }
            UpdateState.Phase.FAILED ->
                Text(stringResource(R.string.update_failed), style = MaterialTheme.typography.bodySmall,
                     color = NxPalette.Danger)
            else -> {}
        }
    }
}
