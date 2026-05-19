package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import com.lonx.lyrico.screens.library.AlbumsPage
import com.lonx.lyrico.screens.library.ArtistsPage
import com.lonx.lyrico.screens.library.LibraryTab
import com.lonx.lyrico.screens.library.SongsPage
import com.lonx.lyrico.ui.components.library.LibraryBottomNavigationBar
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
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
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        bottomBar = {
            LibraryBottomNavigationBar(
                tabs = tabs,
                selectedTab = tabs[pagerState.currentPage],
                onTabSelected = { tab ->
                    scope.launch {
                        pagerState.animateScrollToPage(tab.ordinal)
                    }
                }
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = paddingValues.calculateBottomPadding()
                )
        ) { page ->
            when (tabs[page]) {
                LibraryTab.Songs -> SongsPage(navigator = navigator)
                LibraryTab.Artists -> ArtistsPage(navigator = navigator)
                LibraryTab.Albums -> AlbumsPage(navigator = navigator)
            }
        }
    }
}


