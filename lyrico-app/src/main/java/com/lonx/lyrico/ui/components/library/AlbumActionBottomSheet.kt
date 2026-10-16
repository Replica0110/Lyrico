package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.LucideIcons
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.lonx.lyrico.ui.components.base.ItemMenuAction
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun AlbumActionBottomSheet(
    show: Boolean,
    albumName: String,
    isCalculatingReplayGain: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCalculateReplayGain: () -> Unit
) {
    val actions: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        ItemMenuAction(
            icon = LucideIcons.Volume2, tone = PreferenceIconTone.Library,
            title = stringResource(R.string.menu_action_calculate_album_replay_gain),
            enabled = !isCalculatingReplayGain,
            onClick = {
                if (!isCalculatingReplayGain) {
                    onCalculateReplayGain()
                }
            }
        )
        ItemMenuAction(
            icon = LucideIcons.Share2, tone = PreferenceIconTone.Tools,
            title = stringResource(R.string.menu_action_share_album),
            onClick = onShare
        )
        ItemMenuAction(
            icon = LucideIcons.Trash, tone = PreferenceIconTone.Danger,
            title = stringResource(R.string.menu_action_delete_album),
            summary = stringResource(R.string.menu_action_delete_album_sub),
            onClick = onDelete
        )
    }
    val label = albumName.ifBlank { stringResource(R.string.album_detail_title) }
    if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT) {
        com.lonx.lyrico.ui.components.base.ItemMenuBottomSheet(
            show = show, label = label, onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished, content = actions)
    } else {
        WindowBottomSheet(show = show, onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished) {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
                SmallTitle(text = label, insideMargin = PaddingValues(4.dp))
                Card(Modifier.padding(bottom = 12.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
                    content = actions)
            }
        }
    }
}
