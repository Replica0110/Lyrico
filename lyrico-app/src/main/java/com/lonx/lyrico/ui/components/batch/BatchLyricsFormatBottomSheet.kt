package com.lonx.lyrico.ui.components.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.viewmodel.BatchLyricsFormatUiState
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BatchLyricsFormatBottomSheet(
    batchLyricsFormatUiState: BatchLyricsFormatUiState,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onAbort: () -> Unit
) {
    ActionBottomSheet(
        show = batchLyricsFormatUiState.showProgressDialog,
        onDismissRequest = {
            onDismissRequest()
        },
        onDismissFinished = {
            onDismissFinished()
        },
        allowDismiss = !batchLyricsFormatUiState.isRunning,
        title = stringResource(R.string.action_batch_convert_lyrics_format),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.current_target_format,
                            stringResource(batchLyricsFormatUiState.targetFormat.labelRes)
                        ),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                    )

                    batchLyricsFormatUiState.progress?.let { (current, total) ->
                        val progress =
                            if (total > 0) current.toFloat() / total.toFloat() else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (batchLyricsFormatUiState.isRunning) {
                                    stringResource(R.string.batch_edit_processing)
                                } else {
                                    stringResource(
                                        R.string.batch_replay_gain_total_time,
                                        batchLyricsFormatUiState.totalTimeMillis / 1000.0
                                    )
                                },
                                style = MiuixTheme.textStyles.subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "$current / $total",
                                style = MiuixTheme.textStyles.main,
                                textAlign = TextAlign.End
                            )
                        }

                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(
                            R.string.batch_replay_gain_success,
                            batchLyricsFormatUiState.successCount
                        ),
                        style = MiuixTheme.textStyles.main
                    )
                    Text(
                        text = stringResource(
                            R.string.batch_replay_gain_skipped,
                            batchLyricsFormatUiState.skippedCount
                        ),
                        style = MiuixTheme.textStyles.main
                    )
                    Text(
                        text = stringResource(
                            R.string.batch_replay_gain_failure,
                            batchLyricsFormatUiState.failureCount
                        ),
                        style = MiuixTheme.textStyles.main
                    )
                }

            }
        },
        actions = {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = if (batchLyricsFormatUiState.isRunning) {
                    stringResource(R.string.action_abort)
                } else {
                    stringResource(R.string.action_close)
                },
                onClick = {
                    if (batchLyricsFormatUiState.isRunning) {
                        onAbort()
                    } else {
                        onDismissRequest()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    )
}