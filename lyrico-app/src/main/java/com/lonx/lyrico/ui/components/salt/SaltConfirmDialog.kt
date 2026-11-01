@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.salt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.SaltTheme

/** Shared Salt decision/input dialog with consistent title, body and fixed actions. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SaltConfirmDialog(
    show: Boolean = true,
    title: String?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    cancelText: String,
    confirmText: String,
    summary: String? = null,
    confirmEnabled: Boolean = true,
    dismissOnConfirm: Boolean = true,
    onDismissFinished: () -> Unit = {},
    leadingAction: (@Composable FlowRowScope.() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    var wasVisible by remember { mutableStateOf(show) }
    val finished by rememberUpdatedState(onDismissFinished)
    LaunchedEffect(show) {
        if (!show && wasVisible) finished()
        wasVisible = show
    }
    if (!show) return
    SaltBasicDialog(onDismissRequest = onDismissRequest) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                title?.takeIf { it.isNotBlank() }?.let { SaltDialogTitle(it) }
                summary
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        com.moriafly.salt.ui.Text(
                            text = it,
                            style = SaltTheme.textStyles.main.copy(fontWeight = FontWeight.Normal),
                            color = SaltTheme.colors.text,
                        )
                    }
                content?.let {
                    if (!summary.isNullOrBlank()) Spacer(Modifier.height(12.dp))
                    it()
                }
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = when {
                    LocalDensity.current.fontScale >= 1.3f -> 1
                    leadingAction != null -> 3
                    else -> 2
                },
            ) {
                leadingAction?.invoke(this)
                SaltDialogButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismissRequest,
                    text = cancelText,
                    appearance = ButtonAppearance.Subtle,
                )
                SaltDialogButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onConfirm()
                        if (dismissOnConfirm) onDismissRequest()
                    },
                    text = confirmText,
                    enabled = confirmEnabled,
                )
            }
        }
    }
}
