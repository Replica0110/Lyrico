package com.lonx.lyrico.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.outerPadding
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun UpdateDialog(
    versionName: String,
    releaseNote: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val scrollState = rememberScrollState()
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        // 标题
        Text(
            text = stringResource(id = R.string.dialog_title_update_available, versionName),
            modifier = Modifier.outerPadding(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SaltTheme.dimens.padding)
        ) {
            RoundedColumn(
                paddingValues = PaddingValues(0.dp)
            ) {
                MarkdownText(
                    modifier = Modifier
                        .padding(SaltTheme.dimens.subPadding)
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState),
                    markdown = releaseNote
                )
            }
        }

        Row(
            modifier = Modifier.outerPadding()
        ) {
            Button(
                onClick = onDismissRequest,
                text = stringResource(id = R.string.cancel),
                modifier = Modifier.weight(1f),
                type = com.moriafly.salt.ui.ButtonType.Sub
            )
            Spacer(modifier = Modifier.width(SaltTheme.dimens.padding))
            Button(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
                text = stringResource(id = R.string.dialog_action_go_update),
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }
    }
}
