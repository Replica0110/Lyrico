package com.lonx.lyrico.ui.components.base

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

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
    WindowBottomSheet(show = show, title = label, enableNestedScroll = enableNestedScroll,
        onDismissRequest = onDismissRequest, onDismissFinished = onDismissFinished) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Card(modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                content()
            }
        }
    }
}
