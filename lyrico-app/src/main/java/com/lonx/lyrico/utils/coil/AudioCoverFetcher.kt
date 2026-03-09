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
import com.lonx.audiotag.rw.AudioTagReader
import okio.Buffer

class AudioCoverFetcher(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val pictureBytes = contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->

            AudioTagReader.readPicture(pfd)

        } ?: return null

        if (pictureBytes.isEmpty()) {
            return null
        }

        Log.d("AudioCoverFetcher", "TagLib picture fileSize: ${pictureBytes.size}")

        val buffer = Buffer().apply { write(pictureBytes) }
        val imageSource = ImageSource(buffer, options.fileSystem)

        return SourceFetchResult(
            source = imageSource,
            mimeType = "image/*",
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val contentResolver: ContentResolver) :
        Fetcher.Factory<CoverRequest> {
        override fun create(
            data: CoverRequest,
            options: Options,
            imageLoader: ImageLoader
        ) = AudioCoverFetcher(contentResolver, data.uri, options)
    }
}