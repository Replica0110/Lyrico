@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.*

@Composable
internal fun SettingsCacheDialog(
    show: Boolean,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        if (show)
            com.moriafly.salt.ui.dialog.YesNoDialog(
                title = stringResource(R.string.clear_cache),
                content = "",
                drawContent = {
                    Text(
                        text,
                        modifier =
                            Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                },
                onDismissRequest = onDismiss,
                onConfirm = onConfirm,
                cancelText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.confirm),
            )
    } else
        com.lonx.lyrico.ui.components.base.YesNoDialog(
            show = show,
            title = stringResource(R.string.clear_cache),
            content = {
                top.yukonga.miuix.kmp.basic.Text(
                    text,
                    modifier =
                        Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                )
            },
            onDismissRequest = onDismiss,
            onConfirm = onConfirm,
        )
}
