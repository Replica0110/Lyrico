package com.lonx.lyrico.ui.components.song

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.ui.components.CoverRequest
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LyricoColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: SongEntity,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onToggleSelection: (() -> Unit)? = null,
) {
    val view = LocalView.current

    val backgroundColor =
        if (isSelected) {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            MiuixTheme.colorScheme.surface
        }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection?.invoke()
                    } else {
                        onClick()
                    }
                },
                onLongClick = if (isSelectionMode) {
                    null
                } else {
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onToggleSelection?.invoke()
                    }
                }
            )
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(LyricoColors.coverPlaceholder)
            ) {
                AsyncImage(
                    model = CoverRequest(song.getUri, song.fileLastModified),
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberTintedPainter(
                        painter = painterResource(R.drawable.ic_album_24dp),
                        tint = LyricoColors.coverPlaceholderIcon
                    ),
                    error = rememberTintedPainter(
                        painter = painterResource(R.drawable.ic_album_24dp),
                        tint = LyricoColors.coverPlaceholderIcon
                    )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MiuixTheme.colorScheme.onSecondaryContainer
                                ),
                            )
                        )
                ) {
                    Text(
                        text = song.fileName.substringAfterLast('.', "").uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 1.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist.takeIf { !it.isNullOrBlank() }
                            ?: stringResource(R.string.unknown_artist),
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!song.album.isNullOrBlank()) {
                        Text(
                            text = " - ${song.album}",
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (song.durationMilliseconds > 0) {
                    val minutes = song.durationMilliseconds / 60000
                    val seconds = (song.durationMilliseconds % 60000) / 1000
                    Text(
                        text = String.format("%d:%02d", minutes, seconds),
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontSize = 12.sp
                    )
                }

                if (song.bitrate > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${song.bitrate}kbps",
                        fontSize = 10.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            trailingContent?.let {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    trailingContent()
                }
            }
        }
    }
}