@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.batch

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
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.base.AppLinearProgressIndicator
import com.lonx.lyrico.ui.components.base.SheetProgressAction
import com.lonx.lyrico.ui.components.base.SheetStatisticsRow
import com.lonx.lyrico.ui.components.sheet.miuix.MiuixBottomSheet
import com.lonx.lyrico.ui.components.sheet.salt.SaltBottomSheet
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text as SaltText
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 保存进度：处理中的文件名、进度条与成功/跳过/失败计数。
 * 关闭动作与中止动作由调用方决定，这里只负责展示与回调。
 */
@Composable
internal fun BatchEditSaveProgressSheet(
    state: BatchEditUiState,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onAbort: () -> Unit,
    onClose: () -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBottomSheet(
            show = state.saveProgressBottomSheet,
            onDismissRequest = onDismiss,
            onDismissFinished = onDismissFinished,
            allowDismiss = !state.isSaving,
            title = stringResource(R.string.batch_edit_title),
            groupContent = true,
            scrollContent = true,
            footer = {
                SheetProgressAction(
                    state.isSaving, onAbort, onClose,
                    label = stringResource(if (state.isSaving) R.string.action_abort else R.string.confirm)
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SaltText(
                    if (state.isSaving) state.currentFile.ifEmpty { stringResource(R.string.batch_edit_processing) }
                    else stringResource(R.string.batch_matching_total_time, state.saveTimeMillis / 1000.0),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                SaltText(
                    "${state.saveProgress} / ${state.saveTotal}",
                    style = SaltTheme.textStyles.sub,
                    color = SaltTheme.colors.subText
                )
                AppLinearProgressIndicator(
                    if (state.saveTotal > 0) state.saveProgress.toFloat() / state.saveTotal else 0f,
                    Modifier.fillMaxWidth()
                )
                SheetStatisticsRow {
                    SaltText(stringResource(R.string.batch_matching_success, state.successCount))
                    SaltText(stringResource(R.string.batch_replay_gain_skipped, state.skippedCount))
                    SaltText(stringResource(R.string.batch_matching_failure, state.failureCount))
                }
            }
        }
        return
    }

    MiuixBottomSheet(
        show = state.saveProgressBottomSheet,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        allowDismiss = !state.isSaving,
        title = stringResource(R.string.batch_edit_title),
        footer = {
            TextButton(
                text = if (state.isSaving) stringResource(R.string.action_abort) else stringResource(
                    R.string.confirm
                ),
                onClick = { if (state.isSaving) onAbort() else onClose() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.isSaving) {
                            state.currentFile.ifEmpty { stringResource(R.string.batch_edit_processing) }
                        } else {
                            stringResource(R.string.batch_matching_total_time, state.saveTimeMillis / 1000.0)
                        },
                        style = MiuixTheme.textStyles.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${state.saveProgress} / ${state.saveTotal}",
                        style = MiuixTheme.textStyles.main,
                        textAlign = TextAlign.End
                    )
                }

                LinearProgressIndicator(
                    progress = if (state.saveTotal > 0)
                        state.saveProgress.toFloat() / state.saveTotal
                    else 0f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.batch_matching_success, state.successCount),
                    style = MiuixTheme.textStyles.main
                )
                Text(
                    text = stringResource(R.string.batch_replay_gain_skipped, state.skippedCount),
                    style = MiuixTheme.textStyles.main
                )
                Text(
                    text = stringResource(R.string.batch_matching_failure, state.failureCount),
                    style = MiuixTheme.textStyles.main
                )
            }
        }
    }
}
