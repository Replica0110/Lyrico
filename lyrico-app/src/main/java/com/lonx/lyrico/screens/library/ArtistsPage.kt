package com.lonx.lyrico.screens.library

import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.data.model.UiEngine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import com.lonx.lyrico.ui.components.library.SelectableLibraryItem
import com.lonx.lyrico.data.model.entity.ArtistEntity
import com.lonx.lyrico.ui.components.bar.SelectionTopAppBar
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.ui.components.base.ItemMenuAction
import com.lonx.lyrico.viewmodel.ArtistPosterEditViewModel
import com.lonx.lyrico.ui.components.poster.ArtistPosterRemovalHost
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import com.lonx.lyrico.ui.components.poster.previewMedia
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lonx.lyrico.domain.poster.ResolvedArtistPoster
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.data.model.ArtistSortBy
import com.lonx.lyrico.data.model.ArtistSortInfo
import com.lonx.lyrico.ui.components.CoverCandidate
import com.lonx.lyrico.ui.components.artist.ArtistListItemContent
import com.lonx.lyrico.ui.components.bar.AlphabetSideBar
import com.lonx.lyrico.ui.components.bar.rememberAlphabetSideBarScrollController
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.library.LocalLibraryBottomContentPadding
import com.lonx.lyrico.ui.components.library.libraryOverlayInsets
import com.lonx.lyrico.ui.components.library.libraryScrollbarOverlay
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.viewmodel.ArtistLibraryViewModel
import com.lonx.lyrico.viewmodel.SortOrder
import com.moriafly.salt.ui.SaltTheme
import com.ramcosta.composedestinations.generated.destinations.ArtistDetailDestination
import com.ramcosta.composedestinations.generated.destinations.LocalSearchDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import my.nanihadesuka.compose.InternalLazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val SECTIONS_ASC = listOf("0") + ('A'..'Z').map { it.toString() } + listOf("#")
private val SECTIONS_DESC = SECTIONS_ASC.asReversed()

@Composable
fun ArtistsPage(
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    selectionViewModel: com.lonx.lyrico.viewmodel.ArtistSelectionViewModel = koinViewModel(),
    editor: com.lonx.lyrico.viewmodel.ArtistPosterEditViewModel = koinViewModel()
) {
    val viewModel: ArtistLibraryViewModel = koinViewModel()
    val removalViewModel = editor
    val removalState by removalViewModel.state.collectAsStateWithLifecycle()
    var menuArtist by remember { mutableStateOf<ArtistEntity?>(null) }
    var showArtistMenu by remember { mutableStateOf(false) }
    var addPosterArtist by remember { mutableStateOf<ArtistEntity?>(null) }
    var showAddPoster by remember { mutableStateOf(false) }
    val posterMenu by editor.menu.collectAsStateWithLifecycle()
    var previewPoster by remember { mutableStateOf<ResolvedArtistPoster?>(null) }
    var cropTarget by remember { mutableStateOf<ResolvedArtistPoster?>(null) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCrop by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(cropTarget, showCrop) {
        val target = cropTarget
        if (showCrop && target != null) {
            cropBitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(target.bytes, 0, target.bytes.size)
            }
            if (cropBitmap == null) {
                showCrop = false
                android.widget.Toast.makeText(context, R.string.artist_poster_read_error, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val selecting by selectionViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by selectionViewModel.selectedIds.collectAsStateWithLifecycle()
    val swipeAnchor by selectionViewModel.swipeAnchorId.collectAsStateWithLifecycle()
    val swipeLabel = stringResource(if (!selecting) R.string.swipe_selection_enter_selection
        else if (swipeAnchor == null) R.string.swipe_selection_range_start else R.string.swipe_selection_range_end)
    val swipeSecondaryLabel = if (!selecting) stringResource(R.string.swipe_selection_range_start) else null
    var pendingRemoval by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    var artistGridColumns by remember {
        mutableIntStateOf(1)
    }
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    LaunchedEffect(artists) { selectionViewModel.retainVisible(artists) }
    val artistCoverCandidates by viewModel.artistCoverCandidates.collectAsStateWithLifecycle()
    fun coverCandidates(id: Long): List<CoverCandidate> = artistCoverCandidates[id].orEmpty().map {
        CoverCandidate(uri = it.uri.toUri(), lastUpdate = it.lastModified)
    }
    val sortInfo by viewModel.sortInfo.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    // 使用应用级 UI 引擎，页面与共享列表组件保持一致。
    val isSaltEngine = LocalUiEngine.current == UiEngine.SALT
    val topBarBackdrop = rememberBarBlurBackdrop(enabled = LocalUiEngine.current == UiEngine.MIUIX && com.lonx.lyrico.ui.components.blur.rememberBarBlurEnabled())
    val gridState = rememberLazyGridState()
    val alphabetScrollController = rememberAlphabetSideBarScrollController(gridState)

    val sections = remember(sortInfo.order) {
        if (sortInfo.order == SortOrder.ASC) SECTIONS_ASC else SECTIONS_DESC
    }
    val sectionIndexMap = remember(artists, sortInfo, artistGridColumns) {
        val map = mutableMapOf<String, Int>()

        if (sortInfo.sortBy.supportsIndex) {
            artists.forEachIndexed { index, artist ->
                if (!map.containsKey(artist.groupKey)) {
                    val rowStartIndex = index - index % artistGridColumns
                    map[artist.groupKey] = rowStartIndex
                }
            }
        }

        map
    }
    val enableIndex = artists.isNotEmpty() && sortInfo.sortBy.supportsIndex
    val refreshTexts = listOf(
        stringResource(R.string.pull_to_refresh),
        stringResource(R.string.release_to_refresh),
        stringResource(R.string.refreshing),
        stringResource(R.string.refresh_success)
    )
    val pageContent: @Composable (PaddingValues) -> Unit = { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Salt 顶栏用 BasicScreen 自带的 backdrop，这里不再叠第二层模糊
                .then(if (isSaltEngine) Modifier else Modifier.blurSource(topBarBackdrop))
        ) {
            if (artists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(scaffoldTopHorizontalPadding(paddingValues))
                        .fillMaxSize()
                ) {
                    IllustratedEmptyState(
                        kind = EmptyStateKind.Artists,
                        title = stringResource(R.string.empty_artists_title),
                        modifier = Modifier.align(Alignment.Center),
                        icon = LucideIcons.RefreshCw,
                        actionText = stringResource(R.string.refresh),
                        onAction = { viewModel.refreshSongs() },
                    )
                }
            } else {
                LibraryPullToRefresh(
                    isRefreshing = scanState.isScanning,
                    onRefresh = { viewModel.refreshSongs() },
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    refreshTexts = refreshTexts
                ) {
                    BoxWithConstraints {
                        val targetColumns = if (maxWidth >= 600.dp) 2 else 1

                        LaunchedEffect(targetColumns) {
                            artistGridColumns = targetColumns
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(artistGridColumns),
                            modifier = Modifier
                                .libraryScrollFeedback(topAppBarScrollBehavior)
                                .fillMaxHeight(),
                            state = gridState,
                            overscrollEffect = rememberLibraryOverscrollEffect(),
                            contentPadding = scaffoldContentPadding(
                                paddingValues = paddingValues,
                                bottomExtra = LocalLibraryBottomContentPadding.current,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(
                                items = artists,
                                key = { it.id }
                            ) { artist ->
                                SelectableLibraryItem(
                                    itemKey = artist.id,
                                    onClick = { navigator.navigate(ArtistDetailDestination(artistId = artist.id)) },
                                    isSelectionMode = selecting,
                                    isSelected = artist.id.toString() in selectedIds,
                                    swipeSelectionLabel = swipeLabel,
                                    swipeSelectionSecondaryLabel = swipeSecondaryLabel,
                                    onToggleSelection = { selectionViewModel.toggle(artist.id) },
                                    onSwipeSelection = { selectionViewModel.swipeSelect(artist, artists) }
                                ) {
                                    ArtistListItemContent(
                                        artist = artist,
                                        coverCandidates = coverCandidates(artist.id),
                                        trailingContent = {
                                            SongListItemActions(
                                                isSelectionMode = selecting, isSelected = artist.id.toString() in selectedIds,
                                                onToggleSelection = { selectionViewModel.toggle(artist.id) },
                                                onShowMenu = {
                                                    menuArtist = artist
                                                    editor.loadPoster(artist.id, coverCandidates(artist.id))
                                                    showArtistMenu = true
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (!enableIndex) {
                    InternalLazyVerticalGridScrollbar(
                        state = gridState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .libraryScrollbarOverlay(paddingValues = paddingValues),
                        settings = ScrollbarSettings.Default.copy(
                            alwaysShowScrollbar = true,
                            selectionMode = ScrollbarSelectionMode.Full,
                            thumbUnselectedColor = if (isSaltEngine) {
                                SaltTheme.colors.subText
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantActions
                            },
                            thumbSelectedColor = if (isSaltEngine) {
                                SaltTheme.colors.subText
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantActions
                            },
                        ),
                    )
                }
                if (enableIndex) {
                    AlphabetSideBar(
                        sections = sections,
                        sectionIndexMap = sectionIndexMap,
                        order = sortInfo.order,
                        scrollController = alphabetScrollController,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .libraryOverlayInsets(
                                paddingValues = paddingValues,
                                extraTop = 16.dp,
                                extraBottom = 16.dp,
                            )
                            .fillMaxHeight()
                    )
                }
            }

        }

    }
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltLibraryPage(
            selection = if (selecting) LibrarySelectionState(
            allSelected = artists.isNotEmpty() && selectedIds.size == artists.size,
            title = stringResource(R.string.selection_mode_selected_count, selectedIds.size),
            onClose = selectionViewModel::exitSelectionMode,
            onToggleAll = { if (selectedIds.size == artists.size) selectionViewModel.deselectAll() else selectionViewModel.selectAll(artists) },
            ) else null,
            title = stringResource(R.string.artist_list_title, artists.size),
            onSettings = { navigator.navigate(SettingsDestination()) },
            onSearch = { navigator.navigate(LocalSearchDestination) },
            menu = ArtistSortBy.entries.map { sortBy ->
                val selected = sortInfo.sortBy == sortBy
                LibraryMenuAction(
                    title = stringResource(sortBy.labelRes),
                    selected = selected,
                    summary = if (selected) stringResource(
                        if (sortInfo.order == SortOrder.ASC) R.string.sort_ascending else R.string.sort_descending
                    ) else null,
                ) {
                    viewModel.onSortChange(ArtistSortInfo(sortBy, if (selected && sortInfo.order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC))
                }
            }, content = pageContent,
        )
    } else Scaffold(
        topBar = {
            BlurredTopBar(backdrop = topBarBackdrop) {
                AnimatedContent(
                    targetState = selecting, label = "TopBarAnimation",
                    transitionSpec = {
                        val enter = fadeIn(tween(300)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { -it / 3 }
                        val exit = fadeOut(tween(300)) + slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { -it / 3 }
                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    }
                ) { selectionMode ->
                    if (selectionMode) {
                        SelectionTopAppBar(
                            selectedCount = selectedIds.size,
                            allSelected = artists.isNotEmpty() && selectedIds.size == artists.size,
                            scrollBehavior = topAppBarScrollBehavior, color = Color.Transparent, applyInsets = false,
                            onToggleAll = { if (selectedIds.size == artists.size) selectionViewModel.deselectAll() else selectionViewModel.selectAll(artists) },
                            onClose = selectionViewModel::exitSelectionMode
                        )
                    } else SmallTopAppBar(
                        title = stringResource(R.string.artist_list_title, artists.size),
                        color = Color.Transparent,
                        modifier = Modifier,
                        scrollBehavior = topAppBarScrollBehavior,
                        defaultWindowInsetsPadding = false,
                        navigationIcon = {
                            IconButton(onClick = { navigator.navigate(SettingsDestination()) }) {
                                Icon(
                                    imageVector = LucideIcons.Settings,
                                    contentDescription = null
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { navigator.navigate(LocalSearchDestination) }) {
                                Icon(
                                    imageVector = LucideIcons.Search,
                                    contentDescription = stringResource(R.string.cd_search)
                                )
                            }
                            OverlayIconDropdownMenu(
                                entries = listOf(
                                    artistSortDropdownEntry(sortInfo, viewModel::onSortChange),
                                )
                            ) {
                                Icon(
                                    imageVector = LucideIcons.Sort,
                                    contentDescription = stringResource(R.string.cd_sort)
                                )
                            }

                        }
                    )
                }
            }
        }
    ) { paddingValues -> pageContent(paddingValues) }

    com.lonx.lyrico.ui.components.base.ItemMenuBottomSheet(
        show = showArtistMenu, label = menuArtist?.name.orEmpty(), enableNestedScroll = false,
        onDismissRequest = { showArtistMenu = false },
        onDismissFinished = { if (!showArtistMenu) menuArtist = null }
    ) {
        val source = posterMenu.source
        when {
            posterMenu.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            posterMenu.error -> IllustratedEmptyState(
                kind = EmptyStateKind.Cover,
                title = stringResource(R.string.artist_poster_read_error),
                compact = true,
                icon = LucideIcons.RefreshCw,
                actionText = stringResource(R.string.refresh),
                onAction = { menuArtist?.let { editor.loadPoster(it.id, coverCandidates(it.id)) } }
            )
            source == null -> IllustratedEmptyState(
                kind = EmptyStateKind.Cover,
                title = stringResource(R.string.artist_poster_missing_title),
                compact = true,
                summary = stringResource(R.string.artist_poster_missing_summary),
                icon = LucideIcons.Image,
                actionText = stringResource(R.string.label_select_artist_image),
                onAction = {
                    if (!removalState.isRunning) {
                        addPosterArtist = menuArtist
                        showAddPoster = true
                        showArtistMenu = false
                    }
                },
            )
            else -> {
                ItemMenuAction(icon = LucideIcons.Image, tone = PreferenceIconTone.Appearance,
                    title = stringResource(R.string.poster_view_artwork), onClick = {
                    previewPoster = source
                    showArtistMenu = false
                })
                ItemMenuAction(
                    icon = LucideIcons.Crop, tone = PreferenceIconTone.Tools,
                    title = stringResource(R.string.label_crop_artist_image),
                    summary = if (!source.canCrop) stringResource(R.string.artist_poster_video_crop_unavailable) else null,
                    enabled = source.canCrop && !removalState.isRunning,
                    onClick = { cropTarget = source; showCrop = true; showArtistMenu = false }
                )
                ItemMenuAction(icon = LucideIcons.Trash, tone = PreferenceIconTone.Danger,
                    title = stringResource(R.string.poster_remove_artwork), enabled = !removalState.isRunning, onClick = {
                        menuArtist?.let { pendingRemoval = setOf(it.id) }
                        showArtistMenu = false
                    })
            }
        }
    }
    addPosterArtist?.let { artist ->
        key(artist.id) {
            val addViewModel: com.lonx.lyrico.viewmodel.ArtistDetailViewModel = koinViewModel(
                key = "artist-poster-add-${artist.id}",
                parameters = { org.koin.core.parameter.parametersOf(artist.id) }
            )
            com.lonx.lyrico.ui.components.poster.ArtistPosterAddHost(
                viewModel = addViewModel,
                show = showAddPoster,
                onDismissRequest = { showAddPoster = false },
                enabled = !removalState.isRunning
            )
        }
    }
    previewPoster?.let { source ->
        com.lonx.lyrico.ui.components.poster.ArtistPosterPreview(
            items = listOf(source.previewMedia()), title = { source.artistName },
            onDismiss = { previewPoster = null },
            editingEnabled = !removalState.isRunning,
            onCrop = { _, bitmap -> editor.cropPoster(source, bitmap) }
        )
    }
    com.lonx.lyrico.ui.components.crop.ImageCropperSheet(
        show = showCrop, bitmap = cropBitmap, title = stringResource(R.string.label_crop_artist_image),
        onCrop = { bitmap -> cropTarget?.let { editor.cropPoster(it, bitmap) }; showCrop = false },
        onDismissRequest = { showCrop = false },
        onDismissFinished = { if (!showCrop) { cropTarget = null; cropBitmap = null } }
    )
    ArtistPosterRemovalHost(removalViewModel, pendingRemoval, { pendingRemoval = emptySet() }, showProgress = false)
}

@Composable
private fun artistSortDropdownEntry(
    sortInfo: ArtistSortInfo,
    onSortChange: (ArtistSortInfo) -> Unit
): DropdownEntry {
    return DropdownEntry(
        items = ArtistSortBy.entries.map { sortBy ->
            val isSelected = sortInfo.sortBy == sortBy
            DropdownItem(
                text = stringResource(sortBy.labelRes),
                selected = isSelected,
                summary = if (isSelected) {
                    stringResource(
                        if (sortInfo.order == SortOrder.ASC) {
                            R.string.sort_ascending
                        } else {
                            R.string.sort_descending
                        }
                    )
                } else {
                    null
                },
                onClick = {
                    onSortChange(
                        ArtistSortInfo(
                            sortBy = sortBy,
                            order = if (isSelected && sortInfo.order == SortOrder.ASC) {
                                SortOrder.DESC
                            } else {
                                SortOrder.ASC
                            }
                        )
                    )
                }
            )
        }
    )
}
