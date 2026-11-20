package com.lonx.lyrico.ui.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.sheet.miuix.MiuixBottomSheet
import com.lonx.lyrico.ui.components.sheet.salt.SaltBottomSheet
import com.lonx.lyrico.ui.theme.LocalUiEngine

/** 操作弹层：业务内容共用，外壳按引擎区分（Salt 复用 SaltBottomSheet）。 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ActionBottomSheet(
    show: Boolean,
    title: String? = null,
    enableNestedScroll: Boolean = true,
    allowDismiss: Boolean = true,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
    tools: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    groupContent: Boolean = false,
    /** 正文滚动出口：内容自带 LazyColumn/verticalScroll 时置 false，避免嵌套滚动。 */
    scrollContent: Boolean = true,
    saltFooter: (@Composable () -> Unit)? = null,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            allowDismiss = allowDismiss,
            footer = saltFooter ?: footer,
            title = title,
            header = {
                Column(Modifier.fillMaxWidth()) {
                    if (tools != null) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tools()
                        }
                    }
                }
            },
            groupContent = groupContent,
            scrollContent = scrollContent,
            content = content,
        )
        return
    }
    MiuixBottomSheet(
        show = show,
        enableNestedScroll = enableNestedScroll,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        allowDismiss = allowDismiss,
        title = title,
        tools = tools,
        footer = footer,
        scrollContent = scrollContent,
        content = content,
    )
}
