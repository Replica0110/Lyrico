package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.screens.library.LibraryTab
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LibraryBottomNavigationBar(
    tabs: List<LibraryTab>,
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
) {
    val haptic = LocalHapticFeedback.current
    LibraryBlurredBar(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
    ) {
        NavigationBar(
            modifier = modifier,
            color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selectedTab,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(tab)
                    },
                    icon = tab.icon,
                    label = stringResource(tab.titleRes)
                )
            }
        }
    }
}

@Composable
fun LibraryNavigationRail(
    tabs: List<LibraryTab>,
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    NavigationRail(
        modifier = modifier
    ) {
        tabs.forEach { tab ->
            NavigationRailItem(
                selected = tab == selectedTab,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTabSelected(tab)
                },
                icon = tab.icon,
                label = stringResource(tab.titleRes)
            )
        }
    }
}

private val LibraryTab.icon: ImageVector
    get() = when (this) {
        LibraryTab.Songs -> MiuixIcons.Music
        LibraryTab.Artists -> MiuixIcons.Contacts
        LibraryTab.Albums -> MiuixIcons.Album
    }
