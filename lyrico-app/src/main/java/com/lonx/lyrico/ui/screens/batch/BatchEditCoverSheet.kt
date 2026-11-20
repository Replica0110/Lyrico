@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lonx.lyrico.R
import com.lonx.lyrico.data.artwork.CoverRequest
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.base.ItemMenuAction
import com.lonx.lyrico.ui.components.base.ItemMenuBottomSheet
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.image.PreviewableImage
import com.lonx.lyrico.ui.components.image.rememberTintedPainter
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.sheet.miuix.MiuixBottomSheet
import com.lonx.lyrico.ui.components.sheet.salt.SaltBottomSheet
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.theme.LyricoColors
import com.moriafly.salt.ui.Card as SaltCard
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text as SaltText
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 批量编辑的封面入口：换图、从已选歌曲挑、同专辑、移除、撤销。
 */
@Composable
internal fun BatchEditCoverMenuSheet(
    show: Boolean,
    canRevert: Boolean,
    onDismiss: () -> Unit,
    onChangeCover: () -> Unit,
    onPickFromSongs: () -> Unit,
    onSameAlbum: () -> Unit,
    onRemove: () -> Unit,
    onRevert: () -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        ItemMenuBottomSheet(
            show = show,
            label = stringResource(R.string.label_cover),
            onDismissRequest = onDismiss
        ) {
            ItemMenuAction(stringResource(R.string.batch_edit_cover_change), LucideIcons.ImageAdd, PreferenceIconTone.Appearance) {
                onDismiss()
                onChangeCover()
            }
            ItemMenuAction(stringResource(R.string.batch_edit_select_cover_from_selected_songs), LucideIcons.ImageLibrary, PreferenceIconTone.Tools) {
                onDismiss()
                onPickFromSongs()
            }
            ItemMenuAction(stringResource(R.string.label_select_same_album_cover), LucideIcons.Disc, PreferenceIconTone.Library) {
                onDismiss()
                onSameAlbum()
            }
            ItemMenuAction(stringResource(R.string.batch_edit_cover_remove), LucideIcons.Trash, PreferenceIconTone.Danger) {
                onDismiss()
                onRemove()
            }
            if (canRevert) {
                ItemMenuAction(stringResource(R.string.batch_edit_cover_revert), LucideIcons.Undo, PreferenceIconTone.Lyrics) {
                    onDismiss()
                    onRevert()
                }
            }
        }
        return
    }

    MiuixBottomSheet(
        show = show,
        onDismissRequest = onDismiss
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .scrollEndHaptic()
                .overScrollVertical(),
            overscrollEffect = null
        ) {
            item {
                Card(
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                ) {
                    ArrowPreference(
                        startAction = { PreferenceIcon(LucideIcons.ImageAdd, PreferenceIconTone.Appearance) },
                        title = stringResource(R.string.batch_edit_cover_change),
                        onClick = {
                            onDismiss()
                            onChangeCover()
                        }
                    )
                    ArrowPreference(
                        startAction = { PreferenceIcon(LucideIcons.ImageLibrary, PreferenceIconTone.Tools) },
                        title = stringResource(R.string.batch_edit_select_cover_from_selected_songs),
                        onClick = {
                            onDismiss()
                            onPickFromSongs()
                        }
                    )
                    ArrowPreference(
                        startAction = { PreferenceIcon(LucideIcons.Disc, PreferenceIconTone.Library) },
                        title = stringResource(R.string.label_select_same_album_cover),
                        onClick = {
                            onDismiss()
                            onSameAlbum()
                        }
                    )
                    ArrowPreference(
                        startAction = { PreferenceIcon(LucideIcons.Trash, PreferenceIconTone.Danger) },
                        title = stringResource(R.string.batch_edit_cover_remove),
                        onClick = {
                            onDismiss()
                            onRemove()
                        }
                    )
                    if (canRevert) {
                        ArrowPreference(
                            startAction = { PreferenceIcon(LucideIcons.Undo, PreferenceIconTone.Lyrics) },
                            title = stringResource(R.string.batch_edit_cover_revert),
                            onClick = {
                                onDismiss()
                                onRevert()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 从已选歌曲里挑封面：展示每首歌现有的封面缩略图。
 */
@Composable
internal fun BatchEditCoverPickerSheet(
    show: Boolean,
    loading: Boolean,
    covers: List<BatchEditSelectableCover>,
    onDismiss: () -> Unit,
    onPick: (BatchEditSelectableCover) -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBottomSheet(
            show, onDismiss,
            title = stringResource(R.string.batch_edit_select_cover_from_selected_songs)
        ) {
            LazyVerticalGrid(
                GridCells.Adaptive(96.dp),
                Modifier.heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loading || covers.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    if (loading) {
                        SaltText(
                            stringResource(R.string.batch_edit_loading_selected_values),
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        IllustratedEmptyState(
                            kind = EmptyStateKind.Cover,
                            title = stringResource(R.string.batch_edit_no_selected_covers),
                            compact = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (!loading) gridItems(covers, key = { "${it.sourceUri}\n${it.fileLastModified}" }) { option ->
                    SaltCard {
                        Column(
                            Modifier.clickable { onPick(option) }.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PreviewableImage(
                                CoverRequest(option.previewUri.toUri(), option.fileLastModified), null,
                                onClick = { onPick(option) },
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            SaltText(option.title, style = SaltTheme.textStyles.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (option.summary.isNotBlank()) SaltText(
                                option.summary,
                                style = SaltTheme.textStyles.sub,
                                color = SaltTheme.colors.subText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        return
    }

    MiuixBottomSheet(
        show = show,
        enableNestedScroll = false,
        title = stringResource(R.string.batch_edit_select_cover_from_selected_songs),
        onDismissRequest = onDismiss
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier
                .fillMaxWidth()
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            overscrollEffect = null
        ) {
            if (loading || covers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (loading) {
                        Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                            top.yukonga.miuix.kmp.basic.BasicComponent(
                                title = stringResource(R.string.batch_edit_loading_selected_values)
                            )
                        }
                    } else {
                        IllustratedEmptyState(
                            kind = EmptyStateKind.Cover,
                            title = stringResource(R.string.batch_edit_no_selected_covers),
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (!loading && covers.isNotEmpty()) {
                gridItems(
                    items = covers,
                    key = { "${it.sourceUri}\n${it.fileLastModified}" }
                ) { option ->
                    BatchEditCoverOptionItem(
                        option = option,
                        onClick = { onPick(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchEditCoverOptionItem(
    option: BatchEditSelectableCover,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer)
            .clickable { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PreviewableImage(
            model = CoverRequest(option.previewUri.toUri(), option.fileLastModified),
            onClick = onClick,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(LyricoColors.coverPlaceholder),
            placeholder = rememberTintedPainter(
                painter = painterResource(id = R.drawable.ic_album_24dp),
                tint = LyricoColors.coverPlaceholderIcon
            ),
            error = rememberTintedPainter(
                painter = painterResource(id = R.drawable.ic_album_24dp),
                tint = LyricoColors.coverPlaceholderIcon
            )
        )
        Spacer(modifier = Modifier.height(5.dp))
        top.yukonga.miuix.kmp.basic.Text(
            text = option.title,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (option.summary.isNotBlank()) {
            top.yukonga.miuix.kmp.basic.Text(
                text = option.summary,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
