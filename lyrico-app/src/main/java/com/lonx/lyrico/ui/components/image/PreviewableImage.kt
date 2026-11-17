package com.lonx.lyrico.ui.components.image

import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.lonx.lyrico.R
import com.lonx.lyrico.data.artwork.ArtistPosterReader

private data class ImageViewerSession(val items: List<ImageViewerItem>, val initialPage: Int)

/** A thumbnail with the same click action and an optional full-image long press. */
@Composable
fun PreviewableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: Painter? = null,
    error: Painter? = null,
    previewEnabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    viewerItems: List<ImageViewerItem>? = null,
    viewerInitialPage: Int = 0,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
) {
    var loaded by remember(model) { mutableStateOf(false) }
    var session by remember(model, previewEnabled) { mutableStateOf<ImageViewerSession?>(null) }
    val name = contentDescription ?: stringResource(R.string.cd_cover)
    val candidate = remember(model, name, previewEnabled) { if (previewEnabled) ImageViewerItem.from(model, name) else null }
    val context = LocalContext.current
    val reader: ArtistPosterReader? = if (previewEnabled) org.koin.compose.koinInject() else null
    val item by produceState<ImageViewerItem?>(null, candidate, loaded) {
        value = null
        if (loaded && candidate != null && reader != null) value = candidate.resolveForPreview(context, reader)
    }
    val canPreview = previewEnabled && loaded && item != null
    fun openViewer() {
        val selected = item?.takeIf { canPreview } ?: return
        val gallery = viewerItems?.takeIf { it.isNotEmpty() } ?: listOf(selected)
        session = ImageViewerSession(gallery, viewerInitialPage.coerceIn(gallery.indices))
    }
    val viewLabel = stringResource(R.string.image_viewer_view)
    val gesture = if (canPreview || onClick != null) Modifier.combinedClickable(
        onClick = { if (onClick != null) onClick() else openViewer() },
        onClickLabel = if (onClick != null) onClickLabel else viewLabel,
        onLongClickLabel = viewLabel,
        onLongClick = if (canPreview) (::openViewer) else null,
    ) else Modifier
    AsyncImage(model = model, contentDescription = contentDescription,
        modifier = modifier.then(gesture), contentScale = contentScale, placeholder = placeholder, error = error,
        onLoading = { loaded = false },
        onSuccess = { loaded = true; onSuccess?.invoke(it) },
        onError = { loaded = false; onError?.invoke(it) })
    session?.let {
        ImageViewer(items = it.items, initialPage = it.initialPage, onDismiss = { session = null })
    }
}
