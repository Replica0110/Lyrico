package com.lonx.lyrico.ui.screens.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.lyrics.LyricsOffsetField
import com.lonx.lyrico.ui.components.editor.EditorSheet
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LyricsOffsetSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    lyricsText: String,
    currentOffset: Long,
    onOffsetChange: (Long) -> Unit,
) {
    val salt = LocalUiEngine.current == UiEngine.SALT

    EditorSheet(
        show = show,
        enableNestedScroll = false,
        title = stringResource(R.string.offset_adjust_hint),
        onDismissRequest = onDismissRequest
    ) {
        if (salt) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                RoundedColumn {
                    SelectionContainer {
                        com.moriafly.salt.ui.Text(
                            text = lyricsText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            style = SaltTheme.textStyles.sub
                        )
                    }
                }
                RoundedColumn {
                    LyricsOffsetField(
                        offset = currentOffset,
                        onOffsetChange = onOffsetChange,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Card(
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .padding(horizontal = 8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = lyricsText,
                            style = MiuixTheme.textStyles.footnote1
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Card(
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    LyricsOffsetField(
                        offset = currentOffset,
                        onOffsetChange = onOffsetChange,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}
