package com.lonx.lyrico.ui.screens.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.lonx.lyrico.data.model.appearance.FloatingBarEffect
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine

/** Dispatch presentation only; pager, selection, and navigation stay in LibraryHomeScreen. */
@Composable
internal fun LibraryHomePresentation(
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
    when (LocalUiEngine.current) {
        UiEngine.SALT ->
            LibraryHomeSalt(
                tabs,
                selectedTab,
                useNavigationRail,
                floatingBottomBarEnabled,
                floatingBarEffect,
                barBlurEnabled,
                onTabSelected,
                onBottomPaddingChange,
            ) {
                content(Modifier.fillMaxSize())
            }
        UiEngine.MIUIX ->
            LibraryHomeMiuix(
                tabs,
                selectedTab,
                useNavigationRail,
                floatingBottomBarEnabled,
                floatingBarEffect,
                barBlurEnabled,
                onTabSelected,
                onBottomPaddingChange,
                content,
            )
    }
}
