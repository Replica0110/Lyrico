package com.lonx.lyrico.ui.components.base

import com.lonx.lyrico.ui.components.salt.SaltMenuBottomSheet
import com.lonx.lyrico.ui.components.miuix.MiuixBottomSheet
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The existing song item menu layout, shared by artist and poster file menus. */
@Composable
fun ItemMenuBottomSheet(
    show: Boolean,
    label: String,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    enableNestedScroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT) {
        SaltMenuBottomSheet(
            show = show,
            label = label,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            content = content,
        )
        return
    }
    MiuixBottomSheet(show = show, title = label,
        enableNestedScroll = enableNestedScroll,
        scrollContent = true,
        onDismissRequest = onDismissRequest, onDismissFinished = onDismissFinished) {
        Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
            content()
        }
    }
}
