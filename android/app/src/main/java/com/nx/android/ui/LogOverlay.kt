package com.nx.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.ui.components.QuietButton
import com.nx.android.ui.theme.GlassSheet

/**
 * 日志浮层：盖住整屏（在页面之外组合，App.kt）。
 *
 * 它给的是 App 那份日志文件的内容（App.kt 读好再传进来），不是构建页上显示的那几十行。
 */
@Composable
internal fun LogOverlay(onClose: () -> Unit, text: String) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        // 这一页盖住整屏（它盖在构建页上面），近乎不透明：底下页面的字不该透上来
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
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
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
