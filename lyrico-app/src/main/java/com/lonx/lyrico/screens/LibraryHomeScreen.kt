package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.screens.library.AlbumsPage
import com.lonx.lyrico.screens.library.ArtistsPage
import com.lonx.lyrico.screens.library.LibraryTab
import com.lonx.lyrico.screens.library.SongsPage
import com.lonx.lyrico.ui.components.bar.SongBatchSelectionActions
import com.lonx.lyrico.ui.components.library.LibraryBlurBottomBar
import com.lonx.lyrico.ui.components.library.LibraryBottomNavigationBar
import com.lonx.lyrico.ui.components.library.LibraryNavigationRail
import com.lonx.lyrico.ui.components.library.LocalLibraryBarBlurEnabled
import com.lonx.lyrico.ui.components.library.LocalLibraryBottomContentPadding
import com.lonx.lyrico.ui.components.library.floatingContentBottomPadding
import com.lonx.lyrico.ui.components.library.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import com.lonx.lyrico.ui.components.LocalScaffoldIncludesStartPadding
import com.lonx.lyrico.ui.components.scaffoldBottomPadding
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SongSelectionViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import top.yukonga.miuix.kmp.basic.Scaffold

val SECTIONS_ASC = listOf(
    "0"
) + ('A'..'Z').map { it.toString() } + listOf("#")

val SECTIONS_DESC = SECTIONS_ASC.asReversed()

enum class TopBarState {
    Selection, Default
}
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(start = true, route = "library_home")
fun LibraryHomeScreen(
    navigator: DestinationsNavigator
) {
    val tabs = remember { LibraryTab.entries.toList() }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val viewModel: SongListViewModel = koinActivityViewModel()
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    val settingsRepository: SettingsRepository = koinInject()
    val floatingBottomBarEnabled by settingsRepository.floatingBottomBarEnabled
        .collectAsState(initial = true)
    val barBlurEnabled by settingsRepository.barBlurEnabled.collectAsState(initial = false)
    val floatingBarBlurEnabled by settingsRepository.floatingBarBlurEnabled
        .collectAsState(initial = false)
    val liquidGlassEnabled by settingsRepository.liquidGlassEnabled.collectAsState(initial = false)
    val floatingBackdrop = rememberBlurBackdrop(
        enableBlur = floatingBottomBarEnabled &&
            (floatingBarBlurEnabled || liquidGlassEnabled),
    )
    val standardBottomBackdrop = rememberBlurBackdrop(
        enableBlur = barBlurEnabled && !floatingBottomBarEnabled,
    )
    val songs by viewModel.songs.collectAsState()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsState(initial = false)
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsState()
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var bottomBarPadding by remember { mutableStateOf(0.dp) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxHeight < 520.dp
        val selectedTab = tabs[pagerState.targetPage]

        BackHandler(enabled = isFabMenuExpanded || (isSelectionMode && selectedTab == LibraryTab.Songs)) {
            if (isFabMenuExpanded) {
                isFabMenuExpanded = false
            } else {
                selectionViewModel.exitSelectionMode()
            }
        }

        fun selectTab(tab: LibraryTab) {
            scope.launch {
                selectionViewModel.exitSelectionMode()
                pagerState.animateScrollToPage(tab.ordinal)
            }
        }

        val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val floatingContentPadding = floatingContentBottomPadding(
            systemBottom = systemBottom,
            hasFloatingBar = floatingBottomBarEnabled && !useNavigationRail,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                SideEffect {
                    bottomBarPadding = 0.dp
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    LibraryNavigationRail(
                        tabs = tabs,
                        selectedTab = selectedTab,
                        onTabSelected = ::selectTab,
                    )
                    CompositionLocalProvider(
                        LocalScaffoldIncludesStartPadding provides false,
                        LocalLibraryBottomContentPadding provides 0.dp,
                        LocalLibraryBarBlurEnabled provides barBlurEnabled,
                    ) {
                        LibraryHomePager(
                            tabs = tabs,
                            pagerState = pagerState,
                            navigator = navigator,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else if (floatingBottomBarEnabled) {
                SideEffect {
                    bottomBarPadding = floatingContentPadding
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (floatingBackdrop != null) {
                                Modifier.layerBackdrop(floatingBackdrop)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    CompositionLocalProvider(
                        LocalLibraryBottomContentPadding provides floatingContentPadding,
                        LocalLibraryBarBlurEnabled provides barBlurEnabled,
                    ) {
                        LibraryHomePager(
                            tabs = tabs,
                            pagerState = pagerState,
                            navigator = navigator,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                LibraryBlurBottomBar(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                    backdrop = floatingBackdrop,
                    blurEnabled = floatingBarBlurEnabled,
                    liquidGlassEnabled = liquidGlassEnabled,
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = ::selectTab,
                )
            } else {
                Scaffold(
                    bottomBar = {
                        LibraryBottomNavigationBar(
                            tabs = tabs,
                            selectedTab = selectedTab,
                            onTabSelected = ::selectTab,
                            backdrop = standardBottomBackdrop,
                        )
                    }
                ) { paddingValues ->
                    SideEffect {
                        bottomBarPadding = scaffoldBottomPadding(paddingValues)
                    }

                    val layoutDirection = LocalLayoutDirection.current
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (standardBottomBackdrop != null) {
                                    Modifier.layerBackdrop(standardBottomBackdrop)
                                } else {
                                    Modifier
                                }
                            ),
                    ) {
                        CompositionLocalProvider(
                            // The pager stays behind the bar so the backdrop can sample it;
                            // lists reserve this same space at their end instead.
                            LocalLibraryBottomContentPadding provides scaffoldBottomPadding(paddingValues),
                            LocalLibraryBarBlurEnabled provides barBlurEnabled,
                        ) {
                            LibraryHomePager(
                                tabs = tabs,
                                pagerState = pagerState,
                                navigator = navigator,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        start = paddingValues.calculateStartPadding(layoutDirection),
                                        end = paddingValues.calculateEndPadding(layoutDirection),
                                    )
                            )
                        }
                    }
                }
            }

            SongBatchSelectionActions(
                navigator = navigator,
                songs = songs,
                show = isSelectionMode && selectedTab == LibraryTab.Songs,
                expanded = isFabMenuExpanded,
                selectedSongUris = selectedSongUris,
                modifier = Modifier.padding(bottom = bottomBarPadding),
                onExpandedChange = { isFabMenuExpanded = it },
                onSetSelectionUris = selectionViewModel::setSelectionUris,
                onBatchDelete = selectionViewModel::batchDelete,
                onBatchShare = selectionViewModel::batchShare
            )
        }
    }
}

@Composable
private fun LibraryHomePager(
    tabs: List<LibraryTab>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        modifier = modifier
    ) { page ->
        val pageOffset = (
            pagerState.currentPage - page + pagerState.currentPageOffsetFraction
        ).coerceIn(-1f, 1f)
        val distance = pageOffset.absoluteValue
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - distance * 0.02f
                    alpha = 1f - distance * 0.22f
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            when (tabs[page]) {
                LibraryTab.Songs -> SongsPage(navigator = navigator)
                LibraryTab.Artists -> ArtistsPage(navigator = navigator)
                LibraryTab.Albums -> AlbumsPage(navigator = navigator)
            }
        }
    }
}
