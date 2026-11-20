package com.lonx.lyrico.ui.components.cover

import com.lonx.lyrico.ui.components.image.PreviewableImage

import com.lonx.lyrico.ui.components.icons.LucideIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.data.model.artwork.CoverCandidate
import com.lonx.lyrico.data.model.artwork.CoverRequest
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.theme.LyricoColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CoverImage(
    uri: String?,
    lastModified: Long,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    contentDescription: String? = null,
    pictureType: AudioPictureType = AudioPictureType.FrontCover,
    fallbackPictureTypes: List<AudioPictureType> = emptyList(),
    fallbackToAny: Boolean = pictureType == AudioPictureType.FrontCover,
    candidates: List<CoverCandidate> = emptyList(),
    artistName: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    previewEnabled: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null
) {
    val posterSource = rememberArtistPosterSource(enabled = artistName != null)
    val hasSource = (!uri.isNullOrBlank() && uri != "0") || artistName != null
    val request = remember(
        uri,
        lastModified,
        pictureType,
        fallbackPictureTypes,
        fallbackToAny,
        candidates,
        artistName,
        posterSource
    ) {
        CoverRequest(
            uri = (uri ?: "").toUri(),
            lastUpdate = lastModified,
            pictureType = pictureType,
            fallbackPictureTypes = fallbackPictureTypes,
            fallbackToAny = fallbackToAny,
            candidates = candidates,
            artistName = artistName,
            artistPosterFolders = listOfNotNull(posterSource.folder),
            artistPosterRevision = posterSource.revision
        )
    }
    // An artist without embedded artwork and without a matching poster has nothing to show,
    // so keep the placeholder icon instead of an empty box.
    var loadFailed by remember(request) { mutableStateOf(false) }

    val salt = LocalUiEngine.current == UiEngine.SALT
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (salt) LyricoColors.coverPlaceholder else MiuixTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!hasSource || loadFailed) {
            Icon(
                imageVector = LucideIcons.Image,
                contentDescription = contentDescription,
                tint = if (salt) LyricoColors.coverPlaceholderIcon
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(26.dp)
            )
        }
        if (hasSource) {
            PreviewableImage(
                model = request,
                previewEnabled = previewEnabled,
                onClick = onClick,
                onClickLabel = onClickLabel,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                onSuccess = { loadFailed = false },
                onError = { loadFailed = true }
            )
        }
    }
}
