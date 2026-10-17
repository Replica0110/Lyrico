package com.lonx.lyrico.ui.components.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.components.salt.SaltTextInput
import com.moriafly.salt.ui.RoundedColumn

/** Shared state-based input, styled with Salt surfaces and a stable title/action decoration. */
@Composable
internal fun SaltEditorField(
    label: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    grouped: Boolean = true,
    hint: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val input: @Composable () -> Unit = {
        SaltTextInput(
            state = state,
            label = label,
            hint = hint,
            lineLimits = lineLimits,
            actionContent = {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            },
        )
    }
    if (grouped) RoundedColumn(modifier = modifier) { input() }
    else Box(modifier.fillMaxWidth()) { input() }
}

@Composable
internal fun SaltEditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    grouped: Boolean = true,
    hint: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    SaltEditorField(
        label = label,
        state = rememberEditorTextFieldState(value, onValueChange),
        modifier = modifier,
        lineLimits =
            if (multiline)
                TextFieldLineLimits.MultiLine(minHeightInLines = 6, maxHeightInLines = 12)
            else TextFieldLineLimits.SingleLine,
        grouped = grouped,
        hint = hint,
        actions = actions,
    )
}

/** Keep the action slot measured when the field is unchanged, without an invisible click target. */
@Composable
internal fun SaltEditorUndoAction(visible: Boolean, onUndo: () -> Unit) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        if (visible) {
            SaltIconButton(
                icon = LucideIcons.Undo,
                label = stringResource(R.string.action_undo_changes),
                onClick = onUndo,
                size = 32.dp,
                iconSize = 18.dp,
            )
        }
    }
}
