package com.lonx.lyrico.ui.components.base

import com.lonx.lyrico.ui.components.salt.SaltBottomSheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.ItemOuterTitle
import top.yukonga.miuix.kmp.window.WindowBottomSheet

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
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    groupContent: Boolean = false,
    /**
     * Salt 侧滚动出口：默认由外壳滚动，保证内容超窗也能滚到底部。
     * 内容自带 LazyColumn/verticalScroll 时必须显式置 false，避免两个纵向滚动容器叠加。
     */
    saltScrollContent: Boolean = true,
    saltFooter: (@Composable () -> Unit)? = null,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            allowDismiss = allowDismiss,
            footer = saltFooter,
            header = {
                Column(Modifier.fillMaxWidth()) {
                    title?.let { ItemOuterTitle(text = it) }
                    if (startAction != null || (endAction != null && saltFooter == null)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = when {
                                startAction == null -> Arrangement.End
                                endAction == null || saltFooter != null -> Arrangement.Start
                                else -> Arrangement.SpaceBetween
                            },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            startAction?.invoke()
                            if (saltFooter == null) endAction?.invoke()
                        }
                    }
                }
            },
            groupContent = groupContent,
            scrollContent = saltScrollContent,
            content = content,
        )
        return
    }
    WindowBottomSheet(
        show = show,
        enableNestedScroll = enableNestedScroll,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        allowDismiss = allowDismiss,
        title = title,
        startAction = {
            startAction?.invoke()
        },
        endAction = {
            endAction?.invoke()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            content()
        }
    }
}
