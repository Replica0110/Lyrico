package com.lonx.lyrico

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import org.koin.androidx.compose.koinViewModel

@Composable
fun LyricoApp() {
    val navController = rememberNavController()
    val songListViewModel: SongListViewModel = koinViewModel()

    // When the app's main UI is composed, trigger an initial scan if the database is empty.
    // This runs only once when LyricoApp is first displayed after permissions are granted.
    LaunchedEffect(Unit) {
        songListViewModel.initialScanIfEmpty()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val showSearch = remember { mutableStateOf(false) }
//        NavHost(navController = navController, startDestination = Screen.SongList.route) {
//            composable(Screen.SongList.route) {
//                SongListScreen(
//                    showSearch = showSearch,
//                    onSongClick = { songInfo ->
//                        SongDataHolder.selectedSongInfo = songInfo
//                        navController.navigate(Screen.EditMetadata.route)
//                    },
//                    onSettingsClick = {
//                        navController.navigate(Screen.Settings.route)
//                    },
//                    viewModel = koinViewModel()
//                )
//            }
//            composable(Screen.EditMetadata.route) { backStackEntry ->
//                val searchResult = backStackEntry.savedStateHandle.get<SongSearchResult>("selectedResult")
//                val selectedLyrics = backStackEntry.savedStateHandle.get<String>("selectedLyrics")
//
//                EditMetadataScreen(
//                    searchResult = searchResult,
//                    selectedLyrics = selectedLyrics,
//                    onBackClick = { navController.popBackStack() },
//                    onSearchClick = { keyword ->
//                        navController.navigate(Screen.SearchResults.createRoute(keyword))
//                    },
//                    onSearchResult = {
//                        backStackEntry.savedStateHandle.remove<SongSearchResult>("selectedResult")
//                    },
//                    onLyricsResult = {
//                        backStackEntry.savedStateHandle.remove<String>("selectedLyrics")
//                    }
//                )
//            }
//            composable(Screen.SearchResults.route) { backStackEntry ->
//                val keyword = backStackEntry.arguments?.getString("keyword")
//                SearchResultsScreen(
//                    keyword = keyword,
//                    onBackClick = { navController.popBackStack() },
//                    onResultSelect = { result, lyrics ->
//                        navController.previousBackStackEntry?.savedStateHandle?.set("selectedResult", result)
//                        navController.previousBackStackEntry?.savedStateHandle?.set("selectedLyrics", lyrics)
//                        navController.popBackStack()
//                    }
//                )
//            }
//            composable(Screen.Settings.route) {
//                SettingsScreen(
//                    onBackClick = { navController.popBackStack() }
//                )
//            }
//        }
        DestinationsNavHost(
            navGraph = NavGraphs.root,
            navController = navController,
            dependenciesContainerBuilder =  {

            },
            defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                    {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                        )
                    }

                override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                    {
                        slideOutHorizontally(
                            targetOffsetX = { -it / 5 },
                            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                        )
                    }

                override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                    {
                        slideInHorizontally(
                            initialOffsetX = { -it / 5 },
                            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                        )
                    }

                override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                    {
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                        )
                    }
            }
        )
    }
}
