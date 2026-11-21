package com.lonx.lyrico.data.artwork.coil

import com.lonx.lyrico.data.artwork.readArtwork
import com.lonx.lyrico.data.artwork.ArtistPosterReader
import android.content.ContentResolver
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.lonx.lyrico.data.artwork.CoverRequest
import okio.Buffer

class AudioCoverFetcher(
    private val contentResolver: ContentResolver,
    private val request: CoverRequest,
    private val posterReader: ArtistPosterReader,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val pictureBytes = request.readArtwork(contentResolver, posterReader)?.bytes ?: return null

        val buffer = Buffer().apply { write(pictureBytes) }
        val imageSource = ImageSource(buffer, options.fileSystem)

        return SourceFetchResult(
            source = imageSource,
            mimeType = "image/*",
            dataSource = DataSource.DISK
        )
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
