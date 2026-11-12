package com.nx.android.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nx.android.core.ui.theme.NxPalette
import com.nx.android.core.ui.theme.NxTheme

/**
 * 预览的底：app 的主题与它深色的底。
 *
 * 组件的预览用它包一层——屏幕上就是这个样子（深色、app 自己的排版）。
 * 这个文件只有预览用得到，跑在真机上时它什么也不做。
 */
@Composable
fun PreviewSurface(content: @Composable () -> Unit) {
    NxTheme {
        Box(Modifier.fillMaxSize().background(NxPalette.Background)) { content() }
    }
}
