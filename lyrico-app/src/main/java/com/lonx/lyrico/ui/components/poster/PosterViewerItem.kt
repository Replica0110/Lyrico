package com.lonx.lyrico.ui.components.poster

import com.lonx.lyrico.domain.poster.ResolvedArtistPoster
import com.lonx.lyrico.ui.components.image.ImageViewerItem
import com.lonx.lyrico.ui.components.image.ImageViewerSource

fun ResolvedArtistPoster.viewerItem(): ImageViewerItem = when (this) {
    is ResolvedArtistPoster.File -> ImageViewerItem(uri, name,
        if (canCrop) ImageViewerSource.Bytes(bytes, uri) else ImageViewerSource.Location(uri), isVideo = !canCrop)
    is ResolvedArtistPoster.Embedded -> ImageViewerItem("embedded:$songUri:$artistName", artistName,
        ImageViewerSource.Bytes(bytes, songUri, embedded = true))
}
