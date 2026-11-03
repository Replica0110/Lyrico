@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.salt.SaltTextInput
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.RoundedColumn
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 「添加自定义标签」输入弹窗，单曲编辑与批量编辑共用同一套输入样式：
 * Salt 用与其他输入弹窗一致的占位提示输入框，Miuix 用标签转占位的 TextField。
 * 键名为空时不能确认；每次打开都会清空上一次的输入。
 */
@Composable
internal fun CustomTagInputDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (key: String, value: String) -> Unit,
) {
    val keyState = remember { TextFieldState("") }
    val valueState = remember { TextFieldState("") }
    val keyFocus = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show) {
            keyState.setTextAndPlaceCursorAtEnd("")
            valueState.setTextAndPlaceCursorAtEnd("")
        }
    }

    YesNoDialog(
        show = show,
        title = stringResource(R.string.action_add_custom_tag),
        onDismissRequest = onDismissRequest,
        confirmEnabled = keyState.text.isNotBlank(),
        onConfirm = {
            val key = keyState.text.toString()
            if (key.isNotBlank()) onConfirm(key, valueState.text.toString())
        },
        content = {
            LaunchedEffect(show) { if (show) keyFocus.requestFocus() }
            if (LocalUiEngine.current == UiEngine.SALT) {
                // 两个输入框各自独立成卡片，与页面里的分组输入保持一致。
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                        SaltTextInput(
                            state = keyState,
                            hint = stringResource(R.string.label_custom_tag_name),
                            hintMaxLines = 3,
                            modifier = Modifier.focusRequester(keyFocus),
                        )
                    }
                    RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                        SaltTextInput(
                            state = valueState,
                            hint = stringResource(R.string.label_custom_tag_value),
                            hintMaxLines = 3,
                        )
                    }
                }
            } else {
                TextField(
                    state = keyState,
                    label = stringResource(R.string.label_custom_tag_name),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(keyFocus),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    state = valueState,
                    label = stringResource(R.string.label_custom_tag_value),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        },
    )
}
