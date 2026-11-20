package com.lonx.lyrico.data.artwork

import android.content.ContentResolver
import android.net.Uri
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.lyrico.domain.poster.ResolvedArtistPoster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The actual image and its origin, shared by thumbnails, the viewer and image actions. */
data class ArtworkData(val bytes: ByteArray, val uri: Uri, val embedded: Boolean)

suspend fun CoverRequest.readArtwork(
    resolver: ContentResolver,
    posterReader: ArtistPosterReader,
): ArtworkData? = withContext(Dispatchers.IO) {
    val candidates = candidates.takeIf { it.isNotEmpty() } ?: listOf(CoverCandidate(uri, lastUpdate))

    suspend fun requested(includeOtherArtists: Boolean = false): ArtworkData? {
        for (candidate in candidates) {
            val bytes = readArtworkSafely {
                resolver.openFileDescriptor(candidate.uri, "r")?.use { pfd ->
                    AudioTagReader.readPicture(pfd, pictureType = pictureType,
                        fallbackPictureTypes = fallbackPictureTypes, fallbackToAny = includeOtherArtists,
                        description = artistName)
                }
            }
            if (bytes != null && bytes.isNotEmpty()) return ArtworkData(bytes, candidate.uri, embedded = true)
        }
        return null
    }

    suspend fun fallback(): ArtworkData? {
        if (!fallbackToAny) return null
        val candidate = candidates.firstOrNull() ?: return null
        val bytes = readArtworkSafely {
            resolver.openFileDescriptor(candidate.uri, "r")?.use { pfd ->
                AudioTagReader.readPicture(pfd, pictureType = AudioPictureType.FrontCover, fallbackToAny = true)
            }
        }
        return bytes?.takeIf { it.isNotEmpty() }?.let { ArtworkData(it, candidate.uri, embedded = true) }
    }

    if (artistName != null) {
        when (val poster = posterReader.resolve(this@readArtwork)) {
            is ResolvedArtistPoster.File -> ArtworkData(poster.bytes, Uri.parse(poster.uri), embedded = false)
            is ResolvedArtistPoster.Embedded -> ArtworkData(poster.bytes, Uri.parse(poster.songUri), embedded = true)
            null -> if (!skipEmbeddedPictures && fallbackToAny) requested(true) ?: fallback() else null
        }
    } else requested() ?: fallback()
}
