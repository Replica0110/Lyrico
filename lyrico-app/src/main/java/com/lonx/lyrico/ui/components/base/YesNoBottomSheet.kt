package com.lonx.lyrico.ui.components.base

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R

/** Confirm-sheet semantics are shared; each engine renders its own sheet chrome and actions. */
@Composable
fun YesNoBottomSheet(
    show: Boolean,
    title: String? = null,
    enableNestedScroll: Boolean = true,
    allowDismiss: Boolean = true,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onCancel: () -> Unit = onDismissRequest,
    onConfirm: () -> Unit,
    cancelText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    confirmEnabled: Boolean = true,
    cancelEnabled: Boolean = true,
    groupContent: Boolean = false,
    scrollContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ActionBottomSheet(
        show = show,
        title = title,
        enableNestedScroll = enableNestedScroll,
        allowDismiss = allowDismiss,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        groupContent = groupContent,
        scrollContent = scrollContent,
        footer = {
            SheetConfirmActions(cancelText, confirmText, onCancel, onConfirm, confirmEnabled, cancelEnabled)
        },
        content = content,
    )
}
