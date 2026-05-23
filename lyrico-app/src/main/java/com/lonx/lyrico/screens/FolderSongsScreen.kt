package com.lonx.lyrico.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.ui.components.bar.SongBatchSelectionActions
import com.lonx.lyrico.ui.components.bar.SongSelectionTopAppBar
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.song.SongListItem
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.viewmodel.FolderSongsViewModel
import com.lonx.lyrico.viewmodel.SongSelectionViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "folder_songs")
fun FolderSongsScreen(
    navigator: DestinationsNavigator,
    folderId: Long,
    folderPath: String
) {
    val viewModel: FolderSongsViewModel = koinViewModel(
        parameters = { parametersOf(folderId) }
    )
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    val sortInfo by viewModel.sortInfo.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        if (isFabMenuExpanded) {
            isFabMenuExpanded = false
        } else {
            selectionViewModel.exitSelectionMode()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AnimatedContent(
                    targetState = isSelectionMode,
                    label = "FolderSongsTopBarAnimation",
                    transitionSpec = {
                        val animationDuration = 300
                        val enter = fadeIn(tween(animationDuration)) +
                                slideInVertically(
                                    animationSpec = tween(
                                        animationDuration,
                                        easing = FastOutSlowInEasing
                                    ),
                                    initialOffsetY = { -it / 3 }
                                )
                        val exit = fadeOut(tween(animationDuration)) +
                                slideOutVertically(
                                    animationSpec = tween(
                                        animationDuration,
                                        easing = FastOutSlowInEasing
                                    ),
                                    targetOffsetY = { -it / 3 }
                                )

                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { selectionMode ->
                    if (selectionMode) {
                        SongSelectionTopAppBar(
                            songs = songs,
                            selectedSongUris = selectedSongUris,
                            scrollBehavior = topAppBarScrollBehavior,
                            onSelectAll = selectionViewModel::selectAll,
                            onDeselectAll = selectionViewModel::deselectAll,
                            onClose = selectionViewModel::exitSelectionMode
                        )
                    } else {
                        SmallTopAppBar(
                            title = folderPath.substringAfterLast("/"),
                            navigationIcon = {
                                IconButton(onClick = { navigator.popBackStack() }) {
                                    Icon(
                                        imageVector = MiuixIcons.Back,
                                        contentDescription = stringResource(R.string.action_back)
                                    )
                                }
                            },
                            actions = {
                                val sortTypes = SortBy.entries.toList()
                                val sortEntries = DropdownEntry(
                                    items = sortTypes.map { sortType ->
                                        val isSelected = sortInfo.sortBy == sortType
                                        DropdownItem(
                                            text = stringResource(sortType.labelRes),
                                            selected = isSelected,
                                            summary = if (isSelected) {
                                                stringResource(
                                                    when (sortInfo.order) {
                                                        SortOrder.ASC -> R.string.sort_ascending
                                                        SortOrder.DESC -> R.string.sort_descending
                                                    }
                                                )
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                val newOrder = if (isSelected) {
                                                    if (sortInfo.order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
                                                } else {
                                                    SortOrder.ASC
                                                }
                                                viewModel.onSortChange(
                                                    SortInfo(
                                                        sortType,
                                                        newOrder
                                                    )
                                                )
                                            }
                                        )
                                    }
                                )
                                OverlayIconDropdownMenu(
                                    entries = listOf(sortEntries),
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Sort,
                                        contentDescription = stringResource(R.string.cd_sort)
                                    )
                                }
                            },
                            scrollBehavior = topAppBarScrollBehavior
                        )
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 12.dp,
                ),
                overscrollEffect = null,
            ) {
                if (songs.isEmpty()) {
                    item {
                        FolderSongsEmptyCard()
                    }
                } else {
                    items(
                        items = songs,
                        key = { song -> song.uri.takeIf { it.isNotBlank() && it != "0" } ?: "song-${song.id}" }
                    ) { song ->
                        SongListItem(
                            song = song,
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedSongUris.contains(song.uri),
                            onClick = {
                                navigator.navigate(EditMetadataDestination(song.uri))
                            },
                            onToggleSelection = {
                                selectionViewModel.toggleSelection(song.uri)
                            },
                            trailingContent = {
                                Box(modifier = Modifier.padding(end = 8.dp)) {
                                    SongListItemActions(
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedSongUris.contains(song.uri),
                                        onToggleSelection = {
                                            selectionViewModel.toggleSelection(song.uri)
                                        },
                                        onShowMenu = {
                                            selectedSong = song
                                            showMenuSheet = true
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            SongActionSheets(
                selectedSong = selectedSong,
                showMenuSheet = showMenuSheet,
                showDetailSheet = showDetailSheet,
                showDeleteDialog = showDeleteDialog,
                showRenameDialog = showRenameDialog,
                onDismissMenu = { showMenuSheet = false },
                onDismissMenuFinished = { selectedSong = null },
                onDismissDetail = { showDetailSheet = false },
                onDismissDelete = { showDeleteDialog = false },
                onDismissRename = { showRenameDialog = false },
                onShowDetail = { showDetailSheet = true },
                onShowDelete = { showDeleteDialog = true },
                onShowRename = { showRenameDialog = true },
                onPlay = { song -> selectionViewModel.play(context, song) },
                onDelete = { song -> selectionViewModel.delete(song) },
                onRename = { song, newFileName ->
                    selectionViewModel.renameSong(song, newFileName)
                }
            )
        }

        SongBatchSelectionActions(
            navigator = navigator,
            songs = songs,
            isSelectionMode = isSelectionMode,
            expanded = isFabMenuExpanded,
            selectedSongUris = selectedSongUris,
            onExpandedChange = { isFabMenuExpanded = it },
            onSetSelectionUris = selectionViewModel::setSelectionUris,
            onBatchDelete = selectionViewModel::batchDelete,
            onBatchShare = selectionViewModel::batchShare
        )
    }
}

@Composable
private fun FolderSongsEmptyCard() {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onBackground
        )
    ) {
        BasicComponent(
            title = stringResource(R.string.no_songs_in_folder)
        )
    }
}
