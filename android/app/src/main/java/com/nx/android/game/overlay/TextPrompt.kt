package com.nx.android.game.overlay

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.nx.android.R

/**
 * 游戏的文本输入：游戏要文字时（nn::swkbd）宿主挂起游戏的线程并通知 App，App 用系统的输入法问玩家，
 * 确认后把文字交回宿主。键盘是系统的那一套，游戏画面上不再自绘键盘。
 */
@Composable
fun TextPrompt(title: String, initial: String, max: Int, onOk: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial.take(if (max > 0) max else initial.length)) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (max <= 0 || it.length <= max) text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(onDone = { onOk(text) }),
            )
        },
        confirmButton = { TextButton(onClick = { onOk(text) }) { Text(stringResource(R.string.common_ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) } },
    )
}
