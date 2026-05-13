package com.lonx.lyrico.ui.components.base


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun YesNoBottomSheet(
    show: Boolean,
    title: String? = null,
    enableNestedScroll: Boolean = true,
    content: @Composable (() -> Unit),
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onConfirm: () -> Unit
) {
    WindowBottomSheet(
        show = show,
        enableNestedScroll = enableNestedScroll,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        title = title
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            content.invoke()
            Row {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(20.dp))

                TextButton(
                    text = stringResource(R.string.dialog_action_go_update),
                    onClick = {
                        onConfirm()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}