@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.salt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.dialog.DialogTitle

/** Salt 原生确认弹窗：SaltUI 的 YesNoDialog 不支持禁用确认，这里用 BasicDialog 补齐， 同时保留标题、说明、自定义内容与取消/确认语义。 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SaltConfirmDialog(
    show: Boolean,
    title: String?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    cancelText: String,
    confirmText: String,
    summary: String? = null,
    confirmEnabled: Boolean = true,
    dismissOnConfirm: Boolean = true,
    onDismissFinished: () -> Unit = {},
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
                title?.takeIf { it.isNotBlank() }?.let { DialogTitle(it) }
                summary
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        com.moriafly.salt.ui.Text(
                            text = it,
                            style = SaltTheme.textStyles.main,
                            color = SaltTheme.colors.text,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                content?.let {
                    Spacer(Modifier.height(12.dp))
                    it()
                }
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2,
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onDismissRequest,
                    text = cancelText,
                    appearance = ButtonAppearance.Subtle,
                )
                Button(
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
