package com.lonx.lyrico.ui.components.base

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.salt.SaltBottomSheet
import com.lonx.lyrico.ui.components.salt.SaltSheetActions
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.ItemOuterTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    groupContent: Boolean = false,
    /**
     * Salt 侧滚动出口：默认由外壳滚动，保证内容超窗也能滚到底部。
     * 内容自带 LazyColumn/verticalScroll 时必须显式置 false，避免两个纵向滚动容器叠加。
     */
    saltScrollContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            allowDismiss = allowDismiss,
            header = {
                title?.let { ItemOuterTitle(text = it) }
            },
            footer = {
                SaltSheetActions(cancelText, confirmText, onCancel, onConfirm, confirmEnabled)
            },
            groupContent = groupContent,
            scrollContent = saltScrollContent,
            content = content,
        )
        return
    }
    ActionBottomSheet(
        show = show,
        title = title,
        enableNestedScroll = enableNestedScroll,
        allowDismiss = allowDismiss,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        content = content,
        startAction =  {
            TextButton(
                colors = ButtonColors(
                    containerColor = MiuixTheme.colorScheme.surface,
                    contentColor = MiuixTheme.colorScheme.onSurface,
                    disabledContainerColor = MiuixTheme.colorScheme.surface,
                    disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface
                ),
                onClick = {
                    onCancel()
                }
            ){
                Text(text = cancelText)
            }
        },
        endAction =  {
            TextButton(
                enabled = confirmEnabled,
                colors = ButtonColors(
                    containerColor = MiuixTheme.colorScheme.surface,
                    contentColor = MiuixTheme.colorScheme.primary,
                    disabledContainerColor = MiuixTheme.colorScheme.surface,
                    disabledContentColor = MiuixTheme.colorScheme.disabledPrimary
                ),
                onClick = {
                    onConfirm()
                }
            ){
                Text(text = confirmText, color = MiuixTheme.colorScheme.primary)
            }
        }
    )
}
