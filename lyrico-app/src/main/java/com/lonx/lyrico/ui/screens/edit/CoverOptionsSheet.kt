package com.lonx.lyrico.ui.screens.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.sheet.ItemMenuAction
import com.lonx.lyrico.ui.components.editor.EditorSheet
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone

@Composable
internal fun CoverOptionsSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    hasCoverSearchSources: Boolean,
    onSearchCover: () -> Unit,
    onChangeCover: () -> Unit,
    onSelectSameAlbumCover: () -> Unit,
    onRemoveCover: () -> Unit,
    canExportOrCropCover: Boolean,
    onExportCover: () -> Unit,
    onCropCover: () -> Unit,
) {
    EditorSheet(
        show = show,
        enableNestedScroll = false,
        title = stringResource(R.string.label_cover_options),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            EditorMenuGroup {
                if (hasCoverSearchSources) {
                    ItemMenuAction(
                        icon = LucideIcons.ImageSearch,
                        tone = PreferenceIconTone.Library,
                        title = stringResource(R.string.action_search_cover),
                        onClick = onSearchCover
                    )
                }
                ItemMenuAction(
                    icon = LucideIcons.ImageAdd,
                    tone = PreferenceIconTone.Lyrics,
                    title = stringResource(R.string.label_change_cover),
                    onClick = onChangeCover
                )
                ItemMenuAction(
                    icon = LucideIcons.Disc,
                    tone = PreferenceIconTone.Pink,
                    title = stringResource(R.string.label_select_same_album_cover),
                    onClick = onSelectSameAlbumCover
                )
                ItemMenuAction(
                    icon = LucideIcons.Trash,
                    tone = PreferenceIconTone.Danger,
                    title = stringResource(R.string.label_remove_cover),
                    onClick = onRemoveCover
                )
                if (canExportOrCropCover) {
                    ItemMenuAction(
                        icon = LucideIcons.Download,
                        tone = PreferenceIconTone.Teal,
                        title = stringResource(R.string.label_save_cover),
                        onClick = onExportCover
                    )
                    ItemMenuAction(
                        icon = LucideIcons.Crop,
                        tone = PreferenceIconTone.Orange,
                        title = stringResource(R.string.label_crop_cover),
                        onClick = onCropCover
                    )
                }
            }
        }
    }
}
