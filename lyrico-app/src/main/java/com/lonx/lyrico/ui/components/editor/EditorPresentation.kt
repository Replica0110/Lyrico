package com.lonx.lyrico.ui.components.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.salt.SaltBottomSheet
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.ItemOuterTitle
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** Single native window, with the existing Miuix content retained by the caller. */
@Composable
internal fun EditorSheet(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    enableNestedScroll: Boolean = false,
    endAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT)
        SaltBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            header = {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { ItemOuterTitle(title) }
                    endAction?.invoke()
                    Spacer(Modifier.width(12.dp))
                }
            },
            content = content,
        )
    else
        WindowBottomSheet(
            show = show,
            title = title,
            enableNestedScroll = enableNestedScroll,
            onDismissRequest = onDismissRequest,
            endAction = { endAction?.invoke() },
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
}
