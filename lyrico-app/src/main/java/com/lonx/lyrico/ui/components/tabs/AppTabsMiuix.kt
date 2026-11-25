package com.lonx.lyrico.ui.components.tabs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AppTabsMiuix(
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
