package com.lonx.lyrico

import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.theme.UiEngine
import com.moriafly.salt.ui.SaltTheme
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.spec.Direction
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface

@Composable
fun LyricoApp(externalUri: Uri?) {
    val content: @Composable () -> Unit = {
        val externalUriString = externalUri?.toString()
        key(externalUriString) {
            val navController = rememberNavController()
            val startDirection: Direction =
                externalUriString?.let { EditMetadataDestination(it) }
                    ?: NavGraphs.root.defaultStartDirection

            Log.d("LyricoApp", "LyricoApp: $startDirection")
            DestinationsNavHost(
                navGraph = NavGraphs.root,
                navController = navController,
                start = startDirection,
                dependenciesContainerBuilder = {
                },
                defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                        {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }

                    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                        {
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }

                    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                        {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }

                    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                        {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                }
            )
        }
    }

    if (LocalUiEngine.current == UiEngine.Miuix) {
        MiuixSurface(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    } else {
        MaterialSurface(
            modifier = Modifier.fillMaxSize(),
            color = SaltTheme.colors.background,
            content = content
        )
    }
}
