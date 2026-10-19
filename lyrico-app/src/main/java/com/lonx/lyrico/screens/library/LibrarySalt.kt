@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens.library

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.LocalScaffoldIncludesBottomPadding
import com.lonx.lyrico.ui.components.LocalScaffoldIncludesStartPadding
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.library.LocalLibraryBottomContentPadding
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.components.scaffoldPageWindowInsets
import com.moriafly.salt.ui.*
import com.moriafly.salt.ui.gestures.cupertino.CupertinoOverscrollEffectFactory
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.popup.rememberPopupState
import com.moriafly.salt.ui.screen.BasicScreen

@Composable
internal fun SaltLibraryPage(
    title: String,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    menu: List<LibraryMenuAction>,
    selection: LibrarySelectionState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val popup = rememberPopupState()
    BoxWithConstraints {
        val layoutDirection = LocalLayoutDirection.current
        val pagePadding = scaffoldPageWindowInsets().asPaddingValues()
        val availableWidth =
            maxWidth -
                pagePadding.calculateStartPadding(layoutDirection) -
                pagePadding.calculateEndPadding(layoutDirection)
        val compact = availableWidth < 360.dp
        // Native title-bar slots retain the app's selection animation without replacing Salt's
        // backdrop.
        BasicScreen(
            contentPadding = pagePadding,
            actionButton = {
                LibraryTopBarTransition(selection, Alignment.CenterStart) { selected ->
                    val leadingMaxWidth =
                        (availableWidth -
                                if (selected == null) 112.dp else if (compact) 128.dp else 188.dp)
                            .coerceAtLeast(40.dp)
                    Row(
                        Modifier.widthIn(max = leadingMaxWidth),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selected == null) {
                            SaltIconButton(
                                LucideIcons.Settings,
                                stringResource(R.string.settings_title),
                                onSettings,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            selected?.title ?: title,
                            modifier = Modifier.weight(1f, fill = false),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            toolButtons = {
                LibraryTopBarTransition(selection, Alignment.CenterEnd) { selected ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (selected != null) {
                            SaltSelectionActions(selected, compact)
                        } else {
                            SaltIconButton(
                                LucideIcons.Search,
                                stringResource(R.string.cd_search),
                                onSearch,
                            )
                            Box {
                                SaltIconButton(
                                    LucideIcons.Sort,
                                    stringResource(R.string.cd_sort),
                                    popup::expend,
                                )
                                PopupMenu(
                                    expanded = popup.expend,
                                    onDismissRequest = popup::dismiss,
                                ) {
                                    menu.forEach { action ->
                                        PopupMenuItem(
                                            text = action.title,
                                            sub = action.summary,
                                            selected = action.selected,
                                            onClick = {
                                                popup.dismiss()
                                                action.onClick()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            content(padding)
        }
    }
}

@Composable
internal fun SaltSelectionActions(selection: LibrarySelectionState, compact: Boolean) {
    val label = stringResource(
        if (selection.allSelected) R.string.action_deselect_all else R.string.action_select_all
    )
    val closeLabel = stringResource(R.string.action_close)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (compact) {
            SaltIconButton(
                icon = LucideIcons.ListChecks,
                label = label,
                onClick = selection.onToggleAll,
                tint = SaltTheme.colors.highlight,
            )
            SaltIconButton(
                icon = LucideIcons.Close,
                label = closeLabel,
                onClick = selection.onClose,
                tint = SaltTheme.colors.highlight,
            )
        } else {
            SaltSelectionTextButton(label, selection.onToggleAll)
            SaltSelectionTextButton(closeLabel, selection.onClose)
        }
    }
}

@Composable
private fun SaltSelectionTextButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = SaltTheme.colors.highlight,
            style = SaltTheme.textStyles.main,
            maxLines = 1,
        )
    }
}

@Composable
internal fun LibraryHomeSalt(
    tabs: List<LibraryTab>,
    selectedTab: LibraryTab,
    rail: Boolean,
    onTabSelected: (LibraryTab) -> Unit,
    onBottomPaddingChange: (androidx.compose.ui.unit.Dp) -> Unit,
    content: @Composable () -> Unit,
) {
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    SideEffect { onBottomPaddingChange(if (rail) 0.dp else 56.dp + systemBottom) }
    CompositionLocalProvider(
        LocalIndication provides SaltTheme.configs.indication,
        LocalOverscrollFactory provides CupertinoOverscrollEffectFactory(),
    ) {
        if (rail) {
            Row(Modifier.fillMaxSize().background(SaltTheme.colors.background)) {
                Column(
                    Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                        )
                        .width(88.dp)
                        .fillMaxHeight()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
                        ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    tabs.forEach { tab ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable(role = Role.Tab, onClick = { onTabSelected(tab) })
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val color =
                                if (tab == selectedTab) SaltTheme.colors.highlight
                                else SaltTheme.colors.subText
                            Icon(rememberVectorPainter(tab.saltIcon), null, tint = color)
                            Text(
                                stringResource(tab.titleRes),
                                color = color,
                                style = SaltTheme.textStyles.sub,
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(
                        LocalScaffoldIncludesStartPadding provides false,
                        LocalScaffoldIncludesBottomPadding provides true,
                        LocalLibraryBottomContentPadding provides 0.dp,
                        content = content,
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize().background(SaltTheme.colors.background)) {
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(
                        LocalScaffoldIncludesBottomPadding provides false,
                        LocalLibraryBottomContentPadding provides 0.dp,
                        content = content,
                    )
                }
                Box(Modifier.background(SaltTheme.colors.subBackground).navigationBarsPadding()) {
                    BottomBar {
                        tabs.forEach { tab ->
                            Column(
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .selectable(
                                        selected = tab == selectedTab,
                                        role = Role.Tab,
                                        onClick = { onTabSelected(tab) },
                                    ),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val color =
                                    if (tab == selectedTab) SaltTheme.colors.highlight
                                    else SaltTheme.colors.subText
                                Icon(
                                    rememberVectorPainter(tab.saltIcon),
                                    null,
                                    modifier = Modifier.size(24.dp),
                                    tint = color,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    stringResource(tab.titleRes),
                                    color = color,
                                    fontSize = 10.sp,
                                    style = SaltTheme.textStyles.sub,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val LibraryTab.saltIcon: ImageVector
    get() =
        when (this) {
            LibraryTab.Songs -> LucideIcons.Music
            LibraryTab.Artists -> LucideIcons.Users
            LibraryTab.Albums -> LucideIcons.Disc
        }
