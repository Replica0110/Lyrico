package com.lonx.lyrico.ui.components.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.base.ItemMenuAction
import com.lonx.lyrico.ui.components.base.ItemMenuBottomSheet

@Composable
fun AlbumActionBottomSheet(
    show: Boolean,
    albumName: String,
    isCalculatingReplayGain: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCalculateReplayGain: () -> Unit
) {
    val actions: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        ItemMenuAction(
            icon = LucideIcons.Volume2, tone = PreferenceIconTone.Appearance,
            title = stringResource(R.string.menu_action_calculate_album_replay_gain),
            enabled = !isCalculatingReplayGain,
            onClick = {
                if (!isCalculatingReplayGain) {
                    onCalculateReplayGain()
                }
            }
        )
        ItemMenuAction(
            icon = LucideIcons.Share2, tone = PreferenceIconTone.Tools,
            title = stringResource(R.string.menu_action_share_album),
            onClick = onShare
        )
        ItemMenuAction(
            icon = LucideIcons.Trash, tone = PreferenceIconTone.Danger,
            title = stringResource(R.string.menu_action_delete_album),
            summary = stringResource(R.string.menu_action_delete_album_sub),
            onClick = onDelete
        )
    }
    val label = albumName.ifBlank { stringResource(R.string.album_detail_title) }
    ItemMenuBottomSheet(
        show = show,
        label = label,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        content = actions,
    )
}
