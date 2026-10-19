package com.lonx.lyrico.ui.components.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** Optional title, tools and footer; callers supply only what the task needs. */
@Composable
internal fun MiuixBottomSheet(
    show: Boolean,
    title: String? = null,
    enableNestedScroll: Boolean = true,
    allowDismiss: Boolean = true,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    tools: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    scrollContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    WindowBottomSheet(
        show = show,
        title = title?.takeIf { it.isNotBlank() },
        enableNestedScroll = enableNestedScroll,
        allowDismiss = allowDismiss,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            tools?.let {
                SheetTools(it)
            }
            Column(
                Modifier.fillMaxWidth()
                    .weight(1f, fill = false)
                    .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(bottom = if (footer == null) 16.dp else 12.dp),
                content = content,
            )
            footer?.let {
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { it() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetTools(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
