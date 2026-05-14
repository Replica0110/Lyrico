package com.lonx.lyrico.ui.components.song

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More

@Composable
fun SongListItemActions(
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onShowMenu: () -> Unit,
) {
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
                imageVector = MiuixIcons.More,
                contentDescription = "More"
            )
        }
    }
}