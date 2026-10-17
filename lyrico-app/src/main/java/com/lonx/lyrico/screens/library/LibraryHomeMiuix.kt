package com.lonx.lyrico.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.model.FloatingBarEffect
import com.lonx.lyrico.ui.components.LocalScaffoldIncludesStartPadding
import com.lonx.lyrico.ui.components.blur.*
import com.lonx.lyrico.ui.components.library.*
import com.lonx.lyrico.ui.components.scaffoldBottomPadding
import top.yukonga.miuix.kmp.basic.Scaffold

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
    val floatingBackdrop =
        rememberBlurBackdrop(
            enableBlur = floatingBottomBarEnabled && floatingBarEffect != FloatingBarEffect.NONE
        )
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
                    LocalBarBlurEnabled provides barBlurEnabled,
                ) {
                    content(Modifier.fillMaxSize())
                }
            }
        } else if (floatingBottomBarEnabled) {
            SideEffect {
                onBottomPaddingChange(floatingContentPadding)
            }

            Box(modifier = Modifier.fillMaxSize().blurSource(floatingBackdrop)) {
                CompositionLocalProvider(
                    LocalLibraryBottomContentPadding provides floatingContentPadding,
                    LocalBarBlurEnabled provides barBlurEnabled,
                ) {
                    content(Modifier.fillMaxSize())
                }
            }

            LibraryBlurBottomBar(
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                backdrop = floatingBackdrop,
                effect = floatingBarEffect,
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
                        LocalBarBlurEnabled provides barBlurEnabled,
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
