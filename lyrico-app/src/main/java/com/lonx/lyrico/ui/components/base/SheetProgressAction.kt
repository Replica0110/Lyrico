package com.lonx.lyrico.ui.components.base

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.ButtonIntent

/** Fixed footer shared by progress sheets; the body remains independently scrollable. */
@Composable
internal fun SheetProgressAction(running: Boolean, onAbort: () -> Unit, onClose: () -> Unit,
    label: String? = null) {
    Button(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        text = label ?: stringResource(if (running) R.string.action_abort else R.string.action_close),
        onClick = if (running) onAbort else onClose,
        appearance = if (running) ButtonAppearance.Subtle else ButtonAppearance.Filled,
        intent = if (running) ButtonIntent.Destructive else ButtonIntent.Normal,
    )
}
