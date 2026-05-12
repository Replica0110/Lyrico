package com.lonx.lyrico.ui.dialog

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.base.YesNoDialog
import dev.jeziellago.compose.markdowntext.MarkdownText
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdateDialog(
    show: Boolean,
    versionName: String,
    releaseNote: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val scrollState = rememberScrollState()

    YesNoDialog(
        show = show,
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
        title = stringResource(
            id = R.string.dialog_title_update_available,
            versionName
        ),
        content = {
            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer
                )
            ) {
                MarkdownText(
                    modifier = Modifier
                        .padding(12.dp)
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState),
                    markdown = releaseNote
                )
            }
        }
    )
}