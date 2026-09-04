package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.screens.library.LibraryTab
import top.yukonga.miuix.kmp.basic.Icon
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

/**
 * 手机端悬浮毛玻璃底栏。底部毛玻璃需要 Android 13+ 的 blur 库支持，
 * 不支持时自动降级为描边样式。
 */
@Composable
fun LibraryBlurBottomBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    liquidGlassEnabled: Boolean,
    tabs: List<LibraryTab>,
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    BlurNavigationBarV2(
        modifier = modifier,
        backdrop = backdrop,
        selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
        itemCount = tabs.size,
        isBlurEnabled = blurEnabled,
        isLiquidGlassEnabled = liquidGlassEnabled,
        onSelectionChanged = { index ->
            tabs.getOrNull(index)?.let(onTabSelected)
        },
    ) {
        tabs.forEachIndexed { index, tab ->
            BlurNavigationBarItemV2(
                index = index,
                selected = tab == selectedTab,
                onClick = {},
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = stringResource(tab.titleRes),
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = stringResource(tab.titleRes),
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
