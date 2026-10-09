package com.lonx.lyrico.ui.components.song

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.ui.components.CoverRequest
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LyricoColors
import kotlin.math.abs
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
    swipeSelectionLabel: String? = null,
    swipeSelectionSecondaryLabel: String? = null,
    previewLabel: String? = null,
    previewText: String? = null,
    previewMatchQuery: String? = null,
    showTrackNumbers: Boolean = false,
    onClick: () -> Unit,
    onToggleSelection: (() -> Unit)? = null,
    onSwipeSelection: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val maxSwipeOffsetPx = swipeThresholdPx * 1.35f
    var swipeOffsetX by remember(song.uri) { mutableFloatStateOf(0f) }
    var isSwipeDragging by remember(song.uri) { mutableStateOf(false) }
    val animatedSwipeOffsetX by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = spring(),
        label = "SongListItemSwipeOffset"
    )
    val displayedSwipeOffsetX = if (isSwipeDragging) swipeOffsetX else animatedSwipeOffsetX
    val revealWidth = with(density) { abs(displayedSwipeOffsetX).toDp() }

    val backgroundColor =
        if (isSelected) {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            MiuixTheme.colorScheme.surface
        }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        if (onSwipeSelection != null && swipeSelectionLabel != null && revealWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
            ) {
                Box(
                    modifier = Modifier
                        .align(
                            if (displayedSwipeOffsetX >= 0f) {
                                Alignment.CenterStart
                            } else {
                                Alignment.CenterEnd
                            }
                        )
                        .width(revealWidth)
                        .fillMaxHeight()
                        .clipToBounds()
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = swipeSelectionLabel,
                            color = MiuixTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )

                        swipeSelectionSecondaryLabel?.let { secondaryLabel ->
                            Text(
                                text = secondaryLabel,
                                color = MiuixTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = displayedSwipeOffsetX
                }
                .background(backgroundColor)
            .then(
                if (onSwipeSelection != null) {
                    Modifier.pointerInput(onSwipeSelection, swipeThresholdPx, maxSwipeOffsetPx) {
                        var totalDragX = 0f

                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                                isSwipeDragging = true
                                swipeOffsetX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDragX += dragAmount
                                swipeOffsetX = totalDragX.coerceIn(
                                    -maxSwipeOffsetPx,
                                    maxSwipeOffsetPx
                                )
                                change.consume()
                            },
                            onDragEnd = {
                                if (abs(totalDragX) >= swipeThresholdPx) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onSwipeSelection()
                                }
                                totalDragX = 0f
                                swipeOffsetX = 0f
                                isSwipeDragging = false
                            },
                            onDragCancel = {
                                totalDragX = 0f
                                swipeOffsetX = 0f
                                isSwipeDragging = false
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
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
                if (showTrackNumbers) {
                    val trackNumber = song.trackerNumber
                        ?.substringBefore('/')
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "—"
                    Text(
                        text = song.discNumber?.let { "$trackNumber ($it)" } ?: trackNumber,
                        modifier = Modifier.widthIn(min = 36.dp, max = 72.dp),
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                                        MiuixTheme.colorScheme.secondary
                                    ),
                                )
                            )
                    ) {
                        Text(
                            text = song.fileName.substringAfterLast('.', "").uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSecondary,
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
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = song.artist.takeIf { !it.isNullOrBlank() }
                                ?: stringResource(R.string.unknown_artist),
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.Bold,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (!song.album.isNullOrBlank()) {
                            Text(
                                text = " - ${song.album}",
                                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                fontSize = 13.sp,
                                maxLines = 1,
                                fontWeight = FontWeight.Bold,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                    previewText?.takeIf { it.isNotBlank() }?.let { preview ->
                        Text(
                            text = highlightedLyricPreview(
                                text = previewLabel
                                    ?.let { stringResource(R.string.search_field_preview, it, preview) }
                                    ?: preview,
                                query = previewMatchQuery.orEmpty(),
                                highlightColor = MiuixTheme.colorScheme.primary
                            ),
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (song.bitrate > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${song.bitrate}kbps",
                            fontSize = 10.sp,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            fontWeight = FontWeight.SemiBold
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
}

private fun highlightedLyricPreview(
    text: String,
    query: String,
    highlightColor: Color
) = buildAnnotatedString {
    val keyword = query.trim()
    if (keyword.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }

    var startIndex = 0
    while (startIndex < text.length) {
        val matchIndex = text.indexOf(keyword, startIndex, ignoreCase = true)
        if (matchIndex < 0) {
            append(text.substring(startIndex))
            break
        }

        append(text.substring(startIndex, matchIndex))
        withStyle(
            SpanStyle(
                color = highlightColor,
                fontWeight = FontWeight.Bold
            )
        ) {
            append(text.substring(matchIndex, matchIndex + keyword.length))
        }
        startIndex = matchIndex + keyword.length
    }
}
