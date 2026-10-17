package com.lonx.lyrico.ui.components.poster

import com.lonx.lyrico.ui.components.icons.LucideIcons
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import net.engawapg.lib.zoomable.ScrollGesturePropagation
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.ArtistPosterFile
import com.moriafly.salt.ui.SaltTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

@Composable
internal fun PreviewAction(icon: ImageVector, label: String, enabled: Boolean,
    background: Color, tint: Color, wide: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(width = if (wide) 96.dp else 48.dp, height = 48.dp)
            .clip(RoundedCornerShape(24.dp)).background(background)) {
        Icon(icon, label, tint = tint.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun PosterThumbnailStrip(items: List<PosterPreviewMedia>, currentPage: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
    val dragged by state.interactionSource.collectIsDraggedAsState()
    var scrubbing by remember { mutableStateOf(false) }
    val selectedPage by rememberUpdatedState(currentPage)
    val select by rememberUpdatedState(onSelect)
    val canSelect by rememberUpdatedState(enabled)
    val keys = remember(items) { items.map { it.key } }
    LaunchedEffect(dragged) { if (dragged) scrubbing = true }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { if (!it) scrubbing = false }
    }
    // Only a user's drag/fling drives the main image. Automatic centring never feeds back into selection.
    LaunchedEffect(state, keys) {
        snapshotFlow {
            if (!scrubbing || !canSelect) null else {
                val layout = state.layoutInfo
                val centre = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                val closest = layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - centre) }
                val selected = layout.visibleItemsInfo.firstOrNull { it.index == selectedPage }
                val distance = closest?.let { abs(it.offset + it.size / 2f - centre) }
                val selectedDistance = selected?.let { abs(it.offset + it.size / 2f - centre) }
                // Small hysteresis keeps changing thumbnail widths from oscillating at a boundary.
                closest?.index?.takeIf { distance != null && (selectedDistance == null || distance + 6f < selectedDistance) }
            }
        }.distinctUntilChanged().collect { index -> if (index != null && index != selectedPage) select(index) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        val sidePadding = (maxWidth / 2 - 40.dp).coerceAtLeast(0.dp)
        LaunchedEffect(keys, currentPage, scrubbing, maxWidth) {
            if (!scrubbing) state.animateScrollToItem(currentPage)
        }
        LazyRow(state = state, userScrollEnabled = enabled,
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                val selected = index == currentPage
                val width by animateDpAsState(if (selected) 64.dp else 28.dp, tween(220), label = "poster-thumbnail-width")
                val gap by animateDpAsState(if (selected) 8.dp else 2.dp, tween(220), label = "poster-thumbnail-gap")
                Box(Modifier.width(width + gap * 2).height(48.dp).clip(RoundedCornerShape(8.dp))
                    .selectable(selected = selected, enabled = enabled, role = Role.Button, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.width(width).height(40.dp).clip(RoundedCornerShape(4.dp))
                        .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                        .background(Color(0xFF202020)), contentAlignment = Alignment.Center) {
                        val context = LocalContext.current
                        val request = remember(item) {
                            ImageRequest.Builder(context).data(if (item.isVideo) ArtistPosterFile(item.uri, item.name, item.revision)
                                else item.bytes ?: item.uri.toUri()).size(192)
                                .memoryCacheKey("poster-thumb:${item.key}:${item.revision}:${item.bytes?.contentHashCode()}")
                                .diskCacheKey("poster-thumb:${item.key}:${item.revision}:${item.bytes?.contentHashCode()}").build()
                        }
                        var failed by remember(request) { mutableStateOf(false) }
                        AsyncImage(request, item.name, Modifier.fillMaxSize().padding(if (selected) 2.dp else 0.dp),
                            contentScale = ContentScale.Crop, onError = { failed = true }, onSuccess = { failed = false })
                        if (failed) Icon(LucideIcons.Image, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                        if (item.isVideo) Icon(LucideIcons.Play, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun PosterInformation(item: PosterPreviewMedia, info: PosterMediaInfo?, loading: Boolean, failed: Boolean,
    onRetry: () -> Unit, headerGesture: Modifier) {
    val context = LocalContext.current
    // Salt 用自身语义色，Miuix 保留原色板。
    val salt = LocalUiEngine.current == UiEngine.SALT
    val summaryColor = if (salt) SaltTheme.colors.subText else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().then(headerGesture), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            info?.lastModified?.let { time ->
                // Salt 侧走自身 main/sub 字号，不把 Miuix 的 fontSize 硬塞给 Salt 控件。
                if (salt) com.moriafly.salt.ui.Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                    .format(java.util.Date(time)), style = SaltTheme.textStyles.main, color = SaltTheme.colors.text)
                else Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                    .format(java.util.Date(time)), fontSize = MiuixTheme.textStyles.body1.fontSize)
            }
            if (salt) com.moriafly.salt.ui.Text(item.name, style = SaltTheme.textStyles.sub, color = SaltTheme.colors.text)
            else Text(item.name, fontSize = MiuixTheme.textStyles.body2.fontSize)
        }
        HorizontalDivider()
        when {
            loading -> CircularProgressIndicator(size = 24.dp)
            failed -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (salt) com.moriafly.salt.ui.Text(stringResource(R.string.artist_poster_read_error),
                    modifier = Modifier.weight(1f), style = SaltTheme.textStyles.main, color = SaltTheme.colors.text)
                else Text(stringResource(R.string.artist_poster_read_error), modifier = Modifier.weight(1f))
                IconButton(onClick = onRetry) { Icon(LucideIcons.RefreshCw, stringResource(R.string.refresh)) }
            }
            info != null -> {
                if (info.width > 0 && info.height > 0) InformationRow(stringResource(R.string.poster_preview_dimensions), "${info.width} × ${info.height}")
                info.size?.let { InformationRow(stringResource(R.string.label_file_size), android.text.format.Formatter.formatFileSize(context, it)) }
                InformationRow(stringResource(R.string.poster_preview_format), info.mime.substringAfter('/').uppercase(java.util.Locale.ROOT))
                HorizontalDivider()
                if (salt) com.moriafly.salt.ui.Text(stringResource(if (item.embedded) R.string.label_image_source_embedded else R.string.label_image_source_external),
                    style = SaltTheme.textStyles.sub, color = SaltTheme.colors.text)
                else Text(stringResource(if (item.embedded) R.string.label_image_source_embedded else R.string.label_image_source_external),
                    fontSize = MiuixTheme.textStyles.body2.fontSize)
                if (salt) com.moriafly.salt.ui.Text(info.source, style = SaltTheme.textStyles.sub, color = summaryColor)
                else Text(info.source, fontSize = MiuixTheme.textStyles.footnote1.fontSize, color = summaryColor)
            }
        }
    }
}

@Composable
private fun InformationRow(label: String, value: String) {
    // 标签=副文、取值=主文；Salt 用自身 sub/main，Miuix 保持原字号与色板。
    val salt = LocalUiEngine.current == UiEngine.SALT
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (salt) com.moriafly.salt.ui.Text(label, modifier = Modifier.weight(1f),
            style = SaltTheme.textStyles.sub, color = SaltTheme.colors.subText)
        else Text(label, fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
        if (salt) com.moriafly.salt.ui.Text(value, modifier = Modifier.weight(2f),
            style = SaltTheme.textStyles.sub, color = SaltTheme.colors.text)
        else Text(value, fontSize = MiuixTheme.textStyles.footnote1.fontSize, modifier = Modifier.weight(2f))
    }
}

@Composable
internal fun PosterPreviewImage(item: PosterPreviewMedia, modifier: Modifier, fillProgress: Float,
    fullImageSize: Size, infoImageSize: Size, gesturesEnabled: Boolean, transitioning: Boolean,
    selected: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    val request = remember(item, fullImageSize) {
        ImageRequest.Builder(context).data(item.bytes ?: item.uri.toUri())
            .size(fullImageSize.width.toInt().coerceAtLeast(1), fullImageSize.height.toInt().coerceAtLeast(1))
            .memoryCacheKey("poster-preview:${item.key}:${item.revision}:${item.bytes?.contentHashCode()}")
            .diskCacheKey("poster-preview:${item.key}:${item.revision}:${item.bytes?.contentHashCode()}").build()
    }
    val scale = remember(fillProgress, fullImageSize, infoImageSize) {
        object : ContentScale {
            override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
                // Both endpoints stay fixed while the visible image region shrinks.
                val fit = ContentScale.Fit.computeScaleFactor(srcSize, fullImageSize)
                val crop = ContentScale.Crop.computeScaleFactor(srcSize, infoImageSize)
                return ScaleFactor(fit.scaleX + (crop.scaleX - fit.scaleX) * fillProgress,
                    fit.scaleY + (crop.scaleY - fit.scaleY) * fillProgress)
            }
        }
    }
    var sourceSize by remember(request) { mutableStateOf(Size.Zero) }
    var viewportSize by remember { mutableStateOf(Size.Zero) }
    val zoomState = rememberZoomState(maxScale = 5f)
    val informationLayout = fillProgress > 0f
    LaunchedEffect(sourceSize, viewportSize, fillProgress == 1f) {
        // In information mode the visible crop fills the viewport. In normal mode,
        // use the image's intrinsic dimensions so letterboxing is not treated as image content.
        zoomState.setContentSize(if (fillProgress == 1f) viewportSize else sourceSize)
    }
    LaunchedEffect(selected, informationLayout) {
        if (!selected || informationLayout) zoomState.reset()
    }
    var failed by remember(request) { mutableStateOf(false) }
    var loading by remember(request) { mutableStateOf(true) }
    Box(modifier.onSizeChanged { viewportSize = it.toSize() }
        .zoomable(zoomState = zoomState, zoomEnabled = gesturesEnabled && !transitioning && !loading && !failed,
            scrollGesturePropagation = ScrollGesturePropagation.ContentEdge,
            enableOneFingerZoom = false, onTap = { onTap() }), contentAlignment = Alignment.Center) {
        AsyncImage(request, item.name, Modifier.fillMaxSize(), contentScale = scale,
            onSuccess = { sourceSize = it.painter.intrinsicSize; loading = false; failed = false },
            onError = { loading = false; failed = true })
        if (loading) CircularProgressIndicator()
        if (failed) Text(stringResource(R.string.artist_poster_preview_error), color = Color.White)
    }
}
