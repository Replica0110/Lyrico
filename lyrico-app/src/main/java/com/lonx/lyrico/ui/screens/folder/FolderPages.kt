@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.FolderEntity
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.domain.folder.FolderTree
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.library.SelectableLibraryItem
import com.lonx.lyrico.ui.components.scaffold.pageScroll
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.ui.components.song.SongListItemContent
import com.lonx.lyrico.ui.theme.LocalUiEngine
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** 目录页的两个分页：子目录与当前目录的歌曲。标签顺序与分页索引保持一致。 */
internal const val FOLDER_TAB_INDEX = 0
internal const val SONG_TAB_INDEX = 1

@Composable
internal fun FolderChildrenPage(
    folders: List<FolderEntity>,
    folderTree: FolderTree,
    scanningFolderIds: Set<Long>,
    queuedFolderIds: Set<Long>,
    isSelectionMode: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
    onFolderClick: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onRefreshFolder: (FolderEntity) -> Unit,
    onIgnoredChange: (FolderEntity, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .pageScroll(topAppBarScrollBehavior)
            .fillMaxHeight(),
        contentPadding = PaddingValues(bottom = 12.dp),
        overscrollEffect = if (LocalUiEngine.current == UiEngine.SALT) rememberOverscrollEffect() else null,
    ) {
        if (folders.isEmpty()) {
            item("empty") {
                IllustratedEmptyState(
                    kind = EmptyStateKind.Folders,
                    title = stringResource(R.string.empty_folders_title),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(
            items = folders,
            key = { folder -> "folder-${folder.id}" },
        ) { folder ->
            val node = folderTree.nodeOf(folder.id) ?: return@items
            FolderListItem(
                folder = folder,
                node = node,
                isScanning = folder.id in scanningFolderIds,
                isQueued = folder.id in queuedFolderIds,
                isSelectionMode = isSelectionMode,
                canRemove = folder.addedBySaf,
                onClick = { if (!isSelectionMode) onFolderClick(folder) },
                onDelete = { onDeleteFolder(folder) },
                onRefresh = { onRefreshFolder(folder) },
                onIgnoredChange = { ignored -> onIgnoredChange(folder, ignored) },
            )
        }
    }
}

@Composable
internal fun FolderSongsPage(
    songs: List<SongEntity>,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    topAppBarScrollBehavior: ScrollBehavior,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val salt = LocalUiEngine.current == UiEngine.SALT

    LazyColumn(
        modifier = modifier
            .pageScroll(topAppBarScrollBehavior)
            .fillMaxHeight(),
        state = listState,
        contentPadding = PaddingValues(bottom = 12.dp),
        overscrollEffect = if (salt) rememberOverscrollEffect() else null,
    ) {
        if (songs.isEmpty()) {
            item("empty") {
                IllustratedEmptyState(
                    kind = EmptyStateKind.Songs,
                    title = stringResource(R.string.empty_songs_title),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(
            items = songs,
            key = { song -> song.uri.takeIf { it.isNotBlank() && it != "0" } ?: "song-${song.id}" },
        ) { song ->
            val selected = song.uri in selectedSongUris
            SelectableLibraryItem(
                itemKey = song.uri,
                modifier = Modifier.animateItem(),
                isSelectionMode = isSelectionMode,
                isSelected = selected,
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                onClick = { onSongClick(song) },
                onToggleSelection = { onToggleSelection(song) },
                onSwipeSelection = { onSwipeSelection(song) },
            ) {
                SongListItemContent(
                    song = song,
                    trailingContent = {
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            SongListItemActions(
                                isSelectionMode = isSelectionMode,
                                isSelected = selected,
                                onToggleSelection = { onToggleSelection(song) },
                                onShowMenu = { onShowSongMenu(song) },
                            )
                        }
                    },
                )
            }
        }
    }
}
