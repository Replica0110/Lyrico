package com.lonx.lyrico.ui.components.batch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricsOperation
import com.lonx.lyrico.data.model.lyrics.LyricsColumnMapping
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.lyrics.LyricsActionButton
import com.lonx.lyrico.ui.components.lyrics.LyricsColumnOrderSettings
import com.lonx.lyrico.ui.components.lyrics.LyricsConcurrency
import com.lonx.lyrico.ui.components.lyrics.LyricsOperationContent
import com.lonx.lyrico.viewmodel.BatchLyricsFormatUiState

@Composable
fun BatchLyricsFormatConfigBottomSheet(
    state: BatchLyricsFormatUiState,
    onDismiss: () -> Unit,
    onFormat: (LyricFormat) -> Unit,
    onConcurrency: (Int) -> Unit,
    onTwoColumnMapping: (LyricsColumnMapping) -> Unit,
    onThreeColumnMapping: (LyricsColumnMapping) -> Unit,
    onConfirm: () -> Unit
) {
    ActionBottomSheet(
        saltScrollContent = false,
        show = state.showConfigDialog,
        title = stringResource(state.operation.titleRes),
        onDismissRequest = onDismiss,
        startAction = { LyricsActionButton(stringResource(R.string.cancel), onClick = onDismiss) },
        endAction = { LyricsActionButton(stringResource(R.string.lyrics_process_action), primary = true, onClick = onConfirm) },
        content = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .then(
                        if (com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
                            com.lonx.lyrico.data.model.UiEngine.SALT) Modifier
                        else Modifier.padding(bottom = 24.dp)
                    )
            ) {
                if (state.operation == LyricsOperation.SORT) {
                    LyricsColumnOrderSettings(state.twoColumnMapping, state.threeColumnMapping,
                        onTwoColumnMapping, onThreeColumnMapping)
                } else {
                    LyricsOperationContent(state.operation, state.targetFormat ?: LyricFormat.PLAIN_LRC, onFormat)
                }
                LyricsConcurrency(state.concurrency, onConcurrency)
            }
        }
    )
}
