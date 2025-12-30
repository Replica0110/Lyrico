package com.lonx.lyrico.utils.coil

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import okio.source

class AudioCoverFetcher(
    private val context: Context,
    private val data: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, data)
            val picture = retriever.embeddedPicture ?: return null

            val buffer = Buffer().apply { write(picture) }
            return SourceResult(
                source = ImageSource(buffer, context),
                mimeType = "image/*", // The actual mime type is unknown, but it's an image.
                dataSource = DataSource.DISK
            )
        } finally {
            retriever.release()
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            // We only want to handle content uris
            if (data.scheme != ContentResolver.SCHEME_CONTENT) return null

            // Check if the mime type is audio.
            val mimeType = context.contentResolver.getType(data)
            if (mimeType?.startsWith("audio/") == true) {
                return AudioCoverFetcher(context, data)
            }

            // If mime type is null, fall back to checking the file extension.
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(data.toString())
            if (fileExtension != null && isAudioExtension(fileExtension)) {
                return AudioCoverFetcher(context, data)
            }

            // Not a file we can handle.
            return null
        }

        private fun isAudioExtension(extension: String): Boolean {
            // A basic list of audio file extensions.
            return extension.equals("mp3", ignoreCase = true) ||
                    extension.equals("m4a", ignoreCase = true) ||
                    extension.equals("wav", ignoreCase = true) ||
                    extension.equals("flac", ignoreCase = true) ||
                    extension.equals("ogg", ignoreCase = true)
        }
    }
}

