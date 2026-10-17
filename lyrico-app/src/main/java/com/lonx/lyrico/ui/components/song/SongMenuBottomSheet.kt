package com.lonx.lyrico.ui.components.song

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.ui.components.base.ItemMenuAction

@Composable
fun SongMenuBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    enableNestedScroll: Boolean = true,
    song: SongEntity,
    onPlay: () -> Unit,
    showInfo: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
) {
    val songTitle = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName
    val text =
        song.artist.takeIf { !it.isNullOrBlank() }?.let { "$songTitle - $it" } ?: songTitle
    com.lonx.lyrico.ui.components.base.ItemMenuBottomSheet(
        show = show, label = text, onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished, enableNestedScroll = enableNestedScroll
    ) {
    ItemMenuAction(
        icon = LucideIcons.Play, tone = PreferenceIconTone.Appearance,
        title = stringResource(R.string.menu_action_play),
        summary = stringResource(R.string.menu_action_play_sub),
        onClick = { onPlay() }
    )
    ItemMenuAction(
        icon = LucideIcons.Info, tone = PreferenceIconTone.Tools,
        title = stringResource(R.string.menu_action_info),
        onClick = { showInfo() }
    )
    ItemMenuAction(
        icon = LucideIcons.Share2, tone = PreferenceIconTone.Library,
        title = stringResource(R.string.menu_action_share),
        onClick = { onShare() }
    )
    ItemMenuAction(
        icon = LucideIcons.Pencil, tone = PreferenceIconTone.Lyrics,
        title = stringResource(R.string.menu_action_rename),
        onClick = { onRename() }
    )
    ItemMenuAction(
        icon = LucideIcons.Trash, tone = PreferenceIconTone.Danger,
        title = stringResource(R.string.menu_action_delete),
        summary = stringResource(R.string.menu_action_delete_sub),
        onClick = { onDelete() }
    )
    }
}
