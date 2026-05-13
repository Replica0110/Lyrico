package com.lonx.lyrico.ui.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun ActionBottomSheet(
    show: Boolean,
    title: String? = null,
    enableNestedScroll: Boolean = true,
    allowDismiss: Boolean = true,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    WindowBottomSheet(
        show = show,
        enableNestedScroll = enableNestedScroll,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        allowDismiss = allowDismiss,
        title = title
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
        ) {
            content()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                actions()
            }
        }
    }
}