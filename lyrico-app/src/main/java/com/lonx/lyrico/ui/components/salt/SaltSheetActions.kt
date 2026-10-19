package com.lonx.lyrico.ui.components.salt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance

/** Fixed sheet actions stay outside content cards and stack when text is enlarged. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SaltSheetActions(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2,
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = onCancel,
            text = cancelText,
            appearance = ButtonAppearance.Subtle,
        )
        Button(
            modifier = Modifier.weight(1f),
            onClick = onConfirm,
            text = confirmText,
            enabled = confirmEnabled,
            appearance = ButtonAppearance.Filled,
        )
    }
}
