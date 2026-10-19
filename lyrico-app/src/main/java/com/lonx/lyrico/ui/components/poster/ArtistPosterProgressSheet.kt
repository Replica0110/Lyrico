package com.lonx.lyrico.ui.components.poster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import com.lonx.lyrico.viewmodel.ArtistPosterEmbedUiState
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置艺术家海报的进度与结果面板。
 *
 * 一炉操作可能写几十首歌，也可能只是在写一个外置文件，所以这个面板同时承担三件事：
 * 进行中给进度、写盘需要授权时给入口、结束后给结果。
 *
 * 进行中不允许滑动关闭（[ActionBottomSheet] 的 `allowDismiss`），避免用户以为取消了、
 * 其实后台还在改文件；要停就点「中止」。
 */
@Composable
fun ArtistPosterProgressSheet(
    state: ArtistPosterEmbedUiState,
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onCancel: () -> Unit,
    onGrantPermission: () -> Unit
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    ActionBottomSheet(
        saltFooter = {
            com.lonx.lyrico.ui.components.base.SheetProgressAction(
                running = state.isRunning, onAbort = onCancel,
                onClose = {
                    if (state.permissionIntentSender != null) onGrantPermission() else onDismissRequest()
                },
                label = stringResource(when {
                    state.isRunning -> R.string.action_abort
                    state.permissionIntentSender != null -> R.string.action_grant_permission
                    state.message != null -> R.string.action_done
                    else -> R.string.action_close
                }),
            )
        },
        show = show,
        title = title,
        enableNestedScroll = false,
        allowDismiss = !state.isRunning,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        // Salt 侧裸内容需要分组获得标准内边距（内容不长，由外壳负责滚动）。
        groupContent = true,
        footer = {
            val message = state.message
            val permissionSender = state.permissionIntentSender
            val actionLabel = when {
                state.isRunning -> stringResource(R.string.action_abort)
                permissionSender != null -> stringResource(R.string.action_grant_permission)
                message != null -> stringResource(R.string.action_done)
                else -> stringResource(R.string.action_close)
            }
            val onAction = {
                when {
                    state.isRunning -> onCancel()
                    permissionSender != null -> onGrantPermission()
                    else -> onDismissRequest()
                }
            }
            TextButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 结果只在结束后出现；进行中靠下面那行「正在写入哪首歌」说明进展
                state.message?.let { message ->
                    if (salt) com.moriafly.salt.ui.Text(
                        text = message,
                        style = SaltTheme.textStyles.main,
                        color = SaltTheme.colors.subText
                    ) else Text(
                        text = message,
                        style = MiuixTheme.textStyles.main,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (salt) com.moriafly.salt.ui.Text(
                        text = state.activeSong ?: title,
                        style = SaltTheme.textStyles.sub,
                        color = SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    ) else Text(
                        text = state.activeSong
                            ?: title,
                        style = MiuixTheme.textStyles.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (salt) com.moriafly.salt.ui.Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = SaltTheme.textStyles.main,
                        color = SaltTheme.colors.text,
                        textAlign = TextAlign.End
                    ) else Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = MiuixTheme.textStyles.main,
                        textAlign = TextAlign.End
                    )
                }

                AppLinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (salt) com.moriafly.salt.ui.Text(
                        text = stringResource(
                            R.string.label_artist_poster_write_count,
                            state.done,
                            state.total
                        ),
                        style = SaltTheme.textStyles.main,
                        color = SaltTheme.colors.text
                    ) else Text(
                        text = stringResource(
                            R.string.label_artist_poster_write_count,
                            state.done,
                            state.total
                        ),
                        style = MiuixTheme.textStyles.main
                    )
                    if (state.failures.isNotEmpty()) {
                        if (salt) com.moriafly.salt.ui.Text(
                            text = stringResource(
                                R.string.label_artist_poster_write_failed,
                                state.failures.size
                            ),
                            style = SaltTheme.textStyles.main,
                            color = SaltTheme.colors.error
                        ) else Text(
                            text = stringResource(
                                R.string.label_artist_poster_write_failed,
                                state.failures.size
                            ),
                            style = MiuixTheme.textStyles.main,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                }

            }
        }
    )
}
