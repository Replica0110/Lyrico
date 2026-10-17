package com.lonx.lyrico.screens

import com.lonx.lyrico.ui.components.icons.LucideIcons
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.ui.components.library.SelectableLibraryItem
import com.lonx.lyrico.screens.library.LibrarySelectionState
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.data.model.entity.AlbumEntity
import com.lonx.lyrico.data.model.entity.ArtistEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.search.LocalLyricSearchResult
import com.lonx.lyrico.data.model.search.LocalSearchField
import com.lonx.lyrico.data.model.search.LocalSearchUiState
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.album.AlbumListItem
import com.lonx.lyrico.ui.components.artist.ArtistListItemContent
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.components.bar.SongBatchSelectionActions
import com.lonx.lyrico.ui.components.bar.SongSelectionTopAppBar
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.components.scaffoldTopAppBarInsetsPadding
import com.lonx.lyrico.ui.components.search.SearchSectionHeader
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.song.SongListItemContent
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.viewmodel.LocalSearchViewModel
import com.lonx.lyrico.viewmodel.SongSelectionViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AlbumDetailDestination
import com.ramcosta.composedestinations.generated.destinations.ArtistDetailDestination
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.moriafly.salt.ui.SaltTheme
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "local_search")
fun LocalSearchScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: LocalSearchViewModel = koinViewModel()
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsStateWithLifecycle()
    val swipeAnchorUri by selectionViewModel.swipeAnchorUri.collectAsStateWithLifecycle()
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
    val searchTabs = LocalSearchTab.entries.filter {
        it != LocalSearchTab.Lyrics || uiState.lyricSearchEnabled
    }
    val pagerState = rememberPagerState(pageCount = { searchTabs.size })
    LaunchedEffect(searchTabs) {
        if (pagerState.currentPage >= searchTabs.size) pagerState.scrollToPage(0)
    }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val salt = LocalUiEngine.current == UiEngine.SALT
    val currentTab = searchTabs.getOrElse(pagerState.targetPage) { LocalSearchTab.All }
    val visibleSongs = visibleSongsForTab(
        tab = currentTab,
        uiState = uiState,
        query = searchQuery
    )
    val searchState = rememberTextFieldState(initialText = searchQuery)

    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }
            .distinctUntilChanged()
            .collectLatest(viewModel::onQueryChange)
    }

    LaunchedEffect(isSelectionMode, currentTab) {
        if (isSelectionMode && visibleSongs.isEmpty()) {
            selectionViewModel.exitSelectionMode()
        }
    }

    BackHandler(enabled = isSelectionMode) {
        if (isFabMenuExpanded) {
            isFabMenuExpanded = false
        } else {
            selectionViewModel.exitSelectionMode()
        }
    }

    val allVisibleSongsSelected =
        visibleSongs.isNotEmpty() && selectedSongUris.containsAll(visibleSongs.map { it.uri })
    val saltSelectionState = if (salt && isSelectionMode) {
        LibrarySelectionState(
            title = stringResource(R.string.selection_mode_selected_count, selectedSongUris.size),
            allSelected = allVisibleSongsSelected,
            onClose = { selectionViewModel.exitSelectionMode() },
            onToggleAll = {
                if (allVisibleSongsSelected) selectionViewModel.deselectAll()
                else selectionViewModel.selectAll(visibleSongs)
            },
        )
    } else null

    Box(modifier = Modifier.fillMaxSize()) {
        if (salt) {
            LocalSearchSaltShell(
                title = stringResource(R.string.local_search_title),
                selection = saltSelectionState,
                onBack = { navigator.popBackStack() },
                isSelectionMode = isSelectionMode,
                searchState = searchState,
                onSearch = { keyword -> viewModel.onQueryChange(keyword) },
                searchTabs = searchTabs,
                pagerState = pagerState,
                onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                pages = {
                    LocalSearchPages(
                        pagerState = pagerState,
                        searchTabs = searchTabs,
                        uiState = uiState,
                        searchQuery = searchQuery,
                        isSelectionMode = isSelectionMode,
                        selectedSongUris = selectedSongUris,
                        swipeSelectionLabel = swipeSelectionLabel,
                        swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                        topAppBarScrollBehavior = topAppBarScrollBehavior,
                        contentPaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        salt = true,
                        onArtistClick = { artist ->
                            selectionViewModel.exitSelectionMode()
                            navigator.navigate(ArtistDetailDestination(artistId = artist.id))
                        },
                        onAlbumClick = { album ->
                            selectionViewModel.exitSelectionMode()
                            navigator.navigate(AlbumDetailDestination(albumId = album.id))
                        },
                        onSongClick = { song ->
                            selectionViewModel.exitSelectionMode()
                            navigator.navigate(EditMetadataDestination(songFileUri = song.uri))
                        },
                        onToggleSelection = { song -> selectionViewModel.toggleSelection(song.uri) },
                        onSwipeSelection = { song, pageSongs -> selectionViewModel.swipeSelect(song, pageSongs) },
                        onShowSongMenu = { song ->
                            selectedSong = song
                            showMenuSheet = true
                        }
                    )
                }
            )
        } else Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(MiuixTheme.colorScheme.surface)
                        // 与其它页面（BlurredTopBar 等）一致：顶部区域自己处理安全区
                        .scaffoldTopAppBarInsetsPadding()
                ) {
                    AnimatedContent(
                        targetState = isSelectionMode,
                        label = "LocalSearchTopBarAnimation",
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
                        }
                    ) { selectionMode ->
                        if (selectionMode) {
                            SongSelectionTopAppBar(
                                songs = visibleSongs,
                                selectedSongUris = selectedSongUris,
                                scrollBehavior = topAppBarScrollBehavior,
                                onSelectAll = selectionViewModel::selectAll,
                                onDeselectAll = selectionViewModel::deselectAll,
                                onClose = selectionViewModel::exitSelectionMode
                            )
                        } else {
                            SmallTopAppBar(
                                title = stringResource(R.string.local_search_title),
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
                    AnimatedVisibility(
                        visible = !isSelectionMode,
                        enter = expandVertically(
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            ),
                            initialOffsetY = { -it / 3 }
                        ) + fadeIn(tween(300)),
                        exit = shrinkVertically(
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideOutVertically(
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            ),
                            targetOffsetY = { -it / 3 }
                        ) + fadeOut(tween(300))
                    ) {
                        Column {
                            SearchBar(
                                state = searchState,
                                placeholder = stringResource(R.string.local_search_hint),
                                onSearch = { keyword ->
                                    viewModel.onQueryChange(keyword)
                                },
                                autoFocus = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                            LocalSearchPillTabRow(
                                tabs = searchTabs,
                                selectedTabIndex = pagerState.targetPage,
                                onTabSelected = { index ->
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            LocalSearchPages(
                pagerState = pagerState,
                searchTabs = searchTabs,
                uiState = uiState,
                searchQuery = searchQuery,
                isSelectionMode = isSelectionMode,
                selectedSongUris = selectedSongUris,
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                contentPaddingValues = paddingValues,
                salt = false,
                onArtistClick = { artist ->
                    selectionViewModel.exitSelectionMode()
                    navigator.navigate(ArtistDetailDestination(artistId = artist.id))
                },
                onAlbumClick = { album ->
                    selectionViewModel.exitSelectionMode()
                    navigator.navigate(AlbumDetailDestination(albumId = album.id))
                },
                onSongClick = { song ->
                    selectionViewModel.exitSelectionMode()
                    navigator.navigate(EditMetadataDestination(songFileUri = song.uri))
                },
                onToggleSelection = { song ->
                    selectionViewModel.toggleSelection(song.uri)
                },
                onSwipeSelection = { song, pageSongs ->
                    selectionViewModel.swipeSelect(song, pageSongs)
                },
                onShowSongMenu = { song ->
                    selectedSong = song
                    showMenuSheet = true
                }
            )
        }

        SongBatchSelectionActions(
            navigator = navigator,
            songs = visibleSongs,
            show = isSelectionMode,
            expanded = isFabMenuExpanded,
            selectedSongUris = selectedSongUris,
            onExpandedChange = { isFabMenuExpanded = it },
            onSetSelectionUris = selectionViewModel::setSelectionUris,
            onBatchDelete = selectionViewModel::batchDelete,
            onBatchShare = selectionViewModel::batchShare
        )

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

private enum class LocalSearchTab(
    val labelRes: Int,
    val field: LocalSearchField? = null
) {
    All(R.string.search_type_all),
    Title(LocalSearchField.TITLE.labelRes, LocalSearchField.TITLE),
    Artists(LocalSearchField.ARTIST.labelRes, LocalSearchField.ARTIST),
    Albums(LocalSearchField.ALBUM.labelRes, LocalSearchField.ALBUM),
    Lyrics(R.string.label_lyrics),
    AlbumArtist(LocalSearchField.ALBUM_ARTIST.labelRes, LocalSearchField.ALBUM_ARTIST),
    Date(LocalSearchField.DATE.labelRes, LocalSearchField.DATE),
    Language(LocalSearchField.LANGUAGE.labelRes, LocalSearchField.LANGUAGE),
    Genre(LocalSearchField.GENRE.labelRes, LocalSearchField.GENRE),
    Lyricist(LocalSearchField.LYRICIST.labelRes, LocalSearchField.LYRICIST),
    Composer(LocalSearchField.COMPOSER.labelRes, LocalSearchField.COMPOSER),
    Copyright(LocalSearchField.COPYRIGHT.labelRes, LocalSearchField.COPYRIGHT),
    Comment(LocalSearchField.COMMENT.labelRes, LocalSearchField.COMMENT)
}

@Composable
private fun LocalSearchPillTabRow(
    tabs: List<LocalSearchTab>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val salt = LocalUiEngine.current == UiEngine.SALT
        tabs.forEachIndexed { index, tab ->
            PillButton(
                text = stringResource(tab.labelRes),
                selected = index == selectedTabIndex,
                style = PillButtonDefaults.style(PillButtonSize.Medium),
                colors = if (salt) PillButtonDefaults.colors() else PillButtonDefaults.colors(
                    containerColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                ),
                onClick = { onTabSelected(index) }
            )
        }
    }
}

@Composable
private fun LocalSearchResultsPage(
    tab: LocalSearchTab,
    uiState: LocalSearchUiState,
    searchQuery: String,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    topAppBarScrollBehavior: ScrollBehavior,
    contentPaddingValues: PaddingValues,
    salt: Boolean,
    onArtistClick: (ArtistEntity) -> Unit,
    onAlbumClick: (AlbumEntity) -> Unit,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    val listState = rememberLazyListState()
    val pageSongs = visibleSongsForTab(tab, uiState, searchQuery)
    val hasResults = hasResultsForTab(tab, uiState, searchQuery)

    LazyColumn(
        modifier = (if (salt) Modifier else Modifier
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection))
            .fillMaxHeight(),
        state = listState,
        contentPadding = scaffoldContentPadding(
            paddingValues = contentPaddingValues,
            bottomExtra = 12.dp
        ),
        overscrollEffect = if (salt) rememberOverscrollEffect() else null
    ) {
        if (searchQuery.isNotBlank() && !hasResults) {
            item {
                IllustratedEmptyState(
                    kind = EmptyStateKind.Search,
                    title = stringResource(R.string.search_empty),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (tab == LocalSearchTab.All) {
            FieldSongsSection(
                field = LocalSearchField.TITLE,
                songs = songsMatching(LocalSearchField.TITLE, uiState, searchQuery),
                query = searchQuery,
                isSelectionMode = isSelectionMode,
                selectedSongUris = selectedSongUris,
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                pageSongs = pageSongs,
                onSongClick = onSongClick,
                onToggleSelection = onToggleSelection,
                onSwipeSelection = onSwipeSelection,
                onShowSongMenu = onShowSongMenu
            )
            ArtistsSection(
                artists = uiState.artists,
                onArtistClick = onArtistClick
            )
            AlbumsSection(
                albums = uiState.albums,
                onAlbumClick = onAlbumClick
            )
            songResultFields
                .filterNot { it == LocalSearchField.TITLE }
                .forEach { field ->
                    FieldSongsSection(
                        field = field,
                        songs = songsMatching(field, uiState, searchQuery),
                        query = searchQuery,
                        isSelectionMode = isSelectionMode,
                        selectedSongUris = selectedSongUris,
                        swipeSelectionLabel = swipeSelectionLabel,
                        swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                        pageSongs = pageSongs,
                        onSongClick = onSongClick,
                        onToggleSelection = onToggleSelection,
                        onSwipeSelection = onSwipeSelection,
                        onShowSongMenu = onShowSongMenu
                    )
                }
        } else if (tab == LocalSearchTab.Artists) {
            ArtistsSection(artists = uiState.artists, onArtistClick = onArtistClick)
        } else if (tab == LocalSearchTab.Albums) {
            AlbumsSection(albums = uiState.albums, onAlbumClick = onAlbumClick)
        } else {
            tab.field?.let { field ->
                FieldSongsSection(
                    field = field,
                    songs = songsMatching(field, uiState, searchQuery),
                    query = searchQuery,
                    isSelectionMode = isSelectionMode,
                    selectedSongUris = selectedSongUris,
                    swipeSelectionLabel = swipeSelectionLabel,
                    swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                    pageSongs = pageSongs,
                    onSongClick = onSongClick,
                    onToggleSelection = onToggleSelection,
                    onSwipeSelection = onSwipeSelection,
                    onShowSongMenu = onShowSongMenu
                )
            }
        }

        if (uiState.lyricSearchEnabled && (tab == LocalSearchTab.All || tab == LocalSearchTab.Lyrics)) {
            LyricsSection(
                matches = uiState.lyricMatches,
                query = searchQuery,
                isSelectionMode = isSelectionMode,
                selectedSongUris = selectedSongUris,
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                pageSongs = pageSongs,
                onSongClick = onSongClick,
                onToggleSelection = onToggleSelection,
                onSwipeSelection = onSwipeSelection,
                onShowSongMenu = onShowSongMenu
            )
        }
    }
}

private fun visibleSongsForTab(
    tab: LocalSearchTab,
    uiState: LocalSearchUiState,
    query: String
): List<SongEntity> {
    return when {
        tab == LocalSearchTab.All -> (
            songResultFields.flatMap { songsMatching(it, uiState, query) } +
                uiState.lyricMatches.map { it.song }
            )
            .distinctBy { it.uri }
        tab == LocalSearchTab.Lyrics -> uiState.lyricMatches.map { it.song }
        tab == LocalSearchTab.Albums || tab == LocalSearchTab.Artists -> emptyList()
        tab.field != null -> songsMatching(tab.field, uiState, query)
        else -> emptyList()
    }
}

private fun hasResultsForTab(
    tab: LocalSearchTab,
    uiState: LocalSearchUiState,
    query: String
): Boolean = when {
    tab == LocalSearchTab.All ->
        uiState.songs.isNotEmpty() || uiState.albums.isNotEmpty() ||
            uiState.artists.isNotEmpty() || uiState.lyricMatches.isNotEmpty()
    tab == LocalSearchTab.Artists -> uiState.artists.isNotEmpty()
    tab == LocalSearchTab.Albums -> uiState.albums.isNotEmpty()
    tab == LocalSearchTab.Lyrics -> uiState.lyricMatches.isNotEmpty()
    tab.field != null -> songsMatching(tab.field, uiState, query).isNotEmpty()
    else -> false
}

private val songResultFields = LocalSearchField.entries.filterNot {
    it == LocalSearchField.ARTIST || it == LocalSearchField.ALBUM
}

private fun songsMatching(
    field: LocalSearchField,
    uiState: LocalSearchUiState,
    query: String
): List<SongEntity> = uiState.songs.filter { field.matches(it, query) }

private fun androidx.compose.foundation.lazy.LazyListScope.FieldSongsSection(
    field: LocalSearchField,
    songs: List<SongEntity>,
    query: String,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    pageSongs: List<SongEntity>,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    if (songs.isEmpty()) return

    item {
        SearchSectionHeader(
            title = stringResource(field.labelRes),
            subtitle = stringResource(R.string.song_count, songs.size)
        )
    }
    items(
        items = songs,
        key = { song -> "local-search-${field.name}-${localSearchSongKey(song)}" }
    ) { song ->
        LocalSearchSongItem(
            song = song,
            isSelectionMode = isSelectionMode,
            isSelected = selectedSongUris.contains(song.uri),
            swipeSelectionLabel = swipeSelectionLabel,
            swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
            previewLabel = field.takeUnless { it == LocalSearchField.TITLE }
                ?.let { stringResource(it.labelRes) },
            previewText = field.takeUnless { it == LocalSearchField.TITLE }?.valueOf(song),
            previewMatchQuery = query,
            pageSongs = pageSongs,
            onSongClick = onSongClick,
            onToggleSelection = onToggleSelection,
            onSwipeSelection = onSwipeSelection,
            onShowSongMenu = onShowSongMenu
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.LyricsSection(
    matches: List<LocalLyricSearchResult>,
    query: String,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    pageSongs: List<SongEntity>,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    if (matches.isEmpty()) return

    item {
        SearchSectionHeader(
            title = stringResource(R.string.label_lyrics),
            subtitle = stringResource(R.string.song_count, matches.size)
        )
    }
    items(
        items = matches,
        key = { match -> "local-search-lyric-${localSearchSongKey(match.song)}" }
    ) { match ->
        LocalSearchSongItem(
            song = match.song,
            isSelectionMode = isSelectionMode,
            isSelected = selectedSongUris.contains(match.song.uri),
            swipeSelectionLabel = swipeSelectionLabel,
            swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
            previewLabel = stringResource(R.string.label_lyrics),
            previewText = match.lyricLine,
            previewMatchQuery = query,
            pageSongs = pageSongs,
            onSongClick = onSongClick,
            onToggleSelection = onToggleSelection,
            onSwipeSelection = onSwipeSelection,
            onShowSongMenu = onShowSongMenu
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.AlbumsSection(
    albums: List<AlbumEntity>,
    onAlbumClick: (AlbumEntity) -> Unit
) {
    if (albums.isEmpty()) return

    item {
        SearchSectionHeader(
            title = stringResource(R.string.search_section_albums),
            subtitle = stringResource(R.string.album_count, albums.size)
        )
    }
    items(
        items = albums,
        key = { album -> "${album.name}|${album.albumArtist.orEmpty()}" }
    ) { album ->
        AlbumListItem(
            album = album,
            onClick = { onAlbumClick(album) }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.ArtistsSection(
    artists: List<ArtistEntity>,
    onArtistClick: (ArtistEntity) -> Unit
) {
    if (artists.isEmpty()) return

    item {
        SearchSectionHeader(
            title = stringResource(R.string.search_section_artists),
            subtitle = stringResource(R.string.song_count, artists.size)
        )
    }
    items(
        items = artists,
        key = { artist -> artist.name }
    ) { artist ->
        SelectableLibraryItem(
            itemKey = artist.id,
            onClick = { onArtistClick(artist) }
        ) {
            ArtistListItemContent(
                artist = artist
            )
        }
    }
}

@Composable
private fun LocalSearchSongItem(
    song: SongEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    previewLabel: String?,
    previewText: String?,
    previewMatchQuery: String?,
    pageSongs: List<SongEntity>,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit
) {
    SelectableLibraryItem(
        itemKey = song.uri,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        swipeSelectionLabel = swipeSelectionLabel,
        swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
        onClick = { onSongClick(song) },
        onToggleSelection = { onToggleSelection(song) },
        onSwipeSelection = { onSwipeSelection(song, pageSongs) }
    ) {
        SongListItemContent(
            song = song,
            previewLabel = previewLabel,
            previewText = previewText,
            previewMatchQuery = previewMatchQuery,
            trailingContent = {
                Box(modifier = Modifier.padding(end = 8.dp)) {
                    SongListItemActions(
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onToggleSelection = { onToggleSelection(song) },
                        onShowMenu = { onShowSongMenu(song) }
                    )
                }
            }
        )
    }
}


private fun localSearchSongKey(song: SongEntity): String {
    val stableId = song.uri.takeIf { it.isNotBlank() && it != "0" } ?: song.id.toString()
    return "local-search-song-$stableId"
}

/** Salt 外壳：原生顶栏（含选择态槽位）、固定搜索与标签行，内容由 [pages] 提供。 */
@Composable
private fun LocalSearchSaltShell(
    title: String,
    selection: LibrarySelectionState?,
    onBack: () -> Unit,
    isSelectionMode: Boolean,
    searchState: androidx.compose.foundation.text.input.TextFieldState,
    onSearch: (String) -> Unit,
    searchTabs: List<LocalSearchTab>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onTabSelected: (Int) -> Unit,
    pages: @Composable () -> Unit,
) {
    LibraryDetailPage(
        title = title,
        onBack = onBack,
        selection = selection,
        miuixTopBar = {},
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isSelectionMode) {
                SearchBar(
                    state = searchState,
                    placeholder = stringResource(R.string.local_search_hint),
                    onSearch = onSearch,
                    autoFocus = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                LocalSearchPillTabRow(
                    tabs = searchTabs,
                    selectedTabIndex = pagerState.targetPage,
                    onTabSelected = onTabSelected,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            pages()
        }
    }
}

/** 结果分页：两引擎共用同一套分页与分组逻辑。 */
@Composable
private fun LocalSearchPages(
    pagerState: androidx.compose.foundation.pager.PagerState,
    searchTabs: List<LocalSearchTab>,
    uiState: LocalSearchUiState,
    searchQuery: String,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    topAppBarScrollBehavior: ScrollBehavior,
    contentPaddingValues: PaddingValues,
    salt: Boolean,
    onArtistClick: (ArtistEntity) -> Unit,
    onAlbumClick: (AlbumEntity) -> Unit,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        LocalSearchResultsPage(
            tab = searchTabs.getOrElse(page) { LocalSearchTab.All },
            uiState = uiState,
            searchQuery = searchQuery,
            isSelectionMode = isSelectionMode,
            selectedSongUris = selectedSongUris,
            swipeSelectionLabel = swipeSelectionLabel,
            swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
            topAppBarScrollBehavior = topAppBarScrollBehavior,
            contentPaddingValues = contentPaddingValues,
            salt = salt,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onSongClick = onSongClick,
            onToggleSelection = onToggleSelection,
            onSwipeSelection = onSwipeSelection,
            onShowSongMenu = onShowSongMenu
        )
    }
}
