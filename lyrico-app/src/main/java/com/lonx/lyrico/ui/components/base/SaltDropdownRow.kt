@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moriafly.salt.ui.ItemDropdown
import com.moriafly.salt.ui.popup.PopupMenuItem

/**
 * Salt 原生下拉行：标题 + 当前值 + 弹出选项，替代 Miuix 的 WindowDropdownPreference。
 * [labels] 的每一项对应一个选项，[selectedIndex] 超出范围时显示空值。
 */
@Composable
internal fun SaltDropdownRow(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    sub: String? = null,
    onSelectedIndexChange: (Int) -> Unit,
) {
    ItemDropdown(
        text = title,
        value = labels.getOrNull(selectedIndex).orEmpty(),
        modifier = modifier,
        sub = sub,
    ) {
        labels.forEachIndexed { index, label ->
            PopupMenuItem(
                text = label,
                selected = index == selectedIndex,
                onClick = {
                    state.dismiss()
                    onSelectedIndexChange(index)
                }
            )
        }
    }
}
