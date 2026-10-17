package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
internal fun LibraryDetailPageMiuix(
    topBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    top.yukonga.miuix.kmp.basic.Scaffold(
        topBar = topBar,
        snackbarHost = snackbarHost,
        content = content,
    )
}

@Composable
internal fun Modifier.libraryDetailScrollMiuix(behavior: ScrollBehavior): Modifier =
    scrollEndHaptic().overScrollVertical().nestedScroll(behavior.nestedScrollConnection)

@Composable
internal fun LibraryDetailIconButtonMiuix(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
) {
    top.yukonga.miuix.kmp.basic.IconButton(onClick = onClick) {
        top.yukonga.miuix.kmp.basic.Icon(icon, description)
    }
}

@Composable
internal fun LibraryDetailTabsMiuix(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outer = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)
    top.yukonga.miuix.kmp.basic.Card(outer) {
        top.yukonga.miuix.kmp.basic.TabRowWithContour(
            tabs = tabs,
            selectedTabIndex = selectedIndex,
            onTabSelected = onSelected,
        )
    }
}

@Composable
internal fun LibraryDetailTextMiuix(
    text: String,
    modifier: Modifier = Modifier,
    tone: LibraryDetailTextTone = LibraryDetailTextTone.Body,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    top.yukonga.miuix.kmp.basic.Text(
        text,
        modifier = modifier,
        style =
            when (tone) {
                LibraryDetailTextTone.Title -> MiuixTheme.textStyles.title3
                LibraryDetailTextTone.Body -> MiuixTheme.textStyles.body1
                LibraryDetailTextTone.Secondary -> MiuixTheme.textStyles.body2
            },
        color =
            if (tone == LibraryDetailTextTone.Secondary)
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            else MiuixTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = overflow,
    )
}
