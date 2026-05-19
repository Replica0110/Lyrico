package com.lonx.lyrico.ui.components.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.screens.library.LibraryTab
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Music

@Composable
fun LibraryBottomNavigationBar(
    tabs: List<LibraryTab>,
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
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
