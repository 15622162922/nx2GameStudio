package com.nx.android.ui.env

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nx.android.R
import com.nx.android.domain.EnvCheck
import com.nx.android.ui.theme.NxPalette

/**
 * The top bar's environment state: a dot and a word.
 *
 * The chip is always there, so the player sees whether the app can be used without going anywhere. It is red
 * when the environment or the keys are not in place, and tapping it opens the checklist.
 */
@Composable
fun EnvChip(chip: EnvCheck.Chip, onClick: () -> Unit) {
    val (text, tint) = when (chip) {
        EnvCheck.Chip.CHECKING -> stringResource(R.string.env_chip_checking) to NxPalette.Unbuilt
        EnvCheck.Chip.READY -> stringResource(R.string.env_chip_ready) to NxPalette.Ready
        EnvCheck.Chip.NOT_READY -> stringResource(R.string.env_chip_not_ready) to NxPalette.Danger
    }
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick)
            // 顶栏压在壁纸的背景上：一块实心的深色药丸看着像个洞，淡淡的一层白才像浮在上面
            .background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(tint))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
