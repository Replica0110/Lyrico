package com.lonx.lyrico.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ArtistSortBy
import com.lonx.lyrico.data.model.ArtistSortInfo
import com.lonx.lyrico.ui.components.CoverCandidate
import com.lonx.lyrico.ui.components.artist.ArtistListItem
import com.lonx.lyrico.ui.components.bar.AlphabetSideBar
import com.lonx.lyrico.ui.components.bar.rememberAlphabetSideBarScrollController
import com.lonx.lyrico.ui.components.library.LibraryEmptyState
import com.lonx.lyrico.ui.components.scaffoldTopAppBarInsetsPadding
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.viewmodel.ArtistLibraryViewModel
import com.lonx.lyrico.viewmodel.SortOrder
import com.ramcosta.composedestinations.generated.destinations.ArtistDetailDestination
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults

private val SECTIONS_ASC = listOf("0") + ('A'..'Z').map { it.toString() } + listOf("#")
private val SECTIONS_DESC = SECTIONS_ASC.asReversed()

@Composable
fun ArtistsPage(
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier
) {
    val viewModel: ArtistLibraryViewModel = koinViewModel()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    var artistGridColumns by remember {
        mutableIntStateOf(1)
    }
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val artistCoverCandidates by viewModel.artistCoverCandidates.collectAsStateWithLifecycle()
    val sortInfo by viewModel.sortInfo.collectAsStateWithLifecycle()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
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
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.artist_list_title, artists.size),
                modifier = Modifier.scaffoldTopAppBarInsetsPadding(),
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
                            artistSortDropdownEntry(sortInfo, viewModel::onSortChange),
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(scaffoldTopHorizontalPadding(paddingValues))
                .fillMaxSize()
        ) {
            if (artists.isEmpty()) {
                LibraryEmptyState(
                    title = stringResource(R.string.empty_artists_title),
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
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val targetColumns = if (maxWidth >= 600.dp) 2 else 1

                            LaunchedEffect(targetColumns) {
                                artistGridColumns = targetColumns
                            }


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
                                    columns = GridCells.Fixed(artistGridColumns),
                                    modifier = Modifier
                                        .scrollEndHaptic()
                                        .overScrollVertical()
                                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                        .fillMaxHeight(),
                                    state = gridState,
                                    overscrollEffect = null,
                                    contentPadding = PaddingValues(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    items(
                                        items = artists,
                                        key = { it.id }
                                    ) { artist ->
                                        ArtistListItem(
                                            artist = artist,
                                            coverCandidates = artistCoverCandidates[artist.id]
                                                .orEmpty()
                                                .map { candidate ->
                                                    CoverCandidate(
                                                        uri = candidate.uri.toUri(),
                                                        lastUpdate = candidate.lastModified
                                                    )
                                                },
                                            onClick = {
                                                navigator.navigate(ArtistDetailDestination(artistId = artist.id))
                                            }
                                        )
                                    }
                                }
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
                            .padding(
                                top = 16.dp,
                                bottom = 16.dp
                            )
                    )
                }
            }
        }
    }
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
