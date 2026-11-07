@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.salt

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.RoundedColumn

/** Native input controls and non-auto-closing confirm semantics, with the shared IME host. */
@Composable
internal fun SaltInputDialog(
    title: String,
    state: TextFieldState,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    cancelText: String,
    confirmText: String,
    hint: String? = null,
    confirmEnabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    status: (@Composable () -> Unit)? = null,
    show: Boolean = true,
    onDismissFinished: () -> Unit = {},
) {
    SaltConfirmDialog(
        show = show,
        onDismissFinished = onDismissFinished,
        title = title,
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
        cancelText = cancelText,
        confirmText = confirmText,
        dismissOnConfirm = false,
        confirmEnabled = confirmEnabled,
    ) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        RoundedColumn(paddingValues = PaddingValues(0.dp)) {
            SaltTextInput(
                state = state,
                hint = hint,
                hintMaxLines = 3,
                readOnly = readOnly,
                keyboardOptions = keyboardOptions,
                modifier = Modifier.focusRequester(focus),
            )
        }
        status?.invoke()
    }
}
