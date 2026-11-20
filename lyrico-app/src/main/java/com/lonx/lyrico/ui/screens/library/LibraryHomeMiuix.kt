package com.lonx.lyrico.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.model.appearance.FloatingBarEffect
import com.lonx.lyrico.ui.components.blur.*
import com.lonx.lyrico.ui.components.blur.resolveFloatingBarEffect
import com.lonx.lyrico.ui.components.effect.glass.glassSource
import com.lonx.lyrico.ui.components.effect.glass.rememberGlassSource
import com.lonx.lyrico.ui.components.library.*
import com.lonx.lyrico.ui.components.scaffold.LocalScaffoldIncludesStartPadding
import com.lonx.lyrico.ui.components.scaffold.scaffoldBottomPadding
import com.moriafly.salt.ui.UnstableSaltUiApi
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun LibraryHomeMiuix(
    tabs: List<LibraryTab>,
    selectedTab: LibraryTab,
    useNavigationRail: Boolean,
    floatingBottomBarEnabled: Boolean,
    floatingBarEffect: FloatingBarEffect,
    barBlurEnabled: Boolean,
    onTabSelected: (LibraryTab) -> Unit,
    onBottomPaddingChange: (Dp) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val effect = resolveFloatingBarEffect(barBlurEnabled, floatingBarEffect)
    val floatingBackdrop = rememberGlassSource().takeIf { effect != FloatingBarEffect.NONE }
    val standardBottomBackdrop =
        rememberBarBlurBackdrop(enabled = barBlurEnabled && !floatingBottomBarEnabled)
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingContentPadding =
        floatingContentBottomPadding(
            systemBottom = systemBottom,
            hasFloatingBar = floatingBottomBarEnabled && !useNavigationRail,
        )
    Box(Modifier.fillMaxSize()) {
        if (useNavigationRail) {
            SideEffect {
                onBottomPaddingChange(0.dp)
            }

            Row(modifier = Modifier.fillMaxSize()) {
                LibraryNavigationRail(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                )
                CompositionLocalProvider(
                    LocalScaffoldIncludesStartPadding provides false,
                    LocalLibraryBottomContentPadding provides 0.dp,
                ) {
                    content(Modifier.fillMaxSize())
                }
            }
        } else if (floatingBottomBarEnabled) {
            SideEffect {
                onBottomPaddingChange(floatingContentPadding)
            }

            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface)
                    .then(if (floatingBackdrop != null) Modifier.glassSource(floatingBackdrop) else Modifier),
            ) {
                CompositionLocalProvider(
                    LocalLibraryBottomContentPadding provides floatingContentPadding,
                ) {
                    content(Modifier.fillMaxSize())
                }
            }

            LibraryBlurBottomBar(
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                backdrop = floatingBackdrop,
                effect = effect,
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        } else {
            Scaffold(
                bottomBar = {
                    LibraryBottomNavigationBar(
                        tabs = tabs,
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                        backdrop = standardBottomBackdrop,
                    )
                }
            ) { paddingValues ->
                SideEffect {
                    onBottomPaddingChange(scaffoldBottomPadding(paddingValues))
                }

                val layoutDirection = LocalLayoutDirection.current
                Box(modifier = Modifier.fillMaxSize().blurSource(standardBottomBackdrop)) {
                    CompositionLocalProvider(
                        // The pager stays behind the bar so the backdrop can sample it;
                        // lists reserve this same space at their end instead.
                        LocalLibraryBottomContentPadding provides
                            scaffoldBottomPadding(paddingValues),
                    ) {
                        content(
                            Modifier.fillMaxSize()
                                .padding(
                                    start = paddingValues.calculateStartPadding(layoutDirection),
                                    end = paddingValues.calculateEndPadding(layoutDirection),
                                )
                        )
                    }
                }
            }
        }
    }
}
