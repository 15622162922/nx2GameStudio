package com.nx.android.game.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.nx.android.R
import com.nx.android.core.ui.theme.NxPalette
import com.nx.android.input.GamepadScan
import com.nx.android.input.PadCalibration
import com.nx.android.input.PadMapping
import com.nx.android.input.PadProfileStore
import com.nx.android.input.PadSources
import com.nx.android.input.PadTargets
import com.nx.android.input.PadTestState

/**
 * 手柄中心：接在手机上的物理手柄看得见、摸得着。
 *
 * 开着的时候游戏继续跑，但手柄的输入静音（进不了游戏）—— 面板上显示的正是被静音的那些输入。
 * 面板是 App 自己的 Compose 覆盖层，不是宿主的暂停菜单。
 */
@Composable
fun PadCenter(
    devices: List<GamepadScan.Info>,
    profiles: Map<String, PadProfileStore.Profile>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    states: Map<Int, PadTestState>,
    capturing: String?,
    onLabels: (GamepadScan.Info, Boolean) -> Unit,
    onCapture: (Int, String) -> Unit,
    onClearBinding: (GamepadScan.Info, String) -> Unit,
    onResetBindings: (GamepadScan.Info) -> Unit,
    onCalibration: (GamepadScan.Info, PadCalibration) -> Unit,
    onVibrate: (GamepadScan.Info) -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable { onClose() }) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 标题与关闭
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.pad_center_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.common_close)) }
            }
            HintRow(stringResource(R.string.pad_center_muted))
            Spacer(Modifier.height(8.dp))

            if (devices.isEmpty()) {
                GroupLabel(stringResource(R.string.pad_center_devices))
                Spacer(Modifier.height(4.dp))
                HintRow(stringResource(R.string.pad_center_none))
                Spacer(Modifier.height(8.dp))
                ActionRow(Icons.Filled.Bluetooth, stringResource(R.string.pad_center_bluetooth), null) { onClose() }
                return@Column
            }

            // 手柄的列表
            GroupLabel(stringResource(R.string.pad_center_devices))
            Spacer(Modifier.height(4.dp))
            for (device in devices) {
                val selected = device.id == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) NxPalette.Ready.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onSelect(if (selected) null else device.id) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (device.vibration) device.descriptor else device.descriptor + " · " + stringResource(R.string.pad_center_no_vibration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selected) Icon(Icons.Filled.Bluetooth, null, tint = NxPalette.Ready, modifier = Modifier.size(18.dp))
                }
            }

            val selected = devices.firstOrNull { it.id == selectedId } ?: return@Column
            val profile = profiles[selected.profileKey] ?: PadProfileStore.Profile(selected.profileKey)
            val state = states[selected.id]
            Spacer(Modifier.height(8.dp))

            // 示波：手柄此刻的样子
            GroupLabel(stringResource(R.string.pad_center_scope))
            Spacer(Modifier.height(4.dp))
            ScopeView(state)
            Spacer(Modifier.height(8.dp))

            // 面键：按位置还是按标签（标签属于手柄，不属于游戏）
            val positionLabel = stringResource(R.string.pad_center_position)
            val labelLabel = stringResource(R.string.pad_center_label)
            OptionRow(
                Icons.Filled.Bluetooth,
                stringResource(R.string.pad_center_face_buttons),
                listOf(positionLabel, labelLabel),
                if (profile.labels) labelLabel else positionLabel,
            ) { name -> onLabels(selected, name == labelLabel) }
            HintRow(stringResource(R.string.pad_center_face_hint))
            Spacer(Modifier.height(8.dp))

            // 映射：哪个物理键是哪个游戏键
            GroupLabel(stringResource(R.string.pad_center_mapping))
            Spacer(Modifier.height(4.dp))
            if (capturing != null) HintRow(stringResource(R.string.pad_center_capture))
            Spacer(Modifier.height(4.dp))
            for (target in PadTargets.ALL) {
                val bound = profile.bindings.entries.firstOrNull { it.value == target }?.key
                val default = PadMapping.defaultSourceFor(target, profile.labels)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(target.uppercase(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp))
                    Text(
                        sourceText(bound, default),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.pad_center_rebind),
                        style = MaterialTheme.typography.bodySmall,
                        color = NxPalette.Ready,
                        modifier = Modifier.clickable { onCapture(selected.id, target) }.padding(6.dp),
                    )
                    Text(
                        stringResource(R.string.pad_center_clear),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { bound?.let { onClearBinding(selected, it) } }
                            .padding(6.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            ActionRow(Icons.Filled.Refresh, stringResource(R.string.pad_center_reset), null) { onResetBindings(selected) }

            // 校准
            GroupLabel(stringResource(R.string.pad_center_calibration))
            Spacer(Modifier.height(4.dp))
            CalibrationSlider(stringResource(R.string.pad_center_deadzone), profile.calibration.deadzone,
                PadCalibration.DEADZONE_RANGE) { value ->
                onCalibration(selected, profile.calibration.copy(deadzone = value))
            }
            CalibrationSlider(stringResource(R.string.pad_center_trigger), profile.calibration.trigger,
                PadCalibration.TRIGGER_RANGE) { value ->
                onCalibration(selected, profile.calibration.copy(trigger = value))
            }
            Spacer(Modifier.height(4.dp))
            ActionRow(Icons.Filled.Vibration, stringResource(R.string.pad_center_vibrate), null) { onVibrate(selected) }
        }
    }
}

/** 一个绑定显示成什么：玩家自己绑的源，没有就显示出厂的那个。 */
@Composable
private fun sourceText(bound: String?, default: String?): String = when {
    bound != null && bound != PadSources.DISABLED -> bound
    bound == PadSources.DISABLED -> stringResource(R.string.pad_center_disabled)
    default != null -> stringResource(R.string.pad_center_default) + " · " + default
    else -> stringResource(R.string.pad_center_unbound)
}

/** 示波：面键、十字键、两根摇杆与两个扳机。 */
@Composable
private fun ScopeView(state: PadTestState?) {
    val pressed = state?.pressed.orEmpty()
    val accent = NxPalette.Ready
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("x", "y", "a", "b").forEach { name ->
                ScopeKey(name, pressed.contains(name), accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ScopeStick(state?.leftX ?: 0f, state?.leftY ?: 0f, accent)
            ScopeStick(state?.rightX ?: 0f, state?.rightY ?: 0f, accent)
            ScopeTrigger(state?.leftTrigger ?: 0f, accent)
            ScopeTrigger(state?.rightTrigger ?: 0f, accent)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.pad_center_events) + ": " + (state?.eventCount ?: 0),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScopeKey(name: String, on: Boolean, accent: Color) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (on) accent else Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) { Text(name.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ScopeStick(x: Float, y: Float, accent: Color) {
    Canvas(Modifier.size(56.dp)) {
        drawCircle(color = Color.White.copy(alpha = 0.12f), radius = size.minDimension / 2)
        drawCircle(color = Color.White.copy(alpha = 0.25f), radius = size.minDimension / 2, style = Stroke(width = 1.dp.toPx()))
        drawCircle(
            color = if (x == 0f && y == 0f) Color.White.copy(alpha = 0.35f) else accent,
            radius = 5.dp.toPx(),
            center = Offset(size.width / 2 + x * size.width * 0.4f, size.height / 2 + y * size.height * 0.4f),
        )
    }
}

@Composable
private fun ScopeTrigger(value: Float, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.width(16.dp).height(56.dp)) {
            drawRoundRect(color = Color.White.copy(alpha = 0.12f))
            val h = size.height * value.coerceIn(0f, 1f)
            drawRoundRect(color = accent, topLeft = Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
        }
    }
}

/** 一条校准的滑条。 */
@Composable
private fun CalibrationSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
