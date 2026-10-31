package com.lonx.lyrico.utils.coil

import com.lonx.lyrico.data.artwork.readPosterBytes
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import androidx.core.net.toUri
import com.lonx.lyrico.data.model.artist.ArtistPosterFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer

class PosterFileFetcherFactory : Fetcher.Factory<ArtistPosterFile> {
    override fun create(data: ArtistPosterFile, options: Options, imageLoader: ImageLoader): Fetcher =
        object : Fetcher {
            override suspend fun fetch() = withContext(Dispatchers.IO) {
                val bytes = readPosterBytes(options.context, data.uri.toUri(), data.name)
                    ?: return@withContext null
                SourceFetchResult(
                    source = ImageSource(Buffer().write(bytes), options.fileSystem),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK
                )
            }
        }
}
