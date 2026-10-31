package com.lonx.lyrico.utils.coil

import com.lonx.lyrico.data.artwork.ArtistPosterReader
import com.lonx.lyrico.data.artwork.readArtworkSafely
import android.content.ContentResolver
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.lyrico.data.model.artwork.CoverCandidate
import com.lonx.lyrico.data.model.artwork.CoverRequest
import okio.Buffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioCoverFetcher(
    private val contentResolver: ContentResolver,
    private val request: CoverRequest,
    private val posterReader: ArtistPosterReader,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val candidateList = request.candidates.takeIf { it.isNotEmpty() }
            ?: listOf(CoverCandidate(request.uri, request.lastUpdate))

        val pictureBytes = withContext(Dispatchers.IO) {
            if (request.artistName != null) {
                posterReader.resolve(request)?.bytes
                    // Covers and other artists' images are display fallbacks, never edit targets.
                    ?: if (!request.skipEmbeddedPictures && request.fallbackToAny) {
                        readRequestedPicture(candidateList, includeOtherArtists = true)
                            ?: readFallbackPicture(candidateList)
                    } else null
            } else {
                readRequestedPicture(candidateList)
                    ?: readFallbackPicture(candidateList)
            }
        } ?: return null

        if (pictureBytes.isEmpty()) {
            return null
        }


        val buffer = Buffer().apply { write(pictureBytes) }
        val imageSource = ImageSource(buffer, options.fileSystem)

        return SourceFetchResult(
            source = imageSource,
            mimeType = "image/*",
            dataSource = DataSource.DISK
        )
    }

    private suspend fun readRequestedPicture(
        candidates: List<CoverCandidate>,
        includeOtherArtists: Boolean = false
    ): ByteArray? {
        for (candidate in candidates) {
            val bytes = readArtworkSafely {
                contentResolver.openFileDescriptor(candidate.uri, "r")?.use { pfd ->
                    AudioTagReader.readPicture(
                        pfd = pfd,
                        pictureType = request.pictureType,
                        fallbackPictureTypes = request.fallbackPictureTypes,
                        fallbackToAny = includeOtherArtists,
                        description = request.artistName
                    )
                }
            }
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }
        return null
    }

    private suspend fun readFallbackPicture(
        candidates: List<CoverCandidate>
    ): ByteArray? {
        if (!request.fallbackToAny) return null
        val firstCandidate = candidates.firstOrNull() ?: return null
        return readArtworkSafely {
            contentResolver.openFileDescriptor(firstCandidate.uri, "r")?.use { pfd ->
                AudioTagReader.readPicture(
                    pfd = pfd,
                    pictureType = AudioPictureType.FrontCover,
                    fallbackToAny = true
                )
            }
        }
    }

    class Factory(
        private val contentResolver: ContentResolver,
        private val posterReader: ArtistPosterReader
    ) :
        Fetcher.Factory<CoverRequest> {
        override fun create(
            data: CoverRequest,
            options: Options,
            imageLoader: ImageLoader
        ) = AudioCoverFetcher(
            contentResolver = contentResolver,
            posterReader = posterReader,
            request = data,
            options = options
        )
    }
}
