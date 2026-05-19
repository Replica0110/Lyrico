package com.lonx.lyrico.ui.components.song

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
import com.lonx.lyrico.data.model.entity.SongEntity
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun SongMenuBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    enableNestedScroll: Boolean = true,
    song: SongEntity,
    onPlay: () -> Unit,
    showInfo: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
) {
    val songTitle = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName
    val text =
        song.artist.takeIf { !it.isNullOrBlank() }?.let { "$songTitle - $it" } ?: songTitle
    WindowBottomSheet(
        show = show,
        enableNestedScroll = enableNestedScroll,
        onDismissRequest = { onDismissRequest() },
        onDismissFinished = { onDismissFinished() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SmallTitle(
                text = text,
                insideMargin = PaddingValues(4.dp)
            )
            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                )
            ) {
                ArrowPreference(
                    title = stringResource(R.string.menu_action_play),
                    summary = stringResource(R.string.menu_action_play_sub),
                    onClick = { onPlay() }
                )
                ArrowPreference(
                    title = stringResource(R.string.menu_action_info),
                    onClick = { showInfo() }
                )
                ArrowPreference(
                    title = stringResource(R.string.menu_action_share),
                    onClick = { onShare() }
                )
                ArrowPreference(
                    title = stringResource(R.string.menu_action_rename),
                    onClick = { onRename() }
                )
                ArrowPreference(
                    title = stringResource(R.string.menu_action_delete),
                    summary = stringResource(R.string.menu_action_delete_sub),
                    titleColor = BasicComponentColors(
                        MiuixTheme.colorScheme.error,
                        MiuixTheme.colorScheme.disabledOnSecondaryVariant
                    ),
                    onClick = { onDelete() }
                )
            }
        }
    }
}