package com.lonx.lyrico.ui.components.song

import com.lonx.lyrico.ui.components.icons.LucideIcons
import androidx.compose.runtime.Composable
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton

@Composable
fun SongListItemActions(
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onShowMenu: () -> Unit,
) {
    if (com.lonx.lyrico.ui.components.library.LocalLibraryUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT) {
        com.lonx.lyrico.ui.components.library.SaltLibraryItemActions(
            isSelectionMode, isSelected, onToggleSelection, onShowMenu,
        )
        return
    }
    if (isSelectionMode) {
        Checkbox(
            state = if (isSelected) ToggleableState.On else ToggleableState.Off,
            onClick = onToggleSelection
        )
    } else {
        IconButton(
            onClick = onShowMenu
        ) {
            Icon(
                imageVector = LucideIcons.EllipsisVertical,
                contentDescription = "More"
            )
        }
    }
}
