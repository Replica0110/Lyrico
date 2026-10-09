package com.lonx.lyrico.screens

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lonx.lyrico.R
import com.lonx.lyrico.viewmodel.ArtistPosterFile
import com.lonx.lyrico.viewmodel.ArtistPosterFolder
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
internal fun ArtistPosterFolderContents(
    folder: ArtistPosterFolder,
    revision: Long,
    isLoading: Boolean,
    hasError: Boolean,
    onChangeFolder: () -> Unit,
    onRefresh: () -> Unit,
    padding: PaddingValues,
    scrollBehavior: ScrollBehavior
) {
    val files = folder.posters.orEmpty()
    var previewUri by remember(folder.uri) { mutableStateOf<String?>(null) }
    if (files.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(padding)) {
            ArtistPosterDirectoryCard(folder, !isLoading, onChangeFolder,
                Modifier.padding(top = 12.dp))
            if (isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val unavailable = folder.posters == null
                ArtistPosterEmptyState(
                    title = stringResource(if (unavailable) R.string.artist_poster_folder_unavailable else R.string.artist_poster_no_files),
                    summary = stringResource(if (hasError) R.string.artist_poster_folder_error else if (unavailable) R.string.artist_poster_folders_empty else R.string.artist_poster_empty_hint),
                    actionText = stringResource(if (unavailable) R.string.artist_poster_folder_change else R.string.action_refresh_folder),
                    onAction = if (unavailable) onChangeFolder else onRefresh,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = padding,
        modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        overscrollEffect = null
    ) {
        item(key = "folder", span = { GridItemSpan(maxLineSpan) }) {
            ArtistPosterDirectoryCard(folder, !isLoading, onChangeFolder,
                Modifier.padding(top = 12.dp))
        }
        item(key = "count", span = { GridItemSpan(maxLineSpan) }) {
            SmallTitle(
                text = stringResource(R.string.artist_poster_file_count, files.size),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))
            }
        }
        if (hasError) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.artist_poster_folder_error),
                    color = MiuixTheme.colorScheme.error,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        items(files, key = { it.uri }) { file ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { previewUri = file.uri },
                showIndication = true
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    PosterImage(file, revision, Modifier.fillMaxSize(), ContentScale.Crop)
                    if (file.name.endsWith(".mp4", true)) {
                        Text("MP4", color = Color.White, modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(6.dp).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text(
                    file.name,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
    val previewIndex = files.indexOfFirst { it.uri == previewUri }
    if (previewIndex >= 0) {
        PosterPreview(files, previewIndex, revision) { previewUri = null }
    }
}

@Composable
private fun ArtistPosterDirectoryCard(
    folder: ArtistPosterFolder,
    enabled: Boolean,
    onChangeFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = if (enabled) onChangeFolder else null,
        showIndication = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(44.dp).background(
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)
                ), contentAlignment = Alignment.Center
            ) {
                Icon(MiuixIcons.AddFolder, null, tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MiuixTheme.textStyles.body1,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(folder.path, style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Text(stringResource(R.string.artist_poster_folder_change),
                style = MiuixTheme.textStyles.footnote1,
                color = if (enabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun PosterImage(file: ArtistPosterFile, revision: Long, modifier: Modifier, scale: ContentScale) {
    val context = LocalContext.current
    val request = remember(file, revision, context) {
        ImageRequest.Builder(context)
            .data(if (file.name.endsWith(".mp4", true)) file else file.uri.toUri())
            .memoryCacheKey("poster:${file.uri}:${file.lastModified}:$revision")
            .diskCacheKey("poster:${file.uri}:${file.lastModified}:$revision")
            .build()
    }
    var failed by remember(file, revision) { mutableStateOf(false) }
    var loading by remember(file, revision) { mutableStateOf(true) }
    Box(modifier.background(MiuixTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        AsyncImage(request, file.name, Modifier.fillMaxSize(), contentScale = scale,
            onSuccess = { loading = false; failed = false }, onError = { loading = false; failed = true })
        if (loading) CircularProgressIndicator(size = 24.dp)
        if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(MiuixIcons.Image, null)
            Text(stringResource(R.string.artist_poster_preview_error), fontSize = MiuixTheme.textStyles.body2.fontSize)
        }
    }
}

@Composable
private fun PosterPreview(files: List<ArtistPosterFile>, initialPage: Int, revision: Long, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { files.size })
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(MiuixIcons.Back, stringResource(R.string.artist_poster_back), tint = Color.White) }
                Text(files[pager.currentPage].name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Text("${pager.currentPage + 1} / ${files.size}", color = Color.White, modifier = Modifier.padding(start = 12.dp))
            }
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth().weight(1f), key = { files[it].uri }) { page ->
                val file = files[page]
                if (file.name.endsWith(".mp4", true) && page == pager.currentPage) {
                    var failed by remember(file) { mutableStateOf(false) }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AndroidView(
                            factory = { context ->
                                VideoView(context).apply {
                                    setVideoURI(file.uri.toUri())
                                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                                    setOnPreparedListener { start() }
                                    setOnErrorListener { _, _, _ -> failed = true; true }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            onRelease = { it.stopPlayback() }
                        )
                        if (failed) Text(stringResource(R.string.artist_poster_preview_error), color = Color.White)
                    }
                } else {
                    var zoom by remember(file) { mutableFloatStateOf(1f) }
                    var offset by remember(file) { mutableStateOf(Offset.Zero) }
                    val transform = rememberTransformableState { zoomChange, pan, _ ->
                        zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                        offset = if (zoom > 1f) offset + pan else Offset.Zero
                    }
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))) {
                        PosterImage(file, revision, Modifier.fillMaxSize()
                            .transformable(transform, canPan = { zoom > 1f })
                            .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y },
                            ContentScale.Fit)
                    }
                }
            }
        }
    }
}
