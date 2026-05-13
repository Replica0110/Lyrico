package com.lonx.lyrico.ui.components.song

import android.annotation.SuppressLint
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.ui.components.CoverRequest
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SongDetailBottomSheet(
    show: Boolean,
    song: SongEntity,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dateFormat = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
    WindowBottomSheet(
        show = show,
        enableNestedScroll = false,
        onDismissRequest = {
            onDismissRequest()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = CoverRequest(song.getUri, song.fileLastModified),
                    contentDescription = stringResource(R.string.cd_cover),
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_album_24dp),
                    error = painterResource(R.drawable.ic_album_24dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = song.artist.takeIf { !it.isNullOrBlank() }
                            ?: stringResource(R.string.unknown_artist),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }


            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                )
            ) {
                val copyToClipboard: (String) -> Unit = { text ->
                    clipboardManager.setText(AnnotatedString(text))
                    Toast.makeText(
                        context,
                        context.getString(R.string.msg_copied_to_clipboard),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                SongDetailItem(
                    stringResource(R.string.label_album),
                    song.album,
                    onCopy = copyToClipboard
                )
                SongDetailItem(
                    stringResource(R.string.label_date),
                    song.date,
                    onCopy = copyToClipboard
                )
                SongDetailItem(
                    stringResource(R.string.label_genre),
                    song.genre,
                    onCopy = copyToClipboard
                )
                SongDetailItem(
                    stringResource(R.string.label_track_number),
                    song.trackerNumber,
                    onCopy = copyToClipboard
                )
                SongDetailItem(
                    stringResource(R.string.label_duration),
                    if (song.durationMilliseconds > 0) {
                        val min = song.durationMilliseconds / 60000
                        val sec = (song.durationMilliseconds % 60000) / 1000
                        String.format("%d:%02d", min, sec)
                    } else null,
                    onCopy = copyToClipboard
                )

                SongDetailItem(
                    stringResource(R.string.label_bitrate),
                    if (song.bitrate > 0) "${song.bitrate} kbps" else null,
                    onCopy = copyToClipboard
                )

                SongDetailItem(
                    stringResource(R.string.label_sample_rate),
                    if (song.sampleRate > 0) "${song.sampleRate} Hz" else null,
                    onCopy = copyToClipboard
                )

                SongDetailItem(
                    stringResource(R.string.label_channels),
                    if (song.channels > 0) "${song.channels}" else null,
                    onCopy = copyToClipboard
                )
                SongDetailItem(
                    stringResource(R.string.label_date_added),
                    if (song.fileAdded > 0)
                        dateFormat.format(Date(song.fileAdded))
                    else null,
                    onCopy = copyToClipboard
                )

                SongDetailItem(
                    stringResource(R.string.label_date_modified),
                    if (song.fileLastModified > 0)
                        dateFormat.format(Date(song.fileLastModified))
                    else null,
                    onCopy = copyToClipboard
                )

                SongDetailItem(
                    stringResource(R.string.label_file_path),
                    song.filePath,
                    onCopy = copyToClipboard
                )

                SongDetailItem(
                    stringResource(R.string.label_file_size),
                    if (song.fileSize > 0)
                        Formatter.formatFileSize(context, song.fileSize)
                    else null,
                    onCopy = copyToClipboard
                )

                if (BuildConfig.DEBUG) {
                    SongDetailItem(
                        label = "文件URI",
                        value = song.uri,
                        onCopy = copyToClipboard
                    )
                    SongDetailItem(
                        label = "文件ID",
                        value = song.id.toString(),
                        onCopy = copyToClipboard
                    )
                    SongDetailItem(
                        label = "文件名",
                        value = song.fileName,
                        onCopy = copyToClipboard
                    )
                }

            }
        }
    }
}

@Composable
fun SongDetailItem(
    label: String,
    value: String?,
    onCopy: ((String) -> Unit)? = null
) {
    if (value.isNullOrBlank()) return

    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.main,
                modifier = Modifier.weight(1f)
            )

            if (onCopy != null) {
                IconButton(
                    modifier = Modifier.size(20.dp),
                    onClick = { onCopy(value) }
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = MiuixIcons.Copy,
                        contentDescription = "复制"
                    )
                }
            }
        }
    }
}
