package com.lonx.lyrico.screens

import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.poster.previewMedia
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.lyrico.ui.components.library.SelectableLibraryItem
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.ui.components.library.LibraryDetailTabs
import com.lonx.lyrico.ui.components.library.LibraryDetailIconButton
import com.lonx.lyrico.ui.components.library.LibraryDetailText
import com.lonx.lyrico.ui.components.library.LibraryDetailTextTone
import com.lonx.lyrico.ui.components.library.LocalLibraryUiEngine
import com.lonx.lyrico.ui.components.library.libraryDetailScroll
import com.lonx.lyrico.screens.library.LibrarySelectionState
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.data.model.entity.AlbumEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.ui.components.album.AlbumListItem
import com.lonx.lyrico.ui.components.bar.SongBatchSelectionActions
import com.lonx.lyrico.ui.components.bar.SongSelectionTopAppBar
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.CoverCandidate
import com.lonx.lyrico.ui.components.cover.CoverImage
import com.lonx.lyrico.ui.components.library.AlbumActionBottomSheet
import com.lonx.lyrico.ui.components.poster.ArtistPosterActionsSheet
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.song.SongListItemContent
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.viewmodel.AlbumActionsViewModel
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@Destination<RootGraph>(route = "artist_detail")
fun ArtistDetailScreen(
    navigator: DestinationsNavigator,
    artistId: Long
) {
    val viewModel: ArtistDetailViewModel = koinViewModel(
        parameters = { parametersOf(artistId) }
    )
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    val artist by viewModel.artist.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()

    var selectedAlbum by remember { mutableStateOf<AlbumEntity?>(null) }
    val albumActionsViewModel: AlbumActionsViewModel = koinViewModel()
    val albumActionsUiState by albumActionsViewModel.uiState.collectAsStateWithLifecycle()

    val artistName = artist?.name.orEmpty()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsStateWithLifecycle()
    val swipeAnchorUri by selectionViewModel.swipeAnchorUri.collectAsStateWithLifecycle()

    var showAlbumActionSheet by remember { mutableStateOf(false) }
    var showDeleteAlbumDialog by remember { mutableStateOf(false) }
    val swipeSelectionLabel = stringResource(
        if (!isSelectionMode) {
            R.string.swipe_selection_enter_selection
        } else if (swipeAnchorUri == null) {
            R.string.swipe_selection_range_start
        } else {
            R.string.swipe_selection_range_end
        }
    )
    val swipeSelectionSecondaryLabel = if (!isSelectionMode) {
        stringResource(R.string.swipe_selection_range_start)
    } else {
        null
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Resolve the same artist artwork used by the list and cover renderer.
    val artistPosterState by viewModel.artistPosterEmbedState.collectAsStateWithLifecycle()
    var showArtistPosterSheet by remember { mutableStateOf(false) }
    var showArtistPosterDestination by remember { mutableStateOf(false) }
    val removalViewModel: com.lonx.lyrico.viewmodel.ArtistPosterEditViewModel = koinViewModel()
    val removalState by removalViewModel.state.collectAsStateWithLifecycle()
    val posterMenu by removalViewModel.menu.collectAsStateWithLifecycle()
    var previewPoster by remember { mutableStateOf<com.lonx.lyrico.domain.poster.ResolvedArtistPoster?>(null) }
    LaunchedEffect(showArtistPosterSheet, artistId, songs) {
        if (showArtistPosterSheet) removalViewModel.loadPoster(artistId,
            songs.map { CoverCandidate(uri = it.uri.toUri(), lastUpdate = it.fileLastModified) })
    }
    var pendingPosterRemoval by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingPosterExport by remember { mutableStateOf<Set<Long>>(emptySet()) }
    BackHandler(enabled = isSelectionMode) {
        if (isFabMenuExpanded) {
            isFabMenuExpanded = false
        } else {
            selectionViewModel.exitSelectionMode()
        }
    }

    val allSelected = songs.isNotEmpty() && selectedSongUris.containsAll(songs.map { it.uri })
    val selection = if (isSelectionMode) LibrarySelectionState(
        title = stringResource(R.string.selection_mode_selected_count, selectedSongUris.size),
        allSelected = allSelected,
        onClose = selectionViewModel::exitSelectionMode,
        onToggleAll = {
            if (allSelected) selectionViewModel.deselectAll() else selectionViewModel.selectAll(songs)
        }
    ) else null

    CompositionLocalProvider(LocalLibraryUiEngine provides LocalUiEngine.current) {
        Box(modifier = Modifier.fillMaxSize()) {
            LibraryDetailPage(
                title = artistName.ifBlank { stringResource(R.string.artist_detail_title) },
                onBack = { navigator.popBackStack() },
                selection = selection,
                miuixTopBar = {
                    AnimatedContent(
                        targetState = isSelectionMode,
                        label = "ArtistDetailTopBarAnimation",
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

                            (enter togetherWith exit).using(null)
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
                                title = artistName.ifBlank { stringResource(R.string.artist_detail_title) },
                                navigationIcon = {
                                    IconButton(onClick = { navigator.popBackStack() }) {
                                        Icon(
                                            imageVector = LucideIcons.ArrowLeft,
                                            contentDescription = stringResource(R.string.action_back)
                                        )
                                    }
                                },
                                scrollBehavior = topAppBarScrollBehavior
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(scaffoldTopHorizontalPadding(paddingValues))
                    ) {
                        ArtistDetailHeader(
                            artist = artistName,
                            songCount = songs.size,
                            albums = albums,
                            coverUri = artist?.coverSongUri,
                            coverLastModified = artist?.coverSongLastModified ?: 0L,
                            coverCandidates = songs.map { song ->
                                CoverCandidate(
                                    uri = song.uri.toUri(),
                                    lastUpdate = song.fileLastModified
                                )
                            },
                            onAvatarClick = { showArtistPosterSheet = true }
                        )

                        LibraryDetailTabs(
                            tabs = listOf(
                                stringResource(R.string.artist_tab_songs),
                                stringResource(R.string.artist_tab_albums)
                            ),
                            selectedIndex = pagerState.currentPage,
                            onSelected = { index ->
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) { page ->
                            if (page == 0) {
                                ArtistSongsPage(
                                    songs = songs,
                                    isSelectionMode = isSelectionMode,
                                    selectedSongUris = selectedSongUris,
                                    swipeSelectionLabel = swipeSelectionLabel,
                                    swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                                    bottomPadding = paddingValues.calculateBottomPadding(),
                                    onSongClick = { song ->
                                        selectionViewModel.exitSelectionMode()
                                        navigator.navigate(EditMetadataDestination(songFileUri = song.uri))
                                    },
                                    onToggleSelection = { song ->
                                        selectionViewModel.toggleSelection(song.uri)
                                    },
                                    onSwipeSelection = { song ->
                                        selectionViewModel.swipeSelect(song, songs)
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
                                    bottomPadding = paddingValues.calculateBottomPadding(),
                                    onAlbumClick = { album ->
                                        selectionViewModel.exitSelectionMode()
                                        navigator.navigate(
                                            AlbumDetailDestination(albumId = album.id)
                                        )
                                    },
                                    onAlbumActionClick = { album ->
                                        selectedAlbum = album
                                        showAlbumActionSheet = true
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
                        onDismissMenuFinished = {
                            if (!showMenuSheet && !showDetailSheet && !showDeleteDialog && !showRenameDialog) {
                                selectedSong = null
                            }
                        },
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
            selectedAlbum?.let { album ->
                AlbumActionBottomSheet(
                    show = showAlbumActionSheet,
                    albumName = album.name,
                    isCalculatingReplayGain = albumActionsUiState.isCalculatingAlbumReplayGain,
                    onDismissRequest = { showAlbumActionSheet = false },
                    onDismissFinished = {
                        if (!showAlbumActionSheet && !showDeleteAlbumDialog) {
                            selectedAlbum = null
                        }
                    },
                    onShare = {
                        showAlbumActionSheet = false
                        albumActionsViewModel.shareAlbum(context, album.id)
                    },
                    onDelete = {
                        showAlbumActionSheet = false
                        showDeleteAlbumDialog = true
                    },
                    onCalculateReplayGain = {
                        showAlbumActionSheet = false
                        albumActionsViewModel.calculateAlbumReplayGain(album.id)
                    }
                )

                YesNoDialog(
                    title = stringResource(R.string.dialog_delete_album_title),
                    show = showDeleteAlbumDialog,
                    summary = stringResource(
                        R.string.dialog_delete_album_content,
                        album.songCount,
                        album.name
                    ),
                    onConfirm = {
                        showDeleteAlbumDialog = false
                        albumActionsViewModel.deleteAlbum(album.id)
                        selectedAlbum = null
                    },
                    onDismissRequest = {
                        showDeleteAlbumDialog = false
                        selectedAlbum = null
                    }
                )
            }
            SongBatchSelectionActions(
                navigator = navigator,
                songs = songs,
                show = isSelectionMode,
                expanded = isFabMenuExpanded,
                selectedSongUris = selectedSongUris,
                onExpandedChange = { isFabMenuExpanded = it },
                onSetSelectionUris = selectionViewModel::setSelectionUris,
                onBatchDelete = selectionViewModel::batchDelete,
                onBatchShare = selectionViewModel::batchShare
            )

            // Both local image destinations share the picker and write workflow.
            ArtistPosterActionsSheet(
                show = showArtistPosterSheet,
                canViewPoster = !posterMenu.loading && posterMenu.source?.artistName == artistName,
                posterLoading = posterMenu.loading,
                posterError = posterMenu.error,
                onViewPoster = { previewPoster = posterMenu.source; showArtistPosterSheet = false },
                onAddLocalPoster = {
                    showArtistPosterSheet = false
                    showArtistPosterDestination = true
                },
                onDismissRequest = { showArtistPosterSheet = false },
                enabled = !artistPosterState.isRunning && !removalState.isRunning,
                onRemoveFromSongs = {
                    showArtistPosterSheet = false
                    pendingPosterRemoval = setOf(artistId)
                },
                onExportEmbedded = {
                    showArtistPosterSheet = false
                    pendingPosterExport = setOf(artistId)
                }
            )
            previewPoster?.let { source ->
                com.lonx.lyrico.ui.components.poster.ArtistPosterPreview(
                    items = listOf(source.previewMedia()), title = { source.artistName }, editingEnabled = !removalState.isRunning && !artistPosterState.isRunning,
                    onCrop = { _, bitmap -> removalViewModel.cropPoster(source, bitmap) },
                    onDismiss = { previewPoster = null }
                )
            }
            com.lonx.lyrico.ui.components.poster.ArtistPosterAddHost(
                viewModel = viewModel,
                show = showArtistPosterDestination,
                onDismissRequest = { showArtistPosterDestination = false },
                enabled = !removalState.isRunning
            )

            com.lonx.lyrico.ui.components.poster.ArtistPosterRemovalHost(
                removalViewModel, pendingPosterRemoval, { pendingPosterRemoval = emptySet() }
            )
            com.lonx.lyrico.ui.components.poster.ArtistPosterExportHost(
                removalViewModel, pendingPosterExport, { pendingPosterExport = emptySet() }
            )
        }
    }
}

@Composable
private fun ArtistSongsPage(
    songs: List<SongEntity>,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    topAppBarScrollBehavior: ScrollBehavior,
    bottomPadding: Dp,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier
            .libraryDetailScroll(topAppBarScrollBehavior)
            .fillMaxHeight(),
        state = listState,
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
        overscrollEffect = if (LocalLibraryUiEngine.current == UiEngine.SALT) androidx.compose.foundation.rememberOverscrollEffect() else null
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
            key = { song -> song.uri.takeIf { it.isNotBlank() && it != "0" } ?: "song-${song.id}" }
        ) { song ->
            SelectableLibraryItem(
                itemKey = song.uri,
                isSelectionMode = isSelectionMode,
                isSelected = selectedSongUris.contains(song.uri),
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                onClick = { onSongClick(song) },
                onToggleSelection = { onToggleSelection(song) },
                onSwipeSelection = { onSwipeSelection(song) }
            ) {
                SongListItemContent(
                    song = song,
                    showTrackNumbers = true,
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
}

@Composable
private fun ArtistAlbumsPage(
    albums: List<AlbumEntity>,
    topAppBarScrollBehavior: ScrollBehavior,
    bottomPadding: Dp,
    onAlbumClick: (AlbumEntity) -> Unit,
    onAlbumActionClick: (AlbumEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .libraryDetailScroll(topAppBarScrollBehavior)
            .fillMaxHeight(),
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
        overscrollEffect = if (LocalLibraryUiEngine.current == UiEngine.SALT) androidx.compose.foundation.rememberOverscrollEffect() else null
    ) {
        if (albums.isEmpty()) {
            item("empty") {
                IllustratedEmptyState(
                    kind = EmptyStateKind.Albums,
                    title = stringResource(R.string.empty_albums_title),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(
            items = albums,
            key = { album -> album.id }
        ) { album ->
            AlbumListItem(
                album = album,
                onClick = { onAlbumClick(album) },
                trailingContent = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        LibraryDetailIconButton(
                            icon = LucideIcons.EllipsisVertical,
                            description = stringResource(R.string.cd_more_actions),
                            onClick = { onAlbumActionClick(album) }
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ArtistDetailHeader(
    artist: String,
    songCount: Int,
    albums: List<AlbumEntity>,
    coverUri: String?,
    coverLastModified: Long,
    coverCandidates: List<CoverCandidate>,
    onAvatarClick: () -> Unit
) {
    val salt = LocalLibraryUiEngine.current == UiEngine.SALT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图片角标沿用单曲编辑页的图片语义，同时保留整张海报的点击热区。
        Box(contentAlignment = Alignment.BottomEnd) {
            CoverImage(
                uri = coverUri,
                lastModified = coverLastModified,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .clickable(
                        onClickLabel = stringResource(R.string.label_set_artist_poster),
                        onClick = onAvatarClick
                    ),
                shape = CircleShape,
                contentDescription = artist,
                pictureType = AudioPictureType.Artist,
                fallbackPictureTypes = listOf(
                    AudioPictureType.LeadArtist,
                    AudioPictureType.Band
                ),
                fallbackToAny = true,
                candidates = coverCandidates,
                artistName = artist
            )
            val badgeModifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (salt) SaltTheme.colors.subBackground else MiuixTheme.colorScheme.secondaryContainer)
                .padding(5.dp)
            if (salt) {
                com.moriafly.salt.ui.Icon(
                    painter = rememberVectorPainter(LucideIcons.Image),
                    contentDescription = null,
                    tint = SaltTheme.colors.highlight,
                    modifier = badgeModifier
                )
            } else {
                Icon(
                    imageVector = LucideIcons.Image,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = badgeModifier
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            val title = artist.ifBlank { stringResource(R.string.artist_detail_title) }
            val summary = stringResource(R.string.album_song_count, albums.size, songCount)
            if (salt) {
                LibraryDetailText(
                    text = title,
                    tone = LibraryDetailTextTone.Title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                LibraryDetailText(
                    text = summary,
                    tone = LibraryDetailTextTone.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.main,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
