@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components

import com.lonx.lyrico.ui.components.icons.LucideIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FolderManagementItem(
    name: String,
    path: String,
    status: String,
    actions: DropdownEntry,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT) {
        val popup = com.moriafly.salt.ui.popup.rememberPopupState()
        com.moriafly.salt.ui.RoundedColumn {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                com.moriafly.salt.ui.Item(
                    text = name, sub = "$path\n$status", modifier = Modifier.weight(1f),
                    enabled = enabled, onClick = { onClick?.invoke() },
                    arrowType = com.moriafly.salt.ui.ItemArrowType.None,
                )
                Box(Modifier.padding(end = 8.dp)) {
                    com.lonx.lyrico.ui.components.base.SaltIconButton(
                        LucideIcons.EllipsisVertical, stringResource(R.string.cd_more_actions),
                        enabled = enabled && actions.enabled, onClick = popup::expend,
                    )
                    com.moriafly.salt.ui.popup.PopupMenu(
                        expanded = popup.expend, onDismissRequest = popup::dismiss,
                    ) {
                        actions.items.forEach { item ->
                            com.moriafly.salt.ui.popup.PopupMenuItem(
                                text = item.text, sub = item.summary,
                                onClick = { popup.dismiss(); item.onClick?.invoke() },
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(isLoading) {
                com.lonx.lyrico.ui.components.base.AppLinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        return
    }
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        BasicComponent(
            endActions = {
                OverlayIconDropdownMenu(entry = actions, enabled = enabled) {
                    Icon(LucideIcons.EllipsisVertical, stringResource(R.string.cd_more_actions))
                }
            },
            bottomAction = {
                AnimatedVisibility(visible = isLoading) { LinearProgressIndicator() }
            },
            enabled = enabled,
            onClick = onClick
        ) {
            Text(name, color = MiuixTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(path, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = MiuixTheme.textStyles.body2.fontSize)
            Spacer(Modifier.height(6.dp))
            Text(status, color = MiuixTheme.colorScheme.onSurfaceVariantActions, fontSize = MiuixTheme.textStyles.body2.fontSize)
        }
    }
}
