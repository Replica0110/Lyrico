package com.lonx.lyrico.ui.components.library

import androidx.compose.runtime.staticCompositionLocalOf
import com.lonx.lyrico.data.model.UiEngine

/** Scoped to the library pilot; detail and edit pages retain their current presentation. */
internal val LocalLibraryUiEngine = staticCompositionLocalOf { UiEngine.MIUIX }
