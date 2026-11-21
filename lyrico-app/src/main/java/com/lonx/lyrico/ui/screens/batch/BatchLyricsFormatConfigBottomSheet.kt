package com.lonx.lyrico.ui.screens.batch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsOperation
import com.lonx.lyrico.data.lyrics.LyricsColumnMapping
import com.lonx.lyrico.ui.components.sheet.ActionBottomSheet
import com.lonx.lyrico.ui.components.sheet.SheetConfirmActions
import com.lonx.lyrico.ui.components.lyrics.LyricsColumnOrderSettings
import com.lonx.lyrico.ui.components.lyrics.LyricsConcurrency
import com.lonx.lyrico.ui.components.lyrics.LyricsOperationContent

@Composable
fun BatchLyricsFormatConfigBottomSheet(
    state: BatchLyricsFormatUiState,
    onDismiss: () -> Unit,
    onFormat: (LyricsFormat) -> Unit,
    onConcurrency: (Int) -> Unit,
    onTwoColumnMapping: (LyricsColumnMapping) -> Unit,
    onThreeColumnMapping: (LyricsColumnMapping) -> Unit,
    onConfirm: () -> Unit
) {
    ActionBottomSheet(
        scrollContent = false,
        footer = {
            SheetConfirmActions(
                cancelText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.lyrics_process_action),
                onCancel = onDismiss,
                onConfirm = onConfirm,
            )
        },
        show = state.showConfigDialog,
        title = stringResource(state.operation.titleRes),
        onDismissRequest = onDismiss,
        content = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                if (state.operation == LyricsOperation.SORT) {
                    LyricsColumnOrderSettings(state.twoColumnMapping, state.threeColumnMapping,
                        onTwoColumnMapping, onThreeColumnMapping)
                } else {
                    LyricsOperationContent(state.operation, state.targetFormat ?: LyricsFormat.PLAIN_LRC, onFormat)
                }
                LyricsConcurrency(state.concurrency, onConcurrency)
            }
        }
    )
}
