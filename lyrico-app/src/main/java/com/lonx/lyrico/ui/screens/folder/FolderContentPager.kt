package com.lonx.lyrico.ui.screens.folder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.lonx.lyrico.data.db.entity.SongEntity

/** 目录切换的方向和两端目录；[fromFolderId] 与 [toFolderId] 总是不相同。 */
internal data class FolderContentTransition(
    val fromFolderId: Long,
    val toFolderId: Long,
    val forward: Boolean,
)

/** 一屏目录内容：目录决定子目录，歌曲只对当前目录可用。 */
internal data class FolderContentSnapshot(
    val folderId: Long,
    val songs: List<SongEntity>,
)

/**
 * 承载目录内容的横向分页，并在切换目录时让新旧内容错位滑动。
 *
 * 只负责过渡本身：分页索引与具体内容由 [pageContent] 决定，调用方据此渲染子目录页或歌曲页。
 */
@Composable
internal fun FolderContentPager(
    currentFolderId: Long,
    currentFolderSongs: List<SongEntity>,
    transition: FolderContentTransition?,
    pagerState: PagerState,
    onTransitionFinished: () -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (page: Int, snapshot: FolderContentSnapshot, modifier: Modifier) -> Unit,
) {
    val currentSnapshot = remember(currentFolderId, currentFolderSongs) {
        FolderContentSnapshot(folderId = currentFolderId, songs = currentFolderSongs)
    }
    val fromSnapshot = remember(transition) {
        transition?.let { FolderContentSnapshot(folderId = it.fromFolderId, songs = emptyList()) }
    }
    val toSnapshot = remember(transition, currentFolderId, currentFolderSongs) {
        transition?.let {
            FolderContentSnapshot(
                folderId = it.toFolderId,
                songs = if (it.toFolderId == currentFolderId) currentFolderSongs else emptyList(),
            )
        }
    }

    val progress = remember { Animatable(1f) }
    LaunchedEffect(transition) {
        if (transition == null) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            )
            onTransitionFinished()
        }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        modifier = modifier,
    ) { page ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
            val travelPx = with(LocalDensity.current) { maxWidth.toPx() * 0.45f }
            val from = fromSnapshot
            val to = toSnapshot

            if (transition == null || from == null || to == null) {
                pageContent(page, currentSnapshot, Modifier.fillMaxSize())
            } else {
                val fraction = progress.value
                val fromOffset = if (transition.forward) -travelPx * fraction else travelPx * fraction
                val toOffset = if (transition.forward) travelPx * (1f - fraction) else -travelPx * (1f - fraction)

                Box(modifier = Modifier.fillMaxSize()) {
                    pageContent(
                        page,
                        from,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = fromOffset
                                alpha = 1f - fraction
                            },
                    )
                    pageContent(
                        page,
                        to,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = toOffset
                                alpha = fraction
                            },
                    )
                }
            }
        }
    }
}
