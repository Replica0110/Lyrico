package com.lonx.lyrico.utils.coil

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.lyrico.ui.components.CoverCandidate
import com.lonx.lyrico.ui.components.CoverRequest
import okio.Buffer

class AudioCoverFetcher(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val pictureType: AudioPictureType,
    private val fallbackPictureTypes: List<AudioPictureType>,
    private val fallbackToAny: Boolean,
    private val candidates: List<CoverCandidate>,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val candidateList = candidates.takeIf { it.isNotEmpty() }
            ?: listOf(CoverCandidate(uri, 0L))

        val pictureBytes = readRequestedPicture(candidateList)
            ?: readFallbackPicture(candidateList)
            ?: return null

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
        candidates: List<CoverCandidate>
    ): ByteArray? {
        for (candidate in candidates) {
            val bytes = contentResolver.openFileDescriptor(candidate.uri, "r")?.use { pfd ->
                AudioTagReader.readPicture(
                    pfd = pfd,
                    pictureType = pictureType,
                    fallbackPictureTypes = fallbackPictureTypes,
                    fallbackToAny = false
                )
            }
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }
        return null
    }

    private suspend fun readFallbackPicture(
        candidates: List<CoverCandidate>
    ): ByteArray? {
        if (!fallbackToAny) return null
        val firstCandidate = candidates.firstOrNull() ?: return null
        return contentResolver.openFileDescriptor(firstCandidate.uri, "r")?.use { pfd ->
            AudioTagReader.readPicture(
                pfd = pfd,
                pictureType = AudioPictureType.FrontCover,
                fallbackToAny = true
            )
        }
    }

    class Factory(private val contentResolver: ContentResolver) :
        Fetcher.Factory<CoverRequest> {
        override fun create(
            data: CoverRequest,
            options: Options,
            imageLoader: ImageLoader
        ) = AudioCoverFetcher(
            contentResolver = contentResolver,
            uri = data.uri,
            pictureType = data.pictureType,
            fallbackPictureTypes = data.fallbackPictureTypes,
            fallbackToAny = data.fallbackToAny,
            candidates = data.candidates,
            options = options
        )
    }
}
