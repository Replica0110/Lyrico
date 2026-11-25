@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.settings.editing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.ChipGrid
import com.lonx.lyrico.ui.components.ManagedChip
import com.lonx.lyrico.ui.components.input.salt.SaltTextInput
import com.lonx.lyrico.ui.screens.settings.CustomTagKeyError
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun AddCustomTagDialog(
    show: Boolean,
    availableKeys: List<String>,
    error: CustomTagKeyError?,
    onClearInputError: () -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val inputState = remember(show) { TextFieldState("") }
    val typed = inputState.text.toString()
    val onSelectKey: (String) -> Unit = { key ->
        inputState.setTextAndPlaceCursorAtEnd(key)
        onClearInputError()
    }

    if (LocalUiEngine.current == UiEngine.SALT) {
        // Salt 弹窗同步销毁，用“曾显示”标记在关闭后补发一次完成回调。
        var wasShown by remember { mutableStateOf(false) }
        LaunchedEffect(show) {
            if (show) {
                wasShown = true
            } else if (wasShown) {
                wasShown = false
                onDismissFinished()
            }
        }
        val errorText = when (error) {
            null -> stringResource(R.string.custom_tag_key_hint)
            CustomTagKeyError.EMPTY -> stringResource(R.string.invalid_empty_input)
            CustomTagKeyError.INVALID -> stringResource(R.string.custom_tag_key_invalid)
            CustomTagKeyError.DUPLICATE -> stringResource(R.string.duplicate_item)
        }
        com.lonx.lyrico.ui.components.dialog.salt.SaltConfirmDialog(
            show = show, title = stringResource(R.string.custom_tag_add_key),
            onDismissRequest = onDismiss, onConfirm = { onConfirm(typed) },
            cancelText = stringResource(R.string.cancel), confirmText = stringResource(R.string.confirm),
            dismissOnConfirm = false,
        ) {
            CustomTagSuggestions(availableKeys, onSelectKey)
            RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                SaltTextInput(
                    state = inputState,
                    hint = stringResource(R.string.custom_tag_key_hint),
                    hintMaxLines = 3,
                    error = if (error == null) null else errorText,
                )
            }
        }
        return
    }

    WindowDialog(
        show = show,
        title = stringResource(R.string.custom_tag_add_key),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CustomTagSuggestions(availableKeys, onSelectKey)
            TextField(
                state = inputState,
                label = stringResource(R.string.custom_tag_key_hint),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth(),
                lineLimits = TextFieldLineLimits.MultiLine(),
            )
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (error) {
                        CustomTagKeyError.EMPTY -> stringResource(R.string.invalid_empty_input)
                        CustomTagKeyError.INVALID -> stringResource(R.string.custom_tag_key_invalid)
                        CustomTagKeyError.DUPLICATE -> stringResource(R.string.duplicate_item)
                    },
                    color = MiuixTheme.colorScheme.error,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { onConfirm(typed) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/** 候选标签只填入草稿，统一由对话框的确认操作添加。 */
@Composable
private fun CustomTagSuggestions(keys: List<String>, onSelect: (String) -> Unit) {
    if (keys.isEmpty()) return

    val salt = LocalUiEngine.current == UiEngine.SALT
    if (salt) {
        com.moriafly.salt.ui.Text(
            text = stringResource(R.string.custom_tag_library_section),
            color = SaltTheme.colors.subText,
            style = SaltTheme.textStyles.sub,
        )
    } else {
        Text(
            text = stringResource(R.string.custom_tag_library_section),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
    Spacer(Modifier.height(8.dp))
    ChipGrid(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        keys.forEach { key ->
            ManagedChip(text = key, onClick = { onSelect(key) })
        }
    }
    Spacer(Modifier.height(16.dp))
}
