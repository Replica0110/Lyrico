package com.lonx.lyrico.screens.library

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.AlbumSortBy
import com.lonx.lyrico.data.model.AlbumSortInfo
import com.lonx.lyrico.data.model.entity.AlbumEntity
import com.lonx.lyrico.ui.components.bar.AlphabetSideBar
import com.lonx.lyrico.ui.components.bar.rememberAlphabetSideBarScrollController
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.library.AlbumActionBottomSheet
import com.lonx.lyrico.ui.components.library.AlbumGridItem
import com.lonx.lyrico.ui.components.library.AlbumReplayGainProgressBottomSheet
import com.lonx.lyrico.ui.components.library.LibraryEmptyState
import com.lonx.lyrico.ui.components.library.LibraryBlurredBar
import com.lonx.lyrico.ui.components.library.LocalLibraryBarBlurEnabled
import com.lonx.lyrico.ui.components.library.LocalLibraryBottomContentPadding
import com.lonx.lyrico.ui.components.library.rememberAlbumGridTextStyle
import com.lonx.lyrico.ui.components.library.rememberBlurBackdrop
import com.lonx.lyrico.ui.components.scaffoldTopAppBarInsetsPadding
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.viewmodel.AlbumActionsViewModel
import com.lonx.lyrico.viewmodel.AlbumLibraryViewModel
import com.lonx.lyrico.viewmodel.SortOrder
import com.ramcosta.composedestinations.generated.destinations.AlbumDetailDestination
import com.ramcosta.composedestinations.generated.destinations.LocalSearchDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val SECTIONS_ASC = listOf("0") + ('A'..'Z').map { it.toString() } + listOf("#")
private val SECTIONS_DESC = SECTIONS_ASC.asReversed()

@Composable
fun AlbumsPage(
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier
) {
    val viewModel: AlbumLibraryViewModel = koinViewModel()
    val albumActionsViewModel: AlbumActionsViewModel = koinViewModel()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val albumActionsUiState by albumActionsViewModel.uiState.collectAsStateWithLifecycle()

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val sortInfo by viewModel.sortInfo.collectAsStateWithLifecycle()
    val albumGridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val albumTextStyle = rememberAlbumGridTextStyle(albumGridColumns)
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberBlurBackdrop(LocalLibraryBarBlurEnabled.current)
    val gridState = rememberLazyGridState()
    val alphabetScrollController = rememberAlphabetSideBarScrollController(gridState)
    val context = LocalContext.current
    var selectedAlbum by remember { mutableStateOf<AlbumEntity?>(null) }
    var showAlbumActionSheet by remember { mutableStateOf(false) }
    var showDeleteAlbumDialog by remember { mutableStateOf(false) }

    LaunchedEffect(albumActionsViewModel, context) {
        albumActionsViewModel.events.collect { message ->
            message.asString(context)?.let { text ->
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val sections = remember(sortInfo.order) {
        if (sortInfo.order == SortOrder.ASC) SECTIONS_ASC else SECTIONS_DESC
    }
    val sectionIndexMap = remember(albums, sortInfo, albumGridColumns) {
        val map = mutableMapOf<String, Int>()
        val columns = albumGridColumns.coerceAtLeast(1)

        if (sortInfo.sortBy.supportsIndex) {
            albums.forEachIndexed { index, album ->
                if (!map.containsKey(album.groupKey)) {
                    val rowStartIndex = index - index % columns
                    map[album.groupKey] = rowStartIndex
                }
            }
        }
        map
    }
    val enableIndex = albums.isNotEmpty() && sortInfo.sortBy.supportsIndex
    val refreshTexts = listOf(
        stringResource(R.string.pull_to_refresh),
        stringResource(R.string.release_to_refresh),
        stringResource(R.string.refreshing),
        stringResource(R.string.refresh_success)
    )
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            LibraryBlurredBar(
                backdrop = topBarBackdrop,
                modifier = Modifier.scaffoldTopAppBarInsetsPadding(),
            ) {
            SmallTopAppBar(
                title = stringResource(R.string.album_list_title, albums.size),
                color = if (topBarBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                modifier = Modifier,
                scrollBehavior = topAppBarScrollBehavior,
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(onClick = { navigator.navigate(SettingsDestination()) }) {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navigator.navigate(LocalSearchDestination) }) {
                        Icon(
                            imageVector = MiuixIcons.Search,
                            contentDescription = stringResource(R.string.cd_search)
                        )
                    }
                    OverlayIconDropdownMenu(
                        entries = listOf(
                            albumGridColumnsDropdownEntry(
                                columns = albumGridColumns,
                                onColumnsChange = viewModel::setGridColumns
                            ),
                            albumSortDropdownEntry(sortInfo, viewModel::onSortChange),
                        )
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Sort,
                            contentDescription = stringResource(R.string.cd_sort)
                        )
                    }
                }
            )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (topBarBackdrop != null) Modifier.layerBackdrop(topBarBackdrop) else Modifier)
        ) {
            if (albums.isEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(scaffoldTopHorizontalPadding(paddingValues))
                        .fillMaxSize()
                ) {
                    LibraryEmptyState(
                        title = stringResource(R.string.empty_albums_title),
                        summary = stringResource(R.string.empty_library_index_summary),
                        modifier = Modifier.align(Alignment.Center),
                        action = {
                            TextButton(
                                text = stringResource(R.string.refresh),
                                onClick = { viewModel.refreshSongs() },
                                colors = MiuixButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                    )
                }
            } else {
                PullToRefresh(
                    isRefreshing = scanState.isScanning,
                    onRefresh = { viewModel.refreshSongs() },
                    modifier = Modifier.fillMaxSize(),
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    refreshTexts = refreshTexts
                ) {
                    LazyVerticalGridScrollbar(
                        state = gridState,
                        settings = ScrollbarSettings.Default.copy(
                            enabled = !enableIndex,
                            alwaysShowScrollbar = !enableIndex,
                            selectionMode = ScrollbarSelectionMode.Full,
                            thumbUnselectedColor = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            thumbSelectedColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(albumGridColumns),
                            state = gridState,
                            modifier = Modifier
                                .scrollEndHaptic()
                                .overScrollVertical()
                                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                .fillMaxSize(),
                            contentPadding = scaffoldContentPadding(
                                paddingValues = paddingValues,
                                topExtra = 12.dp,
                                bottomExtra = 12.dp + LocalLibraryBottomContentPadding.current,
                                horizontalExtra = 12.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            overscrollEffect = null
                        ) {
                            items(
                                items = albums,
                                key = { it.id }
                            ) { album ->
                                AlbumGridItem(
                                    albumName = album.name,
                                    summary = buildAlbumSummary(
                                        songCountText = stringResource(
                                            R.string.song_count,
                                            album.songCount
                                        ),
                                        year = album.year
                                    ),
                                    coverUri = album.coverSongUri,
                                    coverLastModified = album.coverSongLastModified,
                                    titleStyle = albumTextStyle.title,
                                    summaryStyle = albumTextStyle.summary,
                                    titleMaxLines = albumTextStyle.titleMaxLines,
                                    onClick = {
                                        navigator.navigate(AlbumDetailDestination(albumId = album.id))
                                    },
                                    onLongClick = {
                                        selectedAlbum = album
                                        showAlbumActionSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
                if (enableIndex) {
                    AlphabetSideBar(
                        sections = sections,
                        sectionIndexMap = sectionIndexMap,
                        order = sortInfo.order,
                        scrollController = alphabetScrollController,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(scaffoldTopHorizontalPadding(paddingValues))
                            .padding(
                                top = 16.dp,
                                bottom = 16.dp
                            )
                    )
                }
            }
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

    AlbumReplayGainProgressBottomSheet(
        uiState = albumActionsUiState,
        onDismissRequest = albumActionsViewModel::closeAlbumReplayGainProgressDialog,
        onDismissFinished = albumActionsViewModel::clearAlbumReplayGainProgressDialog,
        onAbort = albumActionsViewModel::cancelAlbumReplayGain
    )
}

private fun buildAlbumSummary(
    songCountText: String,
    year: String?
): String {
    return listOfNotNull(
        songCountText,
        year?.takeIf { it.isNotBlank() }
    ).joinToString(" ")
}

@Composable
private fun albumGridColumnsDropdownEntry(
    columns: Int,
    onColumnsChange: (Int) -> Unit
): DropdownEntry {
    return DropdownEntry(
        items = listOf(2, 3, 4).map { count ->
            DropdownItem(
                text = stringResource(R.string.album_grid_columns_format, count),
                selected = columns == count,
                onClick = { onColumnsChange(count) }
            )
        }
    )
}

@Composable
private fun albumSortDropdownEntry(
    sortInfo: AlbumSortInfo,
    onSortChange: (AlbumSortInfo) -> Unit
): DropdownEntry {
    return DropdownEntry(
        items = AlbumSortBy.entries.map { sortBy ->
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
                        AlbumSortInfo(
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
