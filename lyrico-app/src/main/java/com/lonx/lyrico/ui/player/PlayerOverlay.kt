package com.lonx.lyrico.ui.player

import android.content.ContentResolver
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.palette.graphics.Palette
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipRect

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import coil3.compose.AsyncImage
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.playback.PlaybackRepeatMode
import com.lonx.lyrico.playback.PlaybackRepository
import com.lonx.lyrico.playback.PlaybackState
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.utils.LyricDecoder
import com.lonx.lyrico.utils.coil.CoverRequest
import com.lonx.lyrics.model.LyricsLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex


private enum class PlayerSheet {
    Hidden,
    NowPlaying,
    Queue
}

@Composable
fun PlayerOverlay(
    modifier: Modifier = Modifier,
    onEditMetadata: (String) -> Unit = {},
    playbackRepository: PlaybackRepository = koinInject()
) {
    val playbackState by playbackRepository.state.collectAsState()
    var sheet by remember { mutableStateOf(PlayerSheet.Hidden) }
    val scope = rememberCoroutineScope()

    if (!playbackState.hasMedia) {
        sheet = PlayerSheet.Hidden
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val rawAccent = rememberCoverAccentColor(playbackState.currentSong)
        // Smooth color transition when song changes
        val backgroundAccent by animateColorAsState(
            targetValue = rawAccent,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            label = "accentColor"
        )
        val density = LocalDensity.current
        val heightPx = with(density) { screenHeight.toPx() }
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val miniHeight = 64.dp + bottomInset
        val miniHeightPx = with(density) { miniHeight.toPx() }
        val collapsedOffset = (heightPx - miniHeightPx).coerceAtLeast(0f)
        val playingOffset = 0f
        val queueOffset = -heightPx
        val sheetOffset = remember { Animatable(0f) }
        var dragSnapJob by remember { mutableStateOf<Job?>(null) }
        var offsetReady by remember { mutableStateOf(false) }
        val sheetAnimationSpec = remember {
            tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
        }

        LaunchedEffect(heightPx) {
            if (heightPx > 0f && sheet == PlayerSheet.Hidden && sheetOffset.value == 0f) {
                sheetOffset.snapTo(collapsedOffset)
            }
            if (heightPx > 0f) {
                offsetReady = true
            }
        }

        fun showSheet(target: PlayerSheet) {
            sheet = target
            scope.launch {
                val targetOffset = when (target) {
                    PlayerSheet.Hidden -> collapsedOffset
                    PlayerSheet.NowPlaying -> playingOffset
                    PlayerSheet.Queue -> queueOffset
                }
                sheetOffset.animateTo(targetOffset, sheetAnimationSpec)
            }
        }

        fun hideSheet() {
            scope.launch {
                sheetOffset.animateTo(collapsedOffset, sheetAnimationSpec)
                sheet = PlayerSheet.Hidden
            }
        }

        fun collapseSheetImmediately() {
            sheet = PlayerSheet.Hidden
            scope.launch {
                sheetOffset.snapTo(collapsedOffset)
            }
        }

        fun snapSheetOffset(delta: Float) {
            dragSnapJob?.cancel()
            dragSnapJob = scope.launch {
                sheetOffset.snapTo((sheetOffset.value + delta).coerceIn(queueOffset, collapsedOffset))
            }
        }

        fun settleFromDrag(totalDrag: Float) {
            val current = sheetOffset.value
            val collapseThreshold = (collapsedOffset + playingOffset) / 2f
            val queueThreshold = playingOffset - heightPx / 2f
            val target = when {
                current < queueThreshold || totalDrag < -120f -> PlayerSheet.Queue
                current > collapseThreshold || totalDrag > 140f -> PlayerSheet.Hidden
                else -> PlayerSheet.NowPlaying
            }
            if (target == PlayerSheet.Hidden) hideSheet() else showSheet(target)
        }

        BackHandler(enabled = sheet != PlayerSheet.Hidden) {
            when (sheet) {
                PlayerSheet.Queue -> showSheet(PlayerSheet.NowPlaying)
                PlayerSheet.NowPlaying -> hideSheet()
                PlayerSheet.Hidden -> Unit
            }
        }

        AnimatedVisibility(
            visible = offsetReady,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val expandedProgress = if (collapsedOffset > 0f) {
                ((collapsedOffset - sheetOffset.value) / collapsedOffset).coerceIn(0f, 1f)
            } else {
                1f
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight * 2)
                        .offset { IntOffset(0, sheetOffset.value.roundToInt()) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(screenHeight)
                            .playerBackground(backgroundAccent)
                    ) {
                        MiniPlayerCard(
                            state = playbackState,
                            onExpand = { showSheet(PlayerSheet.NowPlaying) },
                            onTogglePlay = playbackRepository::togglePlayPause,
                            onQueue = { showSheet(PlayerSheet.Queue) },
                            onDragStart = { sheet = PlayerSheet.NowPlaying },
                            onDrag = { dragAmount -> snapSheetOffset(dragAmount) },
                            onDragEnd = { settleFromDrag(0f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(miniHeight)
                                .align(Alignment.TopCenter)
                                .zIndex(if (expandedProgress < 0.98f) 2f else -1f)
                                .alpha((1f - expandedProgress).coerceIn(0f, 1f))
                        )

                        NowPlayingSheet(
                            state = playbackState,
                            onCollapse = { hideSheet() },
                            onQueue = { showSheet(PlayerSheet.Queue) },
                            onEditMetadata = { songUri ->
                                collapseSheetImmediately()
                                onEditMetadata(songUri)
                            },
                            onTogglePlay = playbackRepository::togglePlayPause,
                            onPrevious = playbackRepository::skipToPrevious,
                            onNext = playbackRepository::skipToNext,
                            onSeek = playbackRepository::seekTo,
                            onShuffle = {
                                playbackRepository.setShuffleModeEnabled(!playbackState.shuffleModeEnabled)
                            },
                            onRepeat = {
                                playbackRepository.setRepeatMode(playbackState.repeatMode.next())
                            },
                            onDrag = { dragAmount -> snapSheetOffset(dragAmount) },
                            onDragEnd = { totalDrag -> settleFromDrag(totalDrag) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(screenHeight)
                                .zIndex(if (expandedProgress > 0.02f) 1f else -1f)
                                .alpha(expandedProgress)
                        )
                    }
                    PlaybackQueueSheet(
                        state = playbackState,
                        onCollapse = { showSheet(PlayerSheet.NowPlaying) },
                        onPlayIndex = playbackRepository::playQueueItem,
                        onDrag = { dragAmount -> snapSheetOffset(dragAmount) },
                        onDragEnd = { totalDrag ->
                            if (totalDrag > 48f || sheetOffset.value > queueOffset + heightPx * 0.5f) {
                                showSheet(PlayerSheet.NowPlaying)
                            } else {
                                showSheet(PlayerSheet.Queue)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(screenHeight)
                            .offset { IntOffset(0, heightPx.roundToInt()) }
                            .playerBackground(backgroundAccent)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerCard(
    state: PlaybackState,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onQueue: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = state.currentSong
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var dragTotal by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragTotal = 0f
                        onDragStart()
                    },
                    onVerticalDrag = { _, dragAmount ->
                        dragTotal += dragAmount
                        onDrag(dragAmount)
                    },
                    onDragEnd = {
                        onDragEnd()
                        dragTotal = 0f
                    }
                )
            }
            .clickable(onClick = onExpand)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .padding(bottom = bottomInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CoverImage(
            song = song,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.displayTitle(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.displaySubtitle(),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onTogglePlay) {
            Icon(
                painter = painterResource(if (state.isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp),
                contentDescription = null
            )
        }
        IconButton(onClick = onQueue) {
            Icon(
                painter = painterResource(R.drawable.ic_queue_24dp),
                contentDescription = null
            )
        }
    }
}

@Composable
private fun NowPlayingSheet(
    state: PlaybackState,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    onEditMetadata: (String) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val pagerState = rememberPagerState { 2 }
    Column(
        modifier = modifier
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragTotal += dragAmount
                        onDrag(dragAmount)
                    },
                    onDragEnd = {
                        onDragEnd(dragTotal)
                        dragTotal = 0f
                    }
                )
            }
            .padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.height(56.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.displayTitle(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.displaySubtitle(),
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_edit_24dp),
                contentDescription = null,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        state.currentSong?.uri?.let(onEditMetadata)
                    }
                    .padding(10.dp)
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val contentMaxWidth = maxWidth
            val contentMaxHeight = maxHeight
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                if (page == 0) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val coverSize = minOf(contentMaxWidth * 0.84f, contentMaxHeight * 0.72f)
                        CoverImage(
                            song = state.currentSong,
                            modifier = Modifier
                                .size(coverSize)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                } else {
                    WordByWordLyrics(
                        song = state.currentSong,
                        positionMs = state.positionMs,
                        isPlaying = state.isPlaying,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Text(
            text = state.audioSummary(),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            textAlign = TextAlign.Center
        )
        PlaybackProgress(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            onSeek = onSeek
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_previous_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    painter = painterResource(if (state.isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconToggle(
                iconRes = R.drawable.ic_shuffle_24dp,
                selected = state.shuffleModeEnabled,
                onClick = onShuffle
            )
            IconToggle(
                iconRes = R.drawable.ic_queue_24dp,
                selected = false,
                onClick = onQueue
            )
            IconToggle(
                iconRes = if (state.repeatMode == PlaybackRepeatMode.ONE) {
                    R.drawable.ic_repeat_one_24dp
                } else {
                    R.drawable.ic_repeat_24dp
                },
                selected = state.repeatMode != PlaybackRepeatMode.OFF,
                onClick = onRepeat
            )
        }
    }
}

@Composable
private fun PlaybackQueueSheet(
    state: PlaybackState,
    onCollapse: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentIndex, state.queue.size) {
        if (state.currentIndex >= 0 && state.queue.isNotEmpty()) {
            listState.animateScrollToItem(state.currentIndex.coerceAtMost(state.queue.lastIndex))
        }
    }

    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragTotal += dragAmount
                        onDrag(dragAmount)
                    },
                    onDragEnd = {
                        onDragEnd(dragTotal)
                        dragTotal = 0f
                    }
                )
            }
            .padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = "此处向下轻扫以返回播放界面",
            modifier = Modifier.fillMaxWidth(),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CoverImage(
                song = state.currentSong,
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.displayTitle(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.displaySubtitle(),
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 34.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${(state.currentIndex + 1).coerceAtLeast(0)} / ${state.queue.size}",
                fontSize = 18.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "播放队列",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "清除",
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.18f))
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            )
        ) {
            itemsIndexed(
                items = state.queue,
                key = { index, song -> song.uri.takeIf { it.isNotBlank() } ?: "queue-$index-${song.id}" }
            ) { index, song ->
                val selected = index == state.currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { onPlayIndex(index) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.displayTitle(),
                            fontSize = 21.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.displaySubtitle(),
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_remove_24dp),
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceContainerVariant
                    )
                }
            }
        }
    }
}

/**
 * Smoothly interpolates playback position between the 250ms state updates.
 * This eliminates karaoke jitter and compensates for position lag.
 */
@Composable
private fun rememberInterpolatedPosition(
    positionMs: Long,
    isPlaying: Boolean
): Long {
    var interpolatedPosition by remember { mutableLongStateOf(positionMs) }
    var lastFrameTime by remember { mutableLongStateOf(0L) }

    // When a new position arrives from the repository, snap to it
    LaunchedEffect(positionMs) {
        interpolatedPosition = positionMs
        lastFrameTime = 0L
    }

    // Frame-based interpolation while playing
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        lastFrameTime = 0L
        while (isActive) {
            withFrameMillis { frameTimeMs ->
                if (lastFrameTime > 0L) {
                    val delta = (frameTimeMs - lastFrameTime).coerceIn(0L, 50L)
                    interpolatedPosition += delta
                }
                lastFrameTime = frameTimeMs
            }
        }
    }

    return interpolatedPosition
}

@Composable
private fun WordByWordLyrics(
    song: SongEntity?,
    positionMs: Long,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    val lyricsResult = remember(song?.lyrics) {
        song?.lyrics?.takeIf { it.isNotBlank() }?.let(LyricDecoder::decode)
    }
    val lines = lyricsResult?.original.orEmpty()
    val romanizationLines = remember(lines, lyricsResult?.romanization) {
        alignSupplementalLyrics(lines, lyricsResult?.romanization)
    }
    val translatedLines = remember(lines, lyricsResult?.translated) {
        alignSupplementalLyrics(lines, lyricsResult?.translated)
    }
    val listState = rememberLazyListState()
    var autoFollowLyrics by remember { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }

    // Use interpolated position for smooth karaoke
    val smoothPosition = rememberInterpolatedPosition(positionMs, isPlaying)

    val activeIndex = remember(lines, smoothPosition) {
        lines.findActiveLyricIndex(smoothPosition)
    }
    val latestActiveIndex by rememberUpdatedState(activeIndex)
    val latestLines by rememberUpdatedState(lines)

    LaunchedEffect(activeIndex, autoFollowLyrics) {
        if (lines.isEmpty() || !autoFollowLyrics) return@LaunchedEffect

        autoScrolling = true
        listState.animateScrollToItem(
            index = activeIndex,
            scrollOffset = -104
        )
        autoScrolling = false
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling && !autoScrolling) {
                    autoFollowLyrics = false
                } else if (!autoFollowLyrics) {
                    delay(3_000L)
                    autoFollowLyrics = true
                    if (latestLines.isNotEmpty()) {
                        listState.animateScrollToItem(latestActiveIndex, scrollOffset = -112)
                    }
                }
            }
    }

    if (lines.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂无可显示歌词",
                fontSize = 22.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.lyricEdgeFade(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 150.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            items = lines,
            key = { index, line -> "${line.start}-${line.end}-$index" }
        ) { index, line ->
            val distance = abs(index - activeIndex)
            val alpha by animateFloatAsState(
                targetValue = when (distance) {
                    0 -> 1f
                    1 -> 0.58f
                    2 -> 0.34f
                    else -> 0.18f
                },
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "lyricLineAlpha"
            )
            val linePositionMs = when {
                index == activeIndex -> smoothPosition
                index < activeIndex -> {
                    val nextLine = lines.getOrNull(index + 1)
                    if (nextLine != null && smoothPosition < nextLine.start) {
                        smoothPosition
                    } else {
                        line.end
                    }
                }
                else -> line.start
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha)
            ) {
                LyricLineText(
                    line = line,
                    positionMs = linePositionMs,
                    active = index == activeIndex,
                    modifier = Modifier.fillMaxWidth()
                )
                romanizationLines
                    .getOrNull(index)
                    ?.takeIf { it.words.any { word -> word.text.isNotBlank() } }
                    ?.let { romanization ->
                        Text(
                            text = romanization.words.joinToString(" ") { it.text },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                        )
                    }
                translatedLines
                    .getOrNull(index)
                    ?.takeIf { it.words.any { word -> word.text.isNotBlank() } }
                    ?.let { translated ->
                        Text(
                            text = translated.words.joinToString("") { it.text },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                        )
                    }
            }
        }
    }
}

@Composable
private fun LyricLineText(
    line: LyricsLine,
    positionMs: Long,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val activeColor = MiuixTheme.colorScheme.onSurface
    val inactiveColor = MiuixTheme.colorScheme.onSurfaceContainerVariant
    val lineText = remember(line) {
        line.words.joinToString("") { it.text }
    }
    val activeProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "lyricActiveProgress"
    )
    val fontSize = 17.sp
    val lineHeight = 21.sp
    val fontWeight = FontWeight.SemiBold
    val textStyle = TextStyle(
        color = if (active) inactiveColor.copy(alpha = 0.88f) else inactiveColor,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center
    )

    Box(modifier = modifier.wrapContentHeight()) {
        if (activeProgress > 0.01f) {
            CanvasKaraokeLineText(
                line = line,
                text = lineText,
                positionMs = positionMs,
                textStyle = textStyle,
                activeColor = activeColor,
                inactiveColor = inactiveColor.copy(alpha = 0.88f),
                activeAlpha = activeProgress,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = lineText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = fontWeight,
                color = inactiveColor
            )
        }
    }
}

@Composable
private fun CanvasKaraokeLineText(
    line: LyricsLine,
    text: String,
    positionMs: Long,
    textStyle: TextStyle,
    activeColor: Color,
    inactiveColor: Color,
    activeAlpha: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val layoutResult = remember(text, textStyle, maxWidthPx) {
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = textStyle,
                constraints = Constraints(maxWidth = maxWidthPx)
            )
        }
        val height = with(density) { layoutResult.size.height.toDp() }

        // Compute per-visual-line pixel progress directly from word timing
        // This maps current time to an exact pixel X position per row
        val lineProgressData = remember(line, text, layoutResult) {
            buildLinePixelMapping(line, text, layoutResult)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val topLeft = androidx.compose.ui.geometry.Offset(
                x = (size.width - layoutResult.size.width).coerceAtLeast(0f) / 2f,
                y = 0f
            )
            drawText(
                textLayoutResult = layoutResult,
                color = inactiveColor,
                topLeft = topLeft
            )
            drawKaraokeGradientFill(
                layoutResult = layoutResult,
                lineProgressData = lineProgressData,
                positionMs = positionMs,
                activeColor = activeColor.copy(alpha = activeAlpha.coerceIn(0f, 1f)),
                topLeft = topLeft
            )
        }
    }
}

@Composable
private fun PlaybackProgress(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    var draggingValue by remember { mutableStateOf<Float?>(null) }
    val sliderValue = draggingValue ?: if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Slider(
        value = sliderValue,
        onValueChange = { draggingValue = it },
        onValueChangeFinished = {
            draggingValue?.let { onSeek((it * durationMs).roundToInt().toLong()) }
            draggingValue = null
        },
        colors = SliderDefaults.colors(
            thumbColor = MiuixTheme.colorScheme.onSurface,
            activeTrackColor = MiuixTheme.colorScheme.onSurface,
            inactiveTrackColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = positionMs.formatTime(),
            fontSize = 17.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = durationMs.formatTime(),
            fontSize = 17.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant
        )
    }
}

@Composable
private fun IconToggle(
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .size(22.dp)
    )
}

@Composable
private fun CoverImage(
    song: SongEntity?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(LyricoColors.coverPlaceholder),
        contentAlignment = Alignment.Center
    ) {
        if (song != null) {
            AsyncImage(
                model = CoverRequest(song.getUri, song.fileLastModified),
                contentDescription = song.displayTitle(),
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
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_album_24dp),
                contentDescription = null,
                tint = LyricoColors.coverPlaceholderIcon
            )
        }
    }
}

@Composable
private fun rememberCoverAccentColor(song: SongEntity?): Color {
    val context = LocalContext.current
    val fallback = MiuixTheme.colorScheme.primary
    var accent by remember { mutableStateOf(fallback) }

    LaunchedEffect(song?.uri, song?.fileLastModified, fallback) {
        accent = fallback
        if (song != null) {
            readCoverAccentColor(context.contentResolver, song.getUri)?.let { accent = it }
        }
    }

    return accent
}

@Composable
private fun Modifier.playerBackground(accent: Color): Modifier {
    val surface = MiuixTheme.colorScheme.surface
    val softAccent = accent.copy(alpha = 0.24f)
    val faintAccent = accent.copy(alpha = 0.12f)
    val flow = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to softAccent,
            0.28f to faintAccent,
            0.52f to surface,
            0.74f to accent.copy(alpha = 0.14f),
            1f to surface
        )
    )
    return background(surface).background(flow)
}

private fun PlaybackRepeatMode.next(): PlaybackRepeatMode {
    return when (this) {
        PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
        PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
        PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
    }
}

private fun PlaybackState.displayTitle(): String {
    return currentSong?.displayTitle()
        ?: fallbackUri?.lastPathSegment
        ?: "未知歌曲"
}

private fun PlaybackState.displaySubtitle(): String {
    return currentSong?.displaySubtitle()
        ?: fallbackUri?.toString()
        ?: ""
}

private fun PlaybackState.audioSummary(): String {
    val song = currentSong ?: return "AudioTrack"
    val extension = song.fileExtension?.uppercase().orEmpty()
    val bitDepth = song.rawProperties
        ?.let { Regex("(?i)(bitDepth|bitsPerSample)\"?\\s*[:=]\\s*\"?(\\d+)").find(it)?.groupValues?.getOrNull(2) }
        ?.let { "$it bits" }
    val sampleRate = song.sampleRate.takeIf { it > 0 }?.let { "${it / 1000.0}".trimEnd('0').trimEnd('.') + " kHz" }
    return listOf("AudioTrack", extension, bitDepth, sampleRate)
        .filter { !it.isNullOrBlank() }
        .joinToString("   ")
}

private fun SongEntity.displayTitle(): String {
    return title.takeIf { !it.isNullOrBlank() } ?: fileName
}

private fun SongEntity.displaySubtitle(): String {
    val artistText = artist.takeIf { !it.isNullOrBlank() }
    val albumText = album.takeIf { !it.isNullOrBlank() }
    return listOfNotNull(artistText, albumText).joinToString(" - ")
        .ifBlank { fileName }
}

private fun Long.formatTime(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun List<LyricsLine>.findActiveLyricIndex(positionMs: Long): Int {
    if (isEmpty()) return 0
    var low = 0
    var high = lastIndex
    var result = 0
    while (low <= high) {
        val middle = (low + high) ushr 1
        if (positionMs >= this[middle].start) {
            result = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return result
}

private fun alignSupplementalLyrics(
    original: List<LyricsLine>,
    supplemental: List<LyricsLine>?
): List<LyricsLine?> {
    if (original.isEmpty()) return emptyList()
    if (supplemental.isNullOrEmpty()) return List(original.size) { null }
    if (supplemental.size == original.size) return supplemental

    val used = BooleanArray(supplemental.size)
    return original.mapIndexed { index, line ->
        val previousStart = original.getOrNull(index - 1)?.start
        val nextStart = original.getOrNull(index + 1)?.start
        val windowStart = previousStart?.let { (it + line.start) / 2 } ?: (line.start - 3_000L)
        val windowEnd = nextStart?.let { (it + line.start) / 2 } ?: (line.start + 3_000L)

        supplemental
            .withIndex()
            .filter { (supplementalIndex, candidate) ->
                !used[supplementalIndex] && candidate.start in windowStart..windowEnd
            }
            .minByOrNull { (_, candidate) -> abs(candidate.start - line.start) }
            ?.also { used[it.index] = true }
            ?.value
    }
}

private fun LyricsLine.progressFractionAt(positionMs: Long): Float {
    val duration = (end - start).coerceAtLeast(1L)
    return ((positionMs - start).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}


/**
 * Pre-computed pixel mapping data for a karaoke line.
 * Maps each word's timing to its pixel boundaries in the text layout.
 */
private data class WordPixelSpan(
    val startMs: Long,
    val endMs: Long,
    val startPx: Float,  // left edge of word's first char
    val endPx: Float,    // right edge of word's last char
    val visualLine: Int  // which visual line (row) this word belongs to
)

private data class LinePixelMapping(
    val wordSpans: List<WordPixelSpan>,
    val rowMappings: List<RowPixelMapping>,
    val lineStart: Long,
    val lineEnd: Long
)

private data class RowPixelMapping(
    val visualLine: Int,
    val wordSpans: List<WordPixelSpan>,
    val minX: Float,
    val maxX: Float,
    val top: Float,
    val bottom: Float,
    val firstStartMs: Long,
    val lastEndMs: Long
)

/**
 * Builds pixel mapping from word timing data to actual pixel positions in the layout.
 * This enables direct time-to-pixel-position conversion for the gradient sweep.
 */
private fun buildLinePixelMapping(
    line: LyricsLine,
    text: String,
    layoutResult: TextLayoutResult
): LinePixelMapping {
    if (text.isEmpty() || line.words.isEmpty()) {
        return LinePixelMapping(emptyList(), emptyList(), line.start, line.end)
    }

    val wordSpans = mutableListOf<WordPixelSpan>()
    var charOffset = 0

    for (word in line.words) {
        val wordLength = word.text.length
        if (wordLength == 0) continue

        val wordStartChar = charOffset
        val wordEndChar = (charOffset + wordLength - 1).coerceAtMost(text.lastIndex)

        // Determine which visual line the word starts/ends on
        val startVisualLine = layoutResult.getLineForOffset(wordStartChar)
        val endVisualLine = layoutResult.getLineForOffset(wordEndChar)

        if (startVisualLine == endVisualLine) {
            // Word fits on a single visual line
            val startBounds = layoutResult.getBoundingBox(wordStartChar)
            val endBounds = layoutResult.getBoundingBox(wordEndChar)
            wordSpans.add(
                WordPixelSpan(
                    startMs = word.start,
                    endMs = word.end,
                    startPx = startBounds.left,
                    endPx = endBounds.right,
                    visualLine = startVisualLine
                )
            )
        } else {
            // Word spans multiple visual lines - split into per-row segments
            val wordDuration = (word.end - word.start).coerceAtLeast(1L)
            var segStart = wordStartChar
            for (vl in startVisualLine..endVisualLine) {
                val rowEnd = layoutResult.getLineEnd(vl, visibleEnd = true)
                val segEnd = minOf(wordEndChar, rowEnd - 1).coerceAtLeast(segStart)
                val startBounds = layoutResult.getBoundingBox(segStart)
                val endBounds = layoutResult.getBoundingBox(segEnd)
                val segStartFraction = (segStart - wordStartChar).toFloat() / wordLength
                val segEndFraction = (segEnd - wordStartChar + 1).toFloat() / wordLength
                wordSpans.add(
                    WordPixelSpan(
                        startMs = word.start + (wordDuration * segStartFraction).toLong(),
                        endMs = word.start + (wordDuration * segEndFraction).toLong(),
                        startPx = startBounds.left,
                        endPx = endBounds.right,
                        visualLine = vl
                    )
                )
                segStart = segEnd + 1
                if (segStart > wordEndChar) break
            }
        }
        charOffset += wordLength
    }

    val rowMappings = wordSpans
        .groupBy { it.visualLine }
        .mapNotNull { (visualLine, spans) ->
            val minX = layoutResult.getLineLeft(visualLine)
            val maxX = layoutResult.getLineRight(visualLine)
            if (maxX <= minX) return@mapNotNull null
            RowPixelMapping(
                visualLine = visualLine,
                wordSpans = spans.sortedBy { it.startMs },
                minX = minX,
                maxX = maxX,
                top = layoutResult.getLineTop(visualLine),
                bottom = layoutResult.getLineBottom(visualLine),
                firstStartMs = spans.minOf { it.startMs },
                lastEndMs = spans.maxOf { it.endMs }
            )
        }
        .sortedBy { it.visualLine }

    return LinePixelMapping(wordSpans, rowMappings, line.start, line.end)
}

/**
 * Computes the pixel X position for the karaoke sweep at the given time,
 * for the given visual line row.
 * Returns null if this row hasn't started yet.
 * Returns Float.MAX_VALUE if this row is fully completed.
 */
private fun computeRowProgressPx(
    rowMapping: RowPixelMapping,
    positionMs: Long,
): Float? {
    val rowSpans = rowMapping.wordSpans
    if (rowSpans.isEmpty()) return null

    if (positionMs < rowMapping.firstStartMs) return null

    // If position is after the last word in this row - row fully swept
    if (positionMs >= rowMapping.lastEndMs) return Float.MAX_VALUE

    // Find the active word span in this row
    var prevEndPx = rowSpans.first().startPx
    var prevEndMs = rowSpans.first().startMs
    for (span in rowSpans) {
        if (positionMs < span.startMs) {
            // In a gap between previous word end and this word start
            // Interpolate smoothly between the two
            val gapDuration = (span.startMs - prevEndMs).coerceAtLeast(1L)
            val gapProgress = ((positionMs - prevEndMs).toFloat() / gapDuration).coerceIn(0f, 1f)
            return prevEndPx + (span.startPx - prevEndPx) * gapProgress
        }
        if (positionMs in span.startMs..span.endMs) {
            // Currently within this word - interpolate pixel position
            val duration = (span.endMs - span.startMs).coerceAtLeast(1L)
            val progress = ((positionMs - span.startMs).toFloat() / duration).coerceIn(0f, 1f)
            return span.startPx + (span.endPx - span.startPx) * progress
        }
        prevEndPx = span.endPx
        prevEndMs = span.endMs
    }

    // Past all spans in this row
    return Float.MAX_VALUE
}

/**
 * Draws the karaoke gradient fill per visual line.
 * Uses per-row saveLayer + DstIn compositing for the sweep effect.
 */
private fun DrawScope.drawKaraokeGradientFill(
    layoutResult: TextLayoutResult,
    lineProgressData: LinePixelMapping,
    positionMs: Long,
    activeColor: Color,
    topLeft: androidx.compose.ui.geometry.Offset
) {
    if (lineProgressData.rowMappings.isEmpty()) return
    if (positionMs < lineProgressData.lineStart) return

    val layerPaint = Paint()

    for (rowMapping in lineProgressData.rowMappings) {
        val progressPx = computeRowProgressPx(rowMapping, positionMs)
            ?: continue

        val localLeft = rowMapping.minX
        val fullLineRight = rowMapping.maxX
        val localTop = rowMapping.top
        val localBottom = rowMapping.bottom
        val rowWidth = (rowMapping.maxX - rowMapping.minX).coerceAtLeast(1f)
        val rowHeight = (rowMapping.bottom - rowMapping.top).coerceAtLeast(1f)
        val horizontalPad = rowWidth * 0.2f
        val verticalPad = rowHeight * 0.1f + 2f

        if (progressPx >= Float.MAX_VALUE || progressPx >= fullLineRight) {
            clipRect(
                left = topLeft.x + localLeft - horizontalPad,
                top = topLeft.y + localTop - verticalPad,
                right = topLeft.x + fullLineRight + horizontalPad,
                bottom = topLeft.y + localBottom + verticalPad
            ) {
                drawText(
                    textLayoutResult = layoutResult,
                    color = activeColor,
                    topLeft = topLeft
                )
            }
            continue
        }

        val minFadeWidth = minOf(100f, rowWidth)
        val fadeCenterNorm = ((progressPx - localLeft) / rowWidth).coerceIn(0f, 1f)
        val fadeRange = (minFadeWidth / rowWidth).coerceAtMost(1f)
        val effectiveCenter = -fadeRange / 2f + fadeCenterNorm * (1f + fadeRange)
        val fadeStart = (effectiveCenter - fadeRange / 2f).coerceIn(0f, 1f)
        val fadeEnd = (effectiveCenter + fadeRange / 2f).coerceIn(0f, 1f)

        val layerBounds = Rect(
            left = topLeft.x + localLeft - horizontalPad,
            top = topLeft.y + localTop - verticalPad,
            right = topLeft.x + fullLineRight + horizontalPad,
            bottom = topLeft.y + localBottom + verticalPad
        )

        drawIntoCanvas { canvas ->
            canvas.saveLayer(layerBounds, layerPaint)
            drawText(
                textLayoutResult = layoutResult,
                color = activeColor,
                topLeft = topLeft
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.White,
                        fadeStart to Color.White,
                        fadeEnd to Color.White.copy(alpha = 0f),
                        1f to Color.Transparent
                    ),
                    startX = topLeft.x + localLeft,
                    endX = topLeft.x + fullLineRight
                ),
                topLeft = layerBounds.topLeft,
                size = layerBounds.size,
                blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }
    }
}




private fun Modifier.lyricEdgeFade(): Modifier {
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.14f to Color.Black,
                    0.82f to Color.Black,
                    1f to Color.Transparent
                )
            ),
            blendMode = BlendMode.DstIn
        )
    }
}

/**
 * Extracts a vibrant accent color from the album cover using Android's Palette API.
 * Prioritizes saturated/vibrant colors over light/muted ones.
 * Falls back to a manual weighted extraction if Palette yields nothing usable.
 */
private suspend fun readCoverAccentColor(
    contentResolver: ContentResolver,
    uri: android.net.Uri
): Color? {
    return withContext(kotlinx.coroutines.Dispatchers.IO) {
        val pictureBytes = contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            AudioTagReader.readPicture(pfd)
        }?.takeIf { it.isNotEmpty() } ?: return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        // Downsample to ~128px for fast palette extraction
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 128 || bounds.outHeight / sampleSize > 128) {
            sampleSize *= 2
        }

        val bitmap = BitmapFactory.decodeByteArray(
            pictureBytes,
            0,
            pictureBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return@withContext null

        try {
            val palette = Palette.from(bitmap)
                .maximumColorCount(24)
                .generate()

            // Priority order: vibrant > dark vibrant > muted > dark muted > dominant
            // This ensures we get a saturated, non-pale color
            val swatch = palette.vibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.darkMutedSwatch
                ?: palette.dominantSwatch

            if (swatch != null) {
                val rgb = swatch.rgb
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(rgb, hsv)
                // Ensure minimum saturation and appropriate lightness
                // Boost saturation if too low, cap value to avoid overly bright colors
                hsv[1] = hsv[1].coerceAtLeast(0.25f) // min saturation
                hsv[2] = hsv[2].coerceIn(0.3f, 0.82f) // not too dark, not too bright
                val adjusted = android.graphics.Color.HSVToColor(hsv)
                Color(
                    android.graphics.Color.red(adjusted),
                    android.graphics.Color.green(adjusted),
                    android.graphics.Color.blue(adjusted)
                )
            } else {
                // Fallback: weighted sampling favoring saturated pixels
                extractWeightedAccentColor(bitmap)
            }
        } finally {
            bitmap.recycle()
        }
    }
}

/**
 * Fallback color extraction that samples pixels and weights by saturation.
 * Light/desaturated pixels contribute less to the final average.
 */
private fun extractWeightedAccentColor(bitmap: android.graphics.Bitmap): Color? {
    val xStep = (bitmap.width / 20).coerceAtLeast(1)
    val yStep = (bitmap.height / 20).coerceAtLeast(1)
    val hsv = FloatArray(3)
    var totalWeight = 0.0
    var weightedR = 0.0
    var weightedG = 0.0
    var weightedB = 0.0

    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 32) {
                x += xStep
                continue
            }
            android.graphics.Color.colorToHSV(pixel, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            // Weight: prefer saturated mid-brightness pixels
            // Desaturated or very bright pixels get low weight
            val satWeight = saturation * saturation // quadratic saturation boost
            val valWeight = if (value > 0.85f) 0.3f else if (value < 0.15f) 0.2f else 1f
            val weight = (satWeight * valWeight).toDouble()
            if (weight > 0.01) {
                weightedR += android.graphics.Color.red(pixel) * weight
                weightedG += android.graphics.Color.green(pixel) * weight
                weightedB += android.graphics.Color.blue(pixel) * weight
                totalWeight += weight
            }
            x += xStep
        }
        y += yStep
    }

    if (totalWeight < 0.01) return null

    val r = (weightedR / totalWeight).toInt().coerceIn(40, 220)
    val g = (weightedG / totalWeight).toInt().coerceIn(40, 220)
    val b = (weightedB / totalWeight).toInt().coerceIn(40, 220)

    // Final saturation check - ensure result is not too pale
    val resultHsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(r, g, b, resultHsv)
    resultHsv[1] = resultHsv[1].coerceAtLeast(0.25f)
    resultHsv[2] = resultHsv[2].coerceIn(0.3f, 0.82f)
    val final = android.graphics.Color.HSVToColor(resultHsv)
    return Color(
        android.graphics.Color.red(final),
        android.graphics.Color.green(final),
        android.graphics.Color.blue(final)
    )
}
