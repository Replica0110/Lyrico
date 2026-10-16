package com.lonx.lyrico.ui.components.poster

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.base.ItemMenuAction

/** Artist poster actions; local images share one entry and a separate destination choice. */
@Composable
fun ArtistPosterActionsSheet(
    show: Boolean,
    onAddLocalPoster: () -> Unit,
    onViewPoster: () -> Unit,
    canViewPoster: Boolean,
    posterLoading: Boolean = false,
    posterError: Boolean = false,
    onDismissRequest: () -> Unit,
    onRemoveFromSongs: () -> Unit,
    onExportEmbedded: () -> Unit,
    enabled: Boolean = true
) {
    com.lonx.lyrico.ui.components.base.ItemMenuBottomSheet(
        show = show, label = stringResource(R.string.label_artist_image_options),
        enableNestedScroll = false, onDismissRequest = onDismissRequest
    ) {
        ItemMenuAction(
            icon = LucideIcons.Image, tone = PreferenceIconTone.Library,
            title = stringResource(R.string.poster_view_artwork),
            summary = if (posterError) stringResource(R.string.artist_poster_read_error)
                else if (!posterLoading && !canViewPoster) stringResource(R.string.artist_poster_missing_title) else null,
            enabled = enabled && canViewPoster,
            onClick = onViewPoster
        )
        // Salt 菜单里不能出现 Miuix 进度圈：SaltUI 3.0.0-beta02 无进度控件，用 Material3 回退并取 Salt 颜色。
        if (posterLoading) {
            if (com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
                com.lonx.lyrico.data.model.UiEngine.SALT
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = com.moriafly.salt.ui.SaltTheme.colors.highlight,
                    trackColor = com.moriafly.salt.ui.SaltTheme.colors.stroke,
                )
                }
            } else {
                top.yukonga.miuix.kmp.basic.CircularProgressIndicator()
            }
        }
        ItemMenuAction(
            icon = LucideIcons.ImageAdd, tone = PreferenceIconTone.Library,
            title = stringResource(R.string.label_change_artist_image),
            summary = stringResource(R.string.artist_poster_add_local_summary),
            enabled = enabled,
            onClick = onAddLocalPoster
        )
        ItemMenuAction(
            icon = LucideIcons.Trash, tone = PreferenceIconTone.Danger,
            title = stringResource(R.string.poster_remove_artwork),
            summary = stringResource(R.string.artist_poster_remove_summary),
            enabled = enabled,
            onClick = onRemoveFromSongs
        )
        ItemMenuAction(
            icon = LucideIcons.FileUp, tone = PreferenceIconTone.Tools,
            title = stringResource(R.string.poster_export_embedded),
            summary = stringResource(R.string.artist_poster_export_summary),
            enabled = enabled,
            onClick = onExportEmbedded
        )
    }
}

@Composable
fun ArtistPosterDestinationPreferences(
    hasPosterFolder: Boolean,
    enabled: Boolean,
    onEmbedToSongs: () -> Unit,
    onSaveToFolder: () -> Unit
) {
    ItemMenuAction(
        icon = LucideIcons.FileMusic, tone = PreferenceIconTone.Library,
        title = stringResource(R.string.label_artist_poster_embed_to_songs),
        summary = stringResource(R.string.artist_poster_embed_summary),
        enabled = enabled,
        onClick = onEmbedToSongs
    )
    ItemMenuAction(
        icon = LucideIcons.FolderPlus, tone = PreferenceIconTone.Library,
        title = stringResource(R.string.label_artist_poster_add_to_folder),
        enabled = enabled,
        summary = stringResource(if (hasPosterFolder) R.string.artist_poster_save_folder_summary else R.string.artist_poster_select_folder_summary),
        onClick = onSaveToFolder
    )
}
