package com.nx.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nx.android.R
import com.nx.android.ui.theme.NxPalette

/**
 * 设置页的行：一行一个设置项。
 *
 * 这一套行是设置页唯一的形状来源 —— 每一行都是「标题 + 说明（如果有）+ 当前值或动作」。
 * 行本身不含状态：值从哪儿来、点了做什么，由调用方给（设置的状态住在 App 的状态对象里，
 * 见 `appstate/`），所以这些行既能在设置页用，也能在别处用。
 *
 * 四种可改的方式，就是四种行：
 *
 *  - [InfoRow]：只读的一行（状态、路径、版本号）。[onClick] 给上时整行可点。
 *  - [ChoiceRow]：取值在固定的几个之间轮转 —— 左右箭头，或者直接点那个值。
 *  - [SwitchRow]：开 / 关两态。
 *  - [ActionRow]：点一下就去做一件事（打开选择器、打开另一个页面）。
 *
 * 另有两行不是设置项，是排版：[NoteRow] 是一行说明，[SubtitleRow] 是小标题。
 */

/**
 * 只读的一行：标题、它的说明（如果有）、它的当前值（如果有）。
 *
 * [leading] 放状态点或进度圈（行的最左边），[trailing] 放这一行自己的动作（最右边）。
 * [onClick] 给上时整行可点，当前的值也标成可点的样子（品牌色 + 下划线）。
 */
@Composable
fun InfoRow(
    title: String,
    value: String? = null,
    note: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = if (onClick != null) NxPalette.Cream else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                // 可点的那一行：下划线告诉玩家它点得开
                textDecoration = if (onClick != null) TextDecoration.Underline else null,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/**
 * 取值在固定的几个之间轮转的一行：左边一个箭头、右边一个箭头，中间是当前的值。
 *
 * 点中间那个值等于点右边的箭头（往前走一档）。取值由调用方给（[step] 算得出下一个）。
 */
@Composable
fun ChoiceRow(
    title: String,
    value: String,
    note: String? = null,
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Step(Icons.AutoMirrored.Filled.KeyboardArrowLeft, onPrev)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = NxPalette.Cream,
            modifier = Modifier
                .widthIn(min = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onNext() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Step(Icons.AutoMirrored.Filled.KeyboardArrowRight, onNext)
    }
}

/** 开 / 关两态的一行：右边写着当前是哪一态，点一下就翻面。 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    note: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            if (checked) androidx.compose.ui.res.stringResource(R.string.common_on)
            else androidx.compose.ui.res.stringResource(R.string.common_off),
            style = MaterialTheme.typography.titleMedium,
            color = if (checked) NxPalette.Cream else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .clickable { onChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** 动作的一行：标题是这件事的名字，右边是按钮（按钮上写动词）。 */
@Composable
fun ActionRow(
    title: String,
    label: String,
    note: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        QuietButton(label, onClick, enabled = enabled)
    }
}

/** 一行说明：把这一区该怎么用说清楚（不是设置项本身）。 */
@Composable
fun NoteRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

/** 小标题：一区里再分几块时用。 */
@Composable
fun SubtitleRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = NxPalette.Cream,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

/** 轮转的一步：左边一档 / 右边一档，走到头就绕回另一头。 */
@Composable
private fun Step(icon: ImageVector, onClick: () -> Unit) {
    Icon(
        icon,
        null,
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(3.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 在几个取值之间轮转一步：[delta] 是 -1（往前）还是 +1（往后）。
 *
 * 当前的值不在 [options] 里（被删掉了、改了）时，从头一个开始。
 */
fun <T> step(options: List<T>, current: T, delta: Int): T {
    val i = options.indexOf(current)
    return options[((if (i < 0) 0 else i) + delta).mod(options.size)]
}
