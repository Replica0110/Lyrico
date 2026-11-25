@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme

@Composable
internal fun AppTabsSalt(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outer = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)

    com.moriafly.salt.ui.Card(outer) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(4.dp)) {
            tabs.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Box(
                    Modifier.weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) SaltTheme.colors.background
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .selectable(selected, role = Role.Tab, onClick = { onSelected(index) })
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    com.moriafly.salt.ui.Text(
                        title,
                        style = SaltTheme.textStyles.main,
                        color =
                            if (selected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
