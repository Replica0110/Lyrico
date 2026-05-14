package com.lonx.lyrico.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.AlbumSearchResult
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.ui.components.cover.CoverImage
import com.lonx.lyrico.ui.components.search.AlbumSongItem
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.song.SongListItem
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.viewmodel.ArtistDetailViewModel
import com.lonx.lyrico.viewmodel.SongSelectionViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AlbumDetailDestination
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "artist_detail")
fun ArtistDetailScreen(
    navigator: DestinationsNavigator,
    artist: String
) {
    val viewModel: ArtistDetailViewModel = koinViewModel(
        parameters = { parametersOf(artist) }
    )
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
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
                if (isSelectionMode) {
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
                        title = artist.ifBlank { stringResource(R.string.artist_detail_title) },
                        navigationIcon = {
                            IconButton(onClick = { navigator.popBackStack() }) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = stringResource(R.string.action_back)
                                )
                            }
                        },
                        scrollBehavior = topAppBarScrollBehavior
                    )
                }
            }
        ) { paddingValues ->
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = paddingValues.calculateStartPadding(layoutDirection),
                            top = paddingValues.calculateTopPadding(),
                            end = paddingValues.calculateEndPadding(layoutDirection)
                        )
                ) {
                    ArtistDetailHeader(
                        artist = artist,
                        songCount = songs.size,
                        albums = albums,
                        coverSong = songs.firstOrNull()
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        TabRowWithContour(
                            tabs = listOf(
                                stringResource(R.string.artist_tab_songs),
                                stringResource(R.string.artist_tab_albums)
                            ),
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = { index ->
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        if (page == 0) {
                            ArtistSongsPage(
                                songs = songs,
                                isSelectionMode = isSelectionMode,
                                selectedSongUris = selectedSongUris,
                                topAppBarScrollBehavior = topAppBarScrollBehavior,
                                onSongClick = { song ->
                                    navigator.navigate(EditMetadataDestination(songFileUri = song.uri))
                                },
                                onToggleSelection = { song ->
                                    selectionViewModel.toggleSelection(song.uri)
                                },
                                onShowSongMenu = { song ->
                                    selectedSong = song
                                    showMenuSheet = true
                                }
                            )
                        } else {
                            ArtistAlbumsPage(
                                albums = albums,
                                topAppBarScrollBehavior = topAppBarScrollBehavior,
                                onAlbumClick = { album ->
                                    navigator.navigate(
                                        AlbumDetailDestination(
                                            album = album.album,
                                            albumArtist = album.albumArtist
                                        )
                                    )
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
private fun ArtistSongsPage(
    songs: List<SongEntity>,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    topAppBarScrollBehavior: ScrollBehavior,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
            .fillMaxHeight(),
        contentPadding = PaddingValues(bottom = 12.dp),
        overscrollEffect = null
    ) {
        items(
            items = songs,
            key = { song -> song.uri.takeIf { it.isNotBlank() && it != "0" } ?: "song-${song.id}" }
        ) { song ->
            SongListItem(
                song = song,
                isSelectionMode = isSelectionMode,
                isSelected = selectedSongUris.contains(song.uri),
                onClick = { onSongClick(song) },
                onToggleSelection = { onToggleSelection(song) },
                trailingContent = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        SongListItemActions(
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedSongUris.contains(song.uri),
                            onToggleSelection = { onToggleSelection(song) },
                            onShowMenu = { onShowSongMenu(song) }
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ArtistAlbumsPage(
    albums: List<AlbumSearchResult>,
    topAppBarScrollBehavior: ScrollBehavior,
    onAlbumClick: (AlbumSearchResult) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
            .fillMaxHeight(),
        contentPadding = PaddingValues(bottom = 12.dp),
        overscrollEffect = null
    ) {
        items(
            items = albums,
            key = { album -> "${album.album}|${album.albumArtist.orEmpty()}" }
        ) { album ->
            AlbumSongItem(
                title = album.album,
                subtitle = listOfNotNull(
                    album.albumArtist,
                    stringResource(R.string.song_count, album.songCount)
                ).joinToString(" - "),
                coverUri = album.coverSongUri,
                coverLastModified = album.coverSongLastModified,
                onClick = { onAlbumClick(album) }
            )
        }
    }
}

@Composable
private fun ArtistDetailHeader(
    artist: String,
    songCount: Int,
    albums: List<AlbumSearchResult>,
    coverSong: SongEntity?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            uri = coverSong?.uri,
            lastModified = coverSong?.fileLastModified ?: 0L,
            modifier = Modifier.size(80.dp),
            shape = CircleShape
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.ifBlank { stringResource(R.string.artist_detail_title) },
                style = MiuixTheme.textStyles.title3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.album_song_count,
                    albums.size,
                    songCount
                ),
                style = MiuixTheme.textStyles.main,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
