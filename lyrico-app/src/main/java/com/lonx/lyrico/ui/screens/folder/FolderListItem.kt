@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.folder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.FolderEntity
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.domain.folder.FolderTreeNode
import com.lonx.lyrico.domain.folder.folderDisplayName
import com.lonx.lyrico.ui.components.base.AppLinearProgressIndicator
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.Item as SaltItem
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.popup.rememberPopupState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 文件夹行：名称、路径与扫描状态，菜单提供隐藏、刷新和移除。扫描中或选择模式下不响应点击。 */
@Composable
internal fun FolderListItem(
    folder: FolderEntity,
    node: FolderTreeNode,
    isScanning: Boolean,
    isQueued: Boolean,
    isSelectionMode: Boolean,
    canRemove: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onIgnoredChange: (Boolean) -> Unit,
) {
    val isBusy = isScanning || isQueued || isSelectionMode
    val statusText = when {
        isScanning -> stringResource(R.string.folder_scanning)
        isQueued -> stringResource(R.string.folder_scan_queued)
        folder.isIgnored -> stringResource(
            R.string.folder_hidden_child_song_count_format,
            node.childFolders.size,
            node.subtreeSongCount,
        )

        else -> stringResource(
            R.string.folder_child_song_count_format,
            node.childFolders.size,
            node.subtreeSongCount,
        )
    }

    val actions = buildList {
        add(
            FolderAction(
                label = stringResource(
                    if (folder.isIgnored) R.string.folder_action_show else R.string.folder_action_hide
                ),
                icon = if (folder.isIgnored) LucideIcons.Eye else LucideIcons.EyeOff,
                tone = PreferenceIconTone.Appearance,
                onClick = { onIgnoredChange(!folder.isIgnored) },
            )
        )
        add(
            FolderAction(
                label = stringResource(R.string.action_refresh_folder),
                icon = LucideIcons.RefreshCw,
                tone = PreferenceIconTone.Tools,
                onClick = { if (!isBusy) onRefresh() },
            )
        )
        if (canRemove) {
            add(
                FolderAction(
                    label = stringResource(R.string.folder_action_remove),
                    icon = LucideIcons.Folder,
                    tone = PreferenceIconTone.Library,
                    onClick = { if (!isBusy) onDelete() },
                )
            )
        }
    }

    val content = FolderItemContent(
        name = folder.path.folderDisplayName(),
        path = folder.path,
        status = statusText,
        actions = actions,
        enabled = !isBusy,
        isLoading = isScanning,
        onClick = onClick,
    )

    if (LocalUiEngine.current == UiEngine.SALT) SaltFolderItemRow(content) else MiuixFolderItemRow(content)
}

/** 菜单项的行为与图标；两套主题各自决定呈现方式。 */
private class FolderAction(
    val label: String,
    val icon: ImageVector,
    val tone: PreferenceIconTone,
    val onClick: () -> Unit,
)

private class FolderItemContent(
    val name: String,
    val path: String,
    val status: String,
    val actions: List<FolderAction>,
    val enabled: Boolean,
    val isLoading: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun SaltFolderItemRow(content: FolderItemContent) {
    val popup = rememberPopupState()
    RoundedColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SaltItem(
                text = content.name,
                sub = "${content.path}\n${content.status}",
                modifier = Modifier.weight(1f),
                enabled = content.enabled,
                onClick = content.onClick,
                arrowType = ItemArrowType.None,
            )
            Box(Modifier.padding(end = 8.dp)) {
                SaltIconButton(
                    LucideIcons.EllipsisVertical,
                    stringResource(R.string.cd_more_actions),
                    enabled = content.enabled,
                    onClick = popup::expend,
                )
                PopupMenu(expanded = popup.expend, onDismissRequest = popup::dismiss) {
                    content.actions.forEach { action ->
                        PopupMenuItem(
                            text = action.label,
                            onClick = {
                                popup.dismiss()
                                action.onClick()
                            },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(content.isLoading) {
            AppLinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MiuixFolderItemRow(content: FolderItemContent) {
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        BasicComponent(
            endActions = {
                OverlayIconDropdownMenu(
                    entry = DropdownEntry(
                        items = content.actions.map { action ->
                            DropdownItem(
                                icon = { modifier ->
                                    PreferenceIcon(action.icon, action.tone, modifier = modifier)
                                },
                                text = action.label,
                                onClick = action.onClick,
                            )
                        },
                    ),
                    enabled = content.enabled,
                ) {
                    Icon(LucideIcons.EllipsisVertical, stringResource(R.string.cd_more_actions))
                }
            },
            bottomAction = {
                AnimatedVisibility(visible = content.isLoading) { LinearProgressIndicator() }
            },
            enabled = content.enabled,
            onClick = content.onClick,
        ) {
            Text(content.name, color = MiuixTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(
                text = content.path,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = content.status,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
            )
        }
    }
}
