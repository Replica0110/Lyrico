@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.base

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.ItemEdit
import com.moriafly.salt.ui.RoundedColumn

/** Native input controls and non-auto-closing confirm semantics, with the shared IME host. */
@Composable
internal fun SaltInputDialog(
    title: String,
    text: String,
    onChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    cancelText: String,
    confirmText: String,
    hint: String? = null,
) {
    SaltConfirmDialog(
        show = true, title = title, onDismissRequest = onDismissRequest, onConfirm = onConfirm,
        cancelText = cancelText, confirmText = confirmText, dismissOnConfirm = false,
    ) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        RoundedColumn(paddingValues = PaddingValues(0.dp)) {
            ItemEdit(text = text, onChange = onChange, hint = hint,
                modifier = Modifier.focusRequester(focus))
        }
    }
}
