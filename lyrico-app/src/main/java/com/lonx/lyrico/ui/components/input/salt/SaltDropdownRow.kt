@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.input.salt

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.moriafly.salt.ui.ItemDropdown
import com.moriafly.salt.ui.popup.PopupMenuItem

/** [selectedIndex] 越界时显示空值。 */
@Composable
internal fun SaltDropdownRow(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    sub: String? = null,
    icon: ImageVector? = null,
    tone: PreferenceIconTone = PreferenceIconTone.Tools,
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) {
    ItemDropdown(
        text = title,
        value = labels.getOrNull(selectedIndex).orEmpty(),
        sub = sub,
        enabled = enabled,
        iconPainter = icon?.let { rememberVectorPainter(it) },
        iconColor = tone.color(),
    ) {
        labels.forEachIndexed { index, label ->
            PopupMenuItem(
                text = label,
                selected = index == selectedIndex,
                onClick = {
                    state.dismiss()
                    onSelectedIndexChange(index)
                },
            )
        }
    }
}
