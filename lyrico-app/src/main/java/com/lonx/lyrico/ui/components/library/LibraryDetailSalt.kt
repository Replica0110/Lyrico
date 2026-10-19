@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.screens.library.LibrarySelectionState
import com.lonx.lyrico.screens.library.LibraryTopBarTransition
import com.lonx.lyrico.screens.library.SaltSelectionActions
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.components.scaffoldPageWindowInsets
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.gestures.cupertino.CupertinoOverscrollEffectFactory
import com.moriafly.salt.ui.screen.BasicScreen

@Composable
internal fun LibraryDetailPageSalt(
    title: String,
    onBack: () -> Unit,
    selection: LibrarySelectionState?,
    actions: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {

    CompositionLocalProvider(
        LocalIndication provides SaltTheme.configs.indication,
        LocalOverscrollFactory provides CupertinoOverscrollEffectFactory(),
    ) {
        BoxWithConstraints {
            val padding = scaffoldPageWindowInsets().asPaddingValues()
            val direction = LocalLayoutDirection.current
            val width =
                maxWidth -
                    padding.calculateStartPadding(direction) -
                    padding.calculateEndPadding(direction)
            val compact = width < 360.dp
            BasicScreen(
                modifier = Modifier.background(SaltTheme.colors.background),
                contentPadding = padding,
                actionButton = {
                    LibraryTopBarTransition(selection, Alignment.CenterStart) { selected ->
                        // Include the native title bar's side and slot spacing as well as the 40dp
                        // action.
                        val reserved =
                            if (selected == null) 72.dp else if (compact) 128.dp else 188.dp
                        Row(
                            Modifier.widthIn(max = (width - reserved).coerceAtLeast(40.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected == null) {
                                SaltIconButton(
                                    LucideIcons.ArrowLeft,
                                    stringResource(R.string.action_back),
                                    onBack,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            com.moriafly.salt.ui.Text(
                                selected?.title ?: title,
                                modifier = Modifier.weight(1f, fill = false),
                                style = SaltTheme.textStyles.main,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                toolButtons = {
                    LibraryTopBarTransition(selection, Alignment.CenterEnd) { selected ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selected == null) actions()
                            else {
                                SaltSelectionActions(selected, compact)
                            }
                        }
                    }
                },
            ) {
                // Salt 没有 Snackbar，宿主由调用方提供，这里只负责底部定位。
                Box(Modifier.fillMaxSize()) {
                    content(it)
                    Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                        snackbarHost()
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryDetailIconButtonSalt(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
) {
    SaltIconButton(icon, description.orEmpty(), onClick)
}

@Composable
internal fun LibraryDetailTabsSalt(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outer = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)

    com.moriafly.salt.ui.Card(outer) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(4.dp)) {
            tabs.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Box(
                    Modifier.weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) SaltTheme.colors.background
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .selectable(selected, role = Role.Tab, onClick = { onSelected(index) })
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    com.moriafly.salt.ui.Text(
                        title,
                        style = SaltTheme.textStyles.main,
                        color =
                            if (selected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun LibraryDetailTextSalt(
    text: String,
    modifier: Modifier = Modifier,
    tone: LibraryDetailTextTone = LibraryDetailTextTone.Body,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {

    com.moriafly.salt.ui.Text(
        text,
        modifier = modifier,
        style =
            when (tone) {
                LibraryDetailTextTone.Title -> SaltTheme.textStyles.largeTitle
                LibraryDetailTextTone.Body -> SaltTheme.textStyles.main
                LibraryDetailTextTone.Secondary -> SaltTheme.textStyles.sub
            },
        color =
            if (tone == LibraryDetailTextTone.Secondary) SaltTheme.colors.subText
            else SaltTheme.colors.text,
        fontWeight = if (tone == LibraryDetailTextTone.Title) FontWeight.Bold else null,
        maxLines = maxLines,
        overflow = overflow,
    )
}
