package com.lonx.lyrico.ui.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine

/** Progress summaries can wrap on narrow windows or with enlarged text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SheetStatisticsRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) { content() }
    }
}
