@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.sheet.salt

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

/** Salt 的 Item 菜单弹层：标题加卡片分组，外壳与生命周期由 [SaltBottomSheet] 统一提供。 */
@Composable
internal fun SaltMenuBottomSheet(
    show: Boolean,
    label: String,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    allowDismiss: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    SaltBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        title = label,
        // 菜单行不自滚，由弹层统一滚动；分组背景交给 RoundedColumn。
        scrollContent = true,
        allowDismiss = allowDismiss,
        content = { com.moriafly.salt.ui.RoundedColumn(content = content) },
    )
}
