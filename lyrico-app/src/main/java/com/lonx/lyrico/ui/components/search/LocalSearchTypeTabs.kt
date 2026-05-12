package com.lonx.lyrico.ui.components.search

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.model.LocalSearchType
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TabRowWithContour

@Composable
fun LocalSearchTypeTabs(
    selectedType: LocalSearchType,
    onTypeSelected: (LocalSearchType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        TabRowWithContour(
            tabs = LocalSearchType.entries.map { stringResource(it.labelRes) },
            selectedTabIndex = LocalSearchType.entries.indexOf(selectedType),
            onTabSelected = {
                onTypeSelected(LocalSearchType.entries[it])
            }
        )
    }
}
