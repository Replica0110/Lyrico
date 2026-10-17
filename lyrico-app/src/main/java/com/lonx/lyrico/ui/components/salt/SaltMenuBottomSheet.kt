@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.salt

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.moriafly.salt.ui.ItemOuterTitle

/** Salt 的 Item 菜单弹层：标题加卡片分组，外壳与生命周期由 [SaltBottomSheet] 统一提供。 */
@Composable
internal fun SaltMenuBottomSheet(
    show: Boolean,
    label: String,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    /** 契约完整性：菜单当前都可关闭，但外壳必须能表达"不可关闭"（进行中菜单等场景）。 */
    allowDismiss: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    SaltBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        header = { ItemOuterTitle(text = label) },
        // 菜单行自身不滚动，因此由弹层统一滚动；行本身不带分组背景，交给 RoundedColumn。
        scrollContent = true,
        allowDismiss = allowDismiss,
        content = { com.moriafly.salt.ui.RoundedColumn(content = content) },
    )
}
