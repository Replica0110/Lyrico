package com.lonx.lyrico.ui.components.song


import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.player.PlayerPickerBottomSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SongActionSheets(
    selectedSong: SongEntity?,
    showMenuSheet: Boolean,
    showDetailSheet: Boolean,
    showDeleteDialog: Boolean,
    showRenameDialog: Boolean,
    onDismissMenu: () -> Unit,
    onDismissMenuFinished: () -> Unit,
    onDismissDetail: () -> Unit,
    onDismissDelete: () -> Unit,
    onDismissRename: () -> Unit,
    onShowDetail: () -> Unit,
    onShowDelete: () -> Unit,
    onShowRename: () -> Unit,
    onPlay: (SongEntity) -> Unit,
    onDelete: (SongEntity) -> Unit,
    onRename: (SongEntity, String) -> Unit
) {
    val context = LocalContext.current
    var showPlayerPicker by remember { mutableStateOf(false) }
    var pendingPlayUri by remember { mutableStateOf<Uri?>(null) }
    val song = selectedSong

    val shareTitle = stringResource(R.string.share_chooser_title)
    if (song != null) {
        SongMenuBottomSheet(
            show = showMenuSheet,
            song = song,
            onDismissRequest = onDismissMenu,
            onDismissFinished = onDismissMenuFinished,
            onPlay = {
                pendingPlayUri = song.getUri
                showPlayerPicker = true
                onDismissMenu()
            },
            showInfo = onShowDetail,
            onDelete = onShowDelete,
            onRename = onShowRename,
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, song.uri.toUri())
                    putExtra(Intent.EXTRA_TITLE, song.title ?: song.fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(
                    Intent.createChooser(
                        intent,
                        shareTitle
                    )
                )
            }
        )

        SongDetailBottomSheet(
            show = showDetailSheet,
            song = song,
            onDismissRequest = onDismissDetail
        )

        YesNoDialog(
            title = stringResource(R.string.dialog_delete_file_title),
            show = showDeleteDialog,
            summary = stringResource(
                R.string.dialog_delete_file_content,
                song.fileName
            ),
            onConfirm = {
                onDismissMenu()
                onDelete(song)
            },
            onDismissRequest = onDismissDelete
        )

        RenameSongDialog(
            show = showRenameDialog,
            song = song,
            onDismissRequest = onDismissRename,
            onConfirm = { newFileName ->
                onRename(song, newFileName)
            }
        )
    }

    PlayerPickerBottomSheet(
        show = showPlayerPicker,
        uri = pendingPlayUri,
        onDismissRequest = {
            showPlayerPicker = false
            pendingPlayUri = null
        }
    )
}

@Composable
private fun RenameSongDialog(
    show: Boolean,
    song: SongEntity,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val extensionDot = if (!song.fileExtension.isNullOrEmpty()) {
        ".${song.fileExtension}"
    } else {
        ""
    }

    val oldName = song.fileName.substringBeforeLast('.')

    var newName by remember(song.uri, song.fileName) {
        mutableStateOf(oldName)
    }

    YesNoDialog(
        title = androidx.compose.ui.res.stringResource(R.string.dialog_rename_title),
        show = show,
        onDismissRequest = onDismissRequest,
        onConfirm = {
            val fullNewName = newName.trim() + extensionDot
            if (newName.isNotBlank() && fullNewName != song.fileName) {
                onConfirm(fullNewName)
            }
        },
        content = {
            TextField(
                value = newName,
                onValueChange = { newName = it },
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (extensionDot.isNotEmpty()) {
                        Text(
                            text = extensionDot,
                            style = MiuixTheme.textStyles.footnote1,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }
            )
        }
    )
}
