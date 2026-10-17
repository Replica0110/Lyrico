package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.screens.library.LibrarySelectionState
import com.lonx.lyrico.ui.theme.LocalUiEngine
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** Detail pages share layout and actions, while each engine owns its controls and typography. */
@Composable
internal fun LibraryDetailPage(
    title: String,
    onBack: () -> Unit,
    selection: LibrarySelectionState? = null,
    actions: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    miuixTopBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val engine = LocalUiEngine.current

    when (engine) {
        UiEngine.SALT ->
            LibraryDetailPageSalt(title, onBack, selection, actions, snackbarHost, content)
        UiEngine.MIUIX -> LibraryDetailPageMiuix(miuixTopBar, snackbarHost, content)
    }
}

@Composable
internal fun Modifier.libraryDetailScroll(behavior: ScrollBehavior): Modifier =
    if (LocalUiEngine.current == UiEngine.SALT) this else libraryDetailScrollMiuix(behavior)

@Composable
internal fun LibraryDetailIconButton(icon: ImageVector, description: String?, onClick: () -> Unit) {
    when (LocalUiEngine.current) {
        UiEngine.SALT -> LibraryDetailIconButtonSalt(icon, description, onClick)
        UiEngine.MIUIX -> LibraryDetailIconButtonMiuix(icon, description, onClick)
    }
}

@Composable
internal fun LibraryDetailTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (LocalUiEngine.current) {
        UiEngine.SALT -> LibraryDetailTabsSalt(tabs, selectedIndex, onSelected, modifier)
        UiEngine.MIUIX -> LibraryDetailTabsMiuix(tabs, selectedIndex, onSelected, modifier)
    }
}

internal enum class LibraryDetailTextTone {
    Title,
    Body,
    Secondary,
}

@Composable
internal fun LibraryDetailText(
    text: String,
    modifier: Modifier = Modifier,
    tone: LibraryDetailTextTone = LibraryDetailTextTone.Body,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    when (LocalUiEngine.current) {
        UiEngine.SALT -> LibraryDetailTextSalt(text, modifier, tone, maxLines, overflow)
        UiEngine.MIUIX -> LibraryDetailTextMiuix(text, modifier, tone, maxLines, overflow)
    }
}
