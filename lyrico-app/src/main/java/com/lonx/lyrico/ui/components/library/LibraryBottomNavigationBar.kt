package com.lonx.lyrico.ui.components.library

import com.lonx.lyrico.ui.components.icons.LucideIcons
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
import com.lonx.lyrico.ui.components.blur.BlurredBar
import com.lonx.lyrico.data.model.FloatingBarEffect
import com.lonx.lyrico.screens.library.LibraryTab
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.blur.LayerBackdrop
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
    BlurredBar(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
    ) {
        NavigationBar(
            modifier = modifier,
            color = Color.Transparent,
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
 * 手机端悬浮底栏。模糊和液态玻璃都需要 Android 13+ 的 RenderEffect，
 * 不支持时自动降级为描边样式。
 */
@Composable
fun LibraryBlurBottomBar(
    backdrop: LayerBackdrop?,
    effect: FloatingBarEffect,
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
        effect = effect,
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
        LibraryTab.Songs -> LucideIcons.Music
        LibraryTab.Artists -> LucideIcons.Users
        LibraryTab.Albums -> LucideIcons.Disc
    }
