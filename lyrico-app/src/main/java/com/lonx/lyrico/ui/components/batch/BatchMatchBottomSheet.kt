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
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.base.SheetStatisticsRow
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.base.AppLinearProgressIndicator
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.BatchMatchUiState
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BatchMatchBottomSheet(
    uiState: BatchMatchUiState,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    enableNestedScroll: Boolean = true,
    onAbort: () -> Unit
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    ActionBottomSheet(
        saltFooter = {
            com.lonx.lyrico.ui.components.base.SheetProgressAction(
                running = uiState.isRunning, onAbort = onAbort, onClose = onDismissRequest,
            )
        },
        show = uiState.isRunning || uiState.batchProgress != null,
        title = stringResource(R.string.batch_matching_title),
        enableNestedScroll = enableNestedScroll,
        groupContent = true,
        // 内容自带 verticalScroll，外壳不再提供滚动出口。
        scrollContent = false,
        // 进行中真正禁止关闭（返回键/外部点击/下拉）。
        allowDismiss = !uiState.isRunning,
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
                    uiState.batchProgress?.let { (current, total) ->
                        val progress =
                            if (total > 0) current.toFloat() / total.toFloat() else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (salt) com.moriafly.salt.ui.Text(
                                text = if (uiState.isRunning) {
                                    stringResource(R.string.batch_edit_processing)
                                } else {
                                    stringResource(
                                        R.string.batch_matching_total_time,
                                        uiState.batchTimeMillis / 1000.0
                                    )
                                },
                                style = SaltTheme.textStyles.sub,
                                color = SaltTheme.colors.subText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            ) else Text(
                                text = if (uiState.isRunning) {
                                    stringResource(R.string.batch_edit_processing)
                                } else {
                                    stringResource(
                                        R.string.batch_matching_total_time,
                                        uiState.batchTimeMillis / 1000.0
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

                        if (uiState.isRunning && uiState.fileProgressMap.isNotEmpty()) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                uiState.fileProgressMap.forEach { (fileName, fileProgress) ->
                                    val progressPercent = (fileProgress * 100).toInt()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (salt) com.moriafly.salt.ui.Text(
                                            text = fileName,
                                            style = SaltTheme.textStyles.sub,
                                            color = SaltTheme.colors.subText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        ) else Text(
                                            text = fileName,
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (salt) com.moriafly.salt.ui.Text(
                                            text = "$progressPercent%",
                                            style = SaltTheme.textStyles.sub,
                                            color = SaltTheme.colors.subText
                                        ) else Text(
                                            text = "$progressPercent%",
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceContainer
                                        )
                                    }
                                    AppLinearProgressIndicator(
                                        progress = fileProgress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                SheetStatisticsRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (salt) {
                        com.moriafly.salt.ui.Text(
                            text = stringResource(
                                R.string.batch_matching_success,
                                uiState.successCount
                            ),
                            style = SaltTheme.textStyles.main,
                            color = SaltTheme.colors.text
                        )
                        com.moriafly.salt.ui.Text(
                            text = stringResource(
                                R.string.batch_matching_skipped,
                                uiState.skippedCount
                            ),
                            style = SaltTheme.textStyles.main,
                            color = SaltTheme.colors.text
                        )
                        com.moriafly.salt.ui.Text(
                            text = stringResource(
                                R.string.batch_matching_failure,
                                uiState.failureCount
                            ),
                            style = SaltTheme.textStyles.main,
                            color = SaltTheme.colors.text
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.batch_matching_success,
                                uiState.successCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                        Text(
                            text = stringResource(
                                R.string.batch_matching_skipped,
                                uiState.skippedCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                        Text(
                            text = stringResource(
                                R.string.batch_matching_failure,
                                uiState.failureCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                    }
                }
            }
        },
        onDismissRequest = {
            onDismissRequest()
        },
        onDismissFinished = {
            onDismissFinished()
        },
        footer = {
            val actionLabel = if (uiState.isRunning) stringResource(R.string.action_abort)
            else stringResource(R.string.action_close)
            val onAction = {
                if (uiState.isRunning) {
                    onAbort()
                } else {
                    onDismissRequest()
                }
            }
            TextButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    )
}
