package com.lonx.lyrico.ui.components.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine

@Composable
internal fun AppTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (LocalUiEngine.current) {
        UiEngine.SALT -> AppTabsSalt(tabs, selectedIndex, onSelected, modifier)
        UiEngine.MIUIX -> AppTabsMiuix(tabs, selectedIndex, onSelected, modifier)
    }
}
