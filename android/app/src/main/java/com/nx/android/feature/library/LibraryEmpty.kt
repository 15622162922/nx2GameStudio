package com.nx.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nx.android.core.ui.PreviewSurface
import com.nx.android.core.ui.theme.NxPalette

/**
 * 空态：架子上几个还没放上游戏的空位。
 *
 * 没有状态，没有动作——只有一张插画、一句提示和几个格式徽章。
 */

/**
 * 空态的插画：架子上几个还没放上游戏的空位。
 *
 * 中间那张大一些、上面有一个手柄，两边两张小一点、暗一点、歪一点——看着像架子上的空位，
 * 而不是一张贴纸。底下垫一圈极淡的品牌橙光晕，纯黑的底不至于发闷。
 */
@Composable
internal fun EmptyShelf() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(230.dp)) {
        Box(
            Modifier.size(210.dp).background(
                Brush.radialGradient(listOf(NxPalette.Brand.copy(alpha = 0.15f), Color.Transparent))
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmptyCard(62.dp, -11f, 0.45f)
            EmptyCard(94.dp, 0f, 1f, icon = true)
            EmptyCard(62.dp, 11f, 0.45f)
        }
    }
}

/** 预览：空态的那张插画 */
@Preview(name = "Empty shelf", widthDp = 280, heightDp = 280, showBackground = true)
@Composable
private fun EmptyShelfPreview() {
    PreviewSurface {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyShelf()
        }
    }
}

/** 空位卡：和真的游戏瓦片一个形状（14dp 圆角），只是没有封面，只有暗底与细描边 */
@Composable
private fun EmptyCard(size: Dp, tilt: Float, alpha: Float, icon: Boolean = false) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(size)
            .rotate(tilt)
            .alpha(alpha)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF26262A), Color(0xFF16161A))))
            .border(1.dp, NxPalette.Unbuilt.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            Icon(Icons.Outlined.VideogameAsset, null, Modifier.size(34.dp),
                 tint = NxPalette.Cream.copy(alpha = 0.30f))
        }
    }
}

/** 空态里的格式徽章：文件后缀是技术记号，两种语言里都是这四个 */
internal val EMPTY_FORMATS = listOf("NSP", "XCI", "NSZ", "XCZ")

@Composable
internal fun FormatChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** 预览：空态里的那几个格式徽章 */
@Preview(name = "Format chips", widthDp = 260, heightDp = 70, showBackground = true)
@Composable
private fun FormatChipsPreview() {
    PreviewSurface {
        Row(Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (f in EMPTY_FORMATS) FormatChip(f)
        }
    }
}
