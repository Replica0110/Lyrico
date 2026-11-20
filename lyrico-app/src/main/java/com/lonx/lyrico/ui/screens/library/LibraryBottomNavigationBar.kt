package com.lonx.lyrico.ui.screens.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.settings.FloatingBarEffect
import com.lonx.lyrico.ui.components.blur.BarBlurBackdrop
import com.lonx.lyrico.ui.components.blur.BlurredBar
import com.lonx.lyrico.ui.components.effect.glass.GlassSource
import com.lonx.lyrico.ui.components.library.BlurNavigationBarItemV2
import com.lonx.lyrico.ui.components.library.BlurNavigationBarV2
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem

@Composable
internal fun LibraryBottomNavigationBar(
    tabs: List<LibraryTab>,
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: BarBlurBackdrop? = null,
) {
    val haptic = LocalHapticFeedback.current
    BlurredBar(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        atTop = false,
    ) {
        NavigationBar(
            modifier = modifier,
            color = Color.Transparent,
            showDivider = backdrop == null,
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
 * 手机端悬浮底栏，按当前栏位效果使用 Haze。
 */
@OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)
@Composable
internal fun LibraryBlurBottomBar(
    backdrop: GlassSource?,
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
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = stringResource(tab.titleRes),
            )
        }
    }
}
