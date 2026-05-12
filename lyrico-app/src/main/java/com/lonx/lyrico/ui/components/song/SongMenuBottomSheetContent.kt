package com.lonx.lyrico.ui.components.song

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults

@SuppressLint("DefaultLocale")
@Composable
fun SongMenuBottomSheetContent(
    song: SongEntity,
    onPlay: () -> Unit,
    showInfo: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        val songTitle = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName
        val text =
            song.artist.takeIf { !it.isNullOrBlank() }?.let { "$songTitle - $it" } ?: songTitle
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier.padding(12.dp)
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
