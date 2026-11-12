package com.nx.android.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.nx.android.R
import com.nx.android.core.ui.theme.NxPalette
import com.nx.android.domain.GameStatus
import com.nx.android.domain.Ported

/**
 * 库页的小词与颜色：状态的名字与颜色、卡片上的几个标记、封面的两个颜色。
 *
 * 都是纯函数（除了读字符串的那几个），所以预览可以直接用它们，不碰任何状态对象。
 */

/** 状态的颜色：能玩是绿的，构建中是黄的，其余是灰的 */
internal fun statusColor(s: GameStatus) = when (s) {
    GameStatus.NOT_PORTED -> NxPalette.Unbuilt
    GameStatus.PORTING -> NxPalette.Building
    GameStatus.DONE -> NxPalette.Ready
    GameStatus.NEEDS_BUILD -> NxPalette.Unbuilt
}

@Composable
internal fun statusLabel(s: GameStatus): String = when (s) {
    GameStatus.NOT_PORTED -> stringResource(R.string.common_not_ported)
    GameStatus.PORTING -> stringResource(R.string.common_porting)
    GameStatus.DONE -> stringResource(R.string.common_done)
    GameStatus.NEEDS_BUILD -> stringResource(R.string.lib_needs_build_status)
}

/**
 * 实验性移植的标记：档案还没在 Android 上调通（清单里的 status 是 experimental），玩家该知道
 * 这个游戏随时可能卡在某条没走过的路上 —— 而不是以为它坏了。
 */
@Composable
internal fun experimentalTag(ported: Ported?): String? =
    if (ported?.status == "experimental") stringResource(R.string.lib_experimental) else null

/** 旧版宿主编的数据包：要用当前版本重新移植（宿主的前端变了，老的宿主没有新的入口） */
@Composable
internal fun reportTag(ported: Ported?): String? =
    if (ported?.needsRePort == true) stringResource(R.string.lib_needs_report) else null

/** 清单里的显示版本（游戏自己的版本号，如 1.1.1）：老的数据包没有这一项，就不显示 */
internal fun versionTag(ported: Ported?): String? =
    ported?.display?.trim()?.takeIf { it.isNotEmpty() }

/** The cover's two colors, picked by the name so one game always wears the same coat */
internal fun coverColors(name: String): List<Color> {
    val h = name.hashCode()
    fun c(off: Int) = Color(
        red = 0.18f + ((h shr off) and 0x3) * 0.05f,
        green = 0.18f + ((h shr (off + 2)) and 0x3) * 0.05f,
        blue = 0.24f + ((h shr (off + 4)) and 0x3) * 0.06f,
        alpha = 1f,
    )
    return listOf(c(0), c(8))
}
