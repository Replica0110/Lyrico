package com.lonx.lyrico.ui.components.base


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun YesNoDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    content: @Composable (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onConfirm: () -> Unit,
    cancelText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    confirmEnabled: Boolean = true
) {
    if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT) {
        var wasVisible by remember { mutableStateOf(show) }
        val finished by rememberUpdatedState(onDismissFinished)
        LaunchedEffect(show) {
            if (!show && wasVisible) finished()
            wasVisible = show
        }
        SaltConfirmDialog(
            show = show, title = title, summary = summary, content = content,
            onDismissRequest = onDismissRequest, onConfirm = onConfirm,
            cancelText = cancelText, confirmText = confirmText, confirmEnabled = confirmEnabled,
        )
        return
    }
    WindowDialog(
        show = show,
        summary = summary,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        title = title
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {

            content?.let {
                content()
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row {
                TextButton(
                    text = cancelText,
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(20.dp))

                TextButton(
                    text = confirmText,
                    enabled = confirmEnabled,
                    onClick = {
                        onConfirm()
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}
