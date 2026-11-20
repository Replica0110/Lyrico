package com.lonx.lyrico.ui.screens.search

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.components.bar.SelectionTopBarState
import com.lonx.lyrico.ui.components.bar.SongSelectionTopAppBar
import com.lonx.lyrico.ui.components.button.PillButton
import com.lonx.lyrico.ui.components.button.PillButtonDefaults
import com.lonx.lyrico.ui.components.button.PillButtonSize
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.scaffold.PageScaffold
import com.lonx.lyrico.ui.components.scaffold.scaffoldTopAppBarInsetsPadding
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.screens.batch.SongBatchSelectionActions
import com.lonx.lyrico.ui.screens.library.SongSelectionViewModel
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AlbumDetailDestination
import com.ramcosta.composedestinations.generated.destinations.ArtistDetailDestination
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@Destination<RootGraph>(route = "local_search")
fun LocalSearchScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: LocalSearchViewModel = koinViewModel()
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lyricSearchEnabled by viewModel.lyricSearchEnabled.collectAsStateWithLifecycle()
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
        it != LocalSearchTab.Lyrics || lyricSearchEnabled == true
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
        SelectionTopBarState(
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
                tabsReady = lyricSearchEnabled != null,
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
                                ready = lyricSearchEnabled != null,
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

@Composable
private fun LocalSearchPillTabRow(
    tabs: List<LocalSearchTab>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    ready: Boolean = true,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState, enabled = ready)
            .padding(horizontal = 12.dp)
            .then(if (ready) Modifier else Modifier
                .graphicsLayer { alpha = 0f }
                .clearAndSetSemantics {}),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val salt = LocalUiEngine.current == UiEngine.SALT
        tabs.forEachIndexed { index, tab ->
            PillButton(
                text = stringResource(tab.labelRes),
                selected = index == selectedTabIndex,
                enabled = ready,
                style = PillButtonDefaults.style(PillButtonSize.Medium),
                colors = if (salt) PillButtonDefaults.colors() else PillButtonDefaults.colors(
                    containerColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                ),
                onClick = { onTabSelected(index) }
            )
        }
    }
}

/** Salt 外壳：原生顶栏（含选择态槽位）、固定搜索与标签行，内容由 [pages] 提供。 */
@Composable
private fun LocalSearchSaltShell(
    title: String,
    selection: SelectionTopBarState?,
    onBack: () -> Unit,
    isSelectionMode: Boolean,
    searchState: androidx.compose.foundation.text.input.TextFieldState,
    onSearch: (String) -> Unit,
    searchTabs: List<LocalSearchTab>,
    tabsReady: Boolean,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onTabSelected: (Int) -> Unit,
    pages: @Composable () -> Unit,
) {
    PageScaffold(
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
                    ready = tabsReady,
                    selectedTabIndex = pagerState.targetPage,
                    onTabSelected = onTabSelected,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            pages()
        }
    }
}
