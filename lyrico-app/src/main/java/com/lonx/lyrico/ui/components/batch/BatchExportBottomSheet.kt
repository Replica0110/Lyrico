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
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.base.SheetStatisticsRow
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.base.AppLinearProgressIndicator
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.BatchExportUiState
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BatchExportBottomSheet(
    batchExportUiState: BatchExportUiState,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onAbort: () -> Unit
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    val title = when (batchExportUiState.taskType) {
        BatchTaskType.EXPORT_COVER -> stringResource(R.string.action_batch_export_cover)
        else -> stringResource(R.string.action_batch_export_lyrics)
    }

    ActionBottomSheet(
        saltFooter = {
            com.lonx.lyrico.ui.components.base.SheetProgressAction(
                running = batchExportUiState.isRunning, onAbort = onAbort, onClose = onDismissRequest,
            )
        },
        show = batchExportUiState.showProgressDialog,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        allowDismiss = !batchExportUiState.isRunning,
        title = title,
        groupContent = true,
        // 内容自带 verticalScroll，外壳不再提供滚动出口。
        saltScrollContent = false,
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
                    batchExportUiState.progress?.let { (current, total) ->
                        val progress =
                            if (total > 0) current.toFloat() / total.toFloat() else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (salt) com.moriafly.salt.ui.Text(
                                text = if (batchExportUiState.isRunning) {
                                    stringResource(R.string.batch_export_running)
                                } else {
                                    stringResource(
                                        R.string.batch_replay_gain_total_time,
                                        batchExportUiState.totalTimeMillis / 1000.0
                                    )
                                },
                                style = SaltTheme.textStyles.sub,
                                color = SaltTheme.colors.subText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            ) else Text(
                                text = if (batchExportUiState.isRunning) {
                                    stringResource(R.string.batch_export_running)
                                } else {
                                    stringResource(
                                        R.string.batch_replay_gain_total_time,
                                        batchExportUiState.totalTimeMillis / 1000.0
                                    )
                                },
                                style = MiuixTheme.textStyles.subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            if (salt) com.moriafly.salt.ui.Text(
                                text = "$current / $total",
                                style = SaltTheme.textStyles.main,
                                color = SaltTheme.colors.text,
                                textAlign = TextAlign.End
                            ) else Text(
                                text = "$current / $total",
                                style = MiuixTheme.textStyles.main,
                                textAlign = TextAlign.End
                            )
                        }

                        AppLinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                SheetStatisticsRow(
                    modifier = Modifier.fillMaxWidth()
                        .then(if (salt) Modifier else Modifier.padding(bottom = 12.dp))
                ) {
                    if (salt) {
                        com.moriafly.salt.ui.Text(
                            text = stringResource(
                                R.string.batch_replay_gain_success,
                                batchExportUiState.successCount
                            ),
                            style = SaltTheme.textStyles.main,
                            color = SaltTheme.colors.text
                        )
                        com.moriafly.salt.ui.Text(
                            text = stringResource(
                                R.string.batch_replay_gain_skipped,
                                batchExportUiState.skippedCount
                            ),
                            style = SaltTheme.textStyles.main,
                            color = SaltTheme.colors.text
                        )
                        com.moriafly.salt.ui.Text(
                            text = stringResource(
                                R.string.batch_replay_gain_failure,
                                batchExportUiState.failureCount
                            ),
                            style = SaltTheme.textStyles.main,
                            color = SaltTheme.colors.text
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.batch_replay_gain_success,
                                batchExportUiState.successCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                        Text(
                            text = stringResource(
                                R.string.batch_replay_gain_skipped,
                                batchExportUiState.skippedCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                        Text(
                            text = stringResource(
                                R.string.batch_replay_gain_failure,
                                batchExportUiState.failureCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                    }
                }
            }
            if (!salt) Spacer(modifier = Modifier.padding(vertical = 12.dp))
        },
        endAction = {
            val actionLabel = if (batchExportUiState.isRunning) stringResource(R.string.action_abort)
            else stringResource(R.string.action_close)
            val onAction = {
                if (batchExportUiState.isRunning) {
                    onAbort()
                } else {
                    onDismissRequest()
                }
            }
            TextButton(
                colors = ButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MiuixTheme.colorScheme.primary,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = MiuixTheme.colorScheme.disabledPrimary
                ),
                onClick = onAction
            ) {
                Text(
                    text = actionLabel,
                    color = if (batchExportUiState.isRunning) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.primary
                    }
                )
            }
        }
    )
}
