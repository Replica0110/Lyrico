package com.lonx.lyrico.ui.components.bar.salt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.bar.SelectionTopBarState
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

@Composable
internal fun SaltSelectionActions(selection: SelectionTopBarState, compact: Boolean) {
    val label = stringResource(
        if (selection.allSelected) R.string.action_deselect_all else R.string.action_select_all
    )
    val closeLabel = stringResource(R.string.action_close)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (compact) {
            SaltIconButton(
                icon = LucideIcons.ListChecks,
                label = label,
                onClick = selection.onToggleAll,
                tint = SaltTheme.colors.highlight,
            )
            SaltIconButton(
                icon = LucideIcons.Close,
                label = closeLabel,
                onClick = selection.onClose,
                tint = SaltTheme.colors.highlight,
            )
        } else {
            SaltSelectionTextButton(label, selection.onToggleAll)
            SaltSelectionTextButton(closeLabel, selection.onClose)
        }
    }
}

@Composable
private fun SaltSelectionTextButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = SaltTheme.colors.highlight,
            style = SaltTheme.textStyles.main,
            maxLines = 1,
        )
    }
}
