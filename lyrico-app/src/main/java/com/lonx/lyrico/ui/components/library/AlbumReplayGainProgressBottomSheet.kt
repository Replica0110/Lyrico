package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.base.AppLinearProgressIndicator
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.AlbumActionsUiState
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AlbumReplayGainProgressBottomSheet(
    uiState: AlbumActionsUiState,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onAbort: () -> Unit
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    ActionBottomSheet(
        saltFooter = {
            com.lonx.lyrico.ui.components.base.SheetProgressAction(
                running = uiState.isCalculatingAlbumReplayGain, onAbort = onAbort, onClose = onDismissRequest,
            )
        },
        show = uiState.showAlbumReplayGainProgressDialog,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        allowDismiss = !uiState.isCalculatingAlbumReplayGain,
        title = uiState.albumName,
        groupContent = true,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val progress = uiState.albumReplayGainProgress ?: 0f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when {
                        uiState.isCalculatingAlbumReplayGain -> stringResource(R.string.album_replay_gain_calculating)
                        else -> stringResource(
                            R.string.batch_replay_gain_total_time,
                            uiState.albumReplayGainTotalTimeMillis / 1000.0
                        )
                    }
                    if (salt) com.moriafly.salt.ui.Text(
                        text = statusText,
                        style = SaltTheme.textStyles.sub,
                        color = SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    ) else Text(
                        text = statusText,
                        style = MiuixTheme.textStyles.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (salt) com.moriafly.salt.ui.Text(
                        text = "${(progress * 100).toInt()}%",
                        style = SaltTheme.textStyles.main,
                        color = SaltTheme.colors.text,
                        textAlign = TextAlign.End
                    ) else Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MiuixTheme.textStyles.main,
                        textAlign = TextAlign.End
                    )

                }

                AppLinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Salt 的共享弹层壳已提供卡片内边距，不再叠加。
                            .then(if (salt) Modifier else Modifier.padding(bottom = 12.dp)),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (salt) com.moriafly.salt.ui.Text(
                                text = stringResource(
                                    R.string.batch_replay_gain_success,
                                    uiState.albumReplayGainWrittenCount
                                ),
                                style = SaltTheme.textStyles.main,
                                color = SaltTheme.colors.text
                            ) else Text(
                                text = stringResource(
                                    R.string.batch_replay_gain_success,
                                    uiState.albumReplayGainWrittenCount
                                ),
                                style = MiuixTheme.textStyles.main
                            )
                            if (salt) com.moriafly.salt.ui.Text(
                                text = "${uiState.albumReplayGainWrittenCount} / ${uiState.albumReplayGainSongCount}",
                                style = SaltTheme.textStyles.main,
                                color = SaltTheme.colors.text,
                                textAlign = TextAlign.End
                            ) else Text(
                                text = "${uiState.albumReplayGainWrittenCount} / ${uiState.albumReplayGainSongCount}",
                                style = MiuixTheme.textStyles.main,
                                textAlign = TextAlign.End
                            )
                        }
                    }



                if (!salt) Spacer(modifier = Modifier.height(12.dp))
            }
        },
        endAction = {
            val actionLabel = if (uiState.isCalculatingAlbumReplayGain) {
                stringResource(R.string.action_abort)
            } else {
                stringResource(R.string.action_close)
            }
            val onAction = {
                if (uiState.isCalculatingAlbumReplayGain) onAbort() else onDismissRequest()
            }
            TextButton(
                colors = ButtonColors(
                    containerColor = MiuixTheme.colorScheme.surface,
                    contentColor = MiuixTheme.colorScheme.primary,
                    disabledContainerColor = MiuixTheme.colorScheme.surface,
                    disabledContentColor = MiuixTheme.colorScheme.disabledPrimary
                ),
                onClick = onAction
            ) {
                Text(
                    text = actionLabel,
                    color = when {
                        uiState.isCalculatingAlbumReplayGain -> MiuixTheme.colorScheme.error
                        else -> MiuixTheme.colorScheme.primary
                    }
                )
            }
        }
    )
}