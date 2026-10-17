package com.lonx.lyrico.ui.components.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.miuix.MiuixConfirmDialog
import com.lonx.lyrico.ui.components.salt.SaltConfirmDialog
import com.lonx.lyrico.ui.theme.LocalUiEngine

@Composable
fun YesNoDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    content: @Composable (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onConfirm: () -> Unit,
    cancelText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    confirmEnabled: Boolean = true,
) {
    when (LocalUiEngine.current) {
        UiEngine.SALT ->
            SaltConfirmDialog(
                show = show,
                title = title,
                summary = summary,
                content = content,
                onDismissRequest = onDismissRequest,
                onDismissFinished = onDismissFinished,
                onConfirm = onConfirm,
                cancelText = cancelText,
                confirmText = confirmText,
                confirmEnabled = confirmEnabled,
            )
        UiEngine.MIUIX ->
            MiuixConfirmDialog(
                show = show,
                title = title,
                summary = summary,
                content = content,
                onDismissRequest = onDismissRequest,
                onDismissFinished = onDismissFinished,
                onConfirm = onConfirm,
                cancelText = cancelText,
                confirmText = confirmText,
                confirmEnabled = confirmEnabled,
            )
    }
}
