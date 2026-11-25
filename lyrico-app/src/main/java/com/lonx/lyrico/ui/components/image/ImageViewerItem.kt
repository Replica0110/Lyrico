package com.lonx.lyrico.ui.components.image

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.lonx.lyrico.data.artwork.ArtistPosterReader
import com.lonx.lyrico.data.artwork.readArtwork
import com.lonx.lyrico.data.artwork.validArtwork
import com.lonx.lyrico.data.artwork.CoverRequest
import com.lonx.lyrico.data.song.tag.ImageBytesFetcher
import com.lonx.lyrico.data.song.tag.PictureSource
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ImageViewerSource {
    data class Location(val uri: String) : ImageViewerSource
    data class Bytes(val data: ByteArray, val origin: String? = null, val embedded: Boolean = false) : ImageViewerSource
    data class Artwork(val request: CoverRequest) : ImageViewerSource
}

data class ImageViewerItem(
    val key: String,
    val name: String,
    val source: ImageViewerSource,
    val isVideo: Boolean = false,
    val revision: Long = 0,
) {
    init {
        require(!isVideo || source is ImageViewerSource.Location) { "Videos require a file URI" }
    }

    internal val model: Any get() = when (source) {
        is ImageViewerSource.Location -> source.uri.toUri()
        is ImageViewerSource.Bytes -> source.data
        is ImageViewerSource.Artwork -> source.request
    }
    internal val cacheKey: String get() = "$key:$revision:" + when (source) {
        is ImageViewerSource.Location -> source.uri
        is ImageViewerSource.Bytes -> source.data.contentHashCode().toString()
        is ImageViewerSource.Artwork -> source.request.toString()
    }
    internal val videoUri: String get() = (source as ImageViewerSource.Location).uri

    companion object {
        /** Convert the image models used by the app's thumbnails. */
        fun from(model: Any?, name: String): ImageViewerItem? = when (model) {
            is CoverRequest -> ImageViewerItem("artwork:${model.uri}:${model.artistName}:${model.pictureType}", name,
                // An artist's display fallback is not that artist's poster.
                ImageViewerSource.Artwork(if (model.artistName != null) model.copy(fallbackToAny = false) else model))
            is ByteArray -> model.takeIf { it.isNotEmpty() }?.let {
                ImageViewerItem("bytes:${it.contentHashCode()}", name, ImageViewerSource.Bytes(it))
            }
            is Uri -> from(model.toString(), name)
            is File -> from(Uri.fromFile(model), name)
            is String -> model.takeIf { it.isNotBlank() && it != "0" }?.let {
                ImageViewerItem(it, name, ImageViewerSource.Location(it))
            }
            else -> null
        }
    }
}

/** Resolve actual artwork before exposing it as a preview or including it in a gallery. */
internal suspend fun ImageViewerItem.resolveForPreview(context: Context, reader: ArtistPosterReader): ImageViewerItem? =
    withContext(Dispatchers.IO) {
        try {
            when (val source = source) {
                is ImageViewerSource.Artwork -> {
                    val request = if (source.request.artistName != null) source.request.copy(fallbackToAny = false) else source.request
                    val data = request.readArtwork(context.contentResolver, reader) ?: return@withContext null
                    if (data.bytes.validArtwork() == null) return@withContext null
                    copy(source = ImageViewerSource.Bytes(data.bytes, data.uri.toString(), data.embedded))
                }
                is ImageViewerSource.Bytes -> this@resolveForPreview.takeIf { source.data.validArtwork() != null }
                is ImageViewerSource.Location -> this@resolveForPreview
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

private suspend fun ImageViewerItem.resolved(context: Context, reader: ArtistPosterReader): ImageViewerItem =
    requireNotNull(resolveForPreview(context, reader)) { "Image unavailable" }

private suspend fun ImageViewerItem.readBytes(fetcher: ImageBytesFetcher): ByteArray? = when (val source = source) {
    is ImageViewerSource.Bytes -> source.data
    is ImageViewerSource.Location -> fetcher.fetch(
        if (source.uri.toUri().scheme in listOf("http", "https")) PictureSource.UrlSource(source.uri)
        else PictureSource.UriSource(source.uri.toUri()))
    is ImageViewerSource.Artwork -> error("Resolve artwork before reading image bytes")
}

internal suspend fun ImageViewerItem.readBitmap(context: Context, reader: ArtistPosterReader, fetcher: ImageBytesFetcher): Bitmap? {
    val bytes = resolved(context, reader).readBytes(fetcher) ?: return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

internal enum class ImageOrigin { EMBEDDED, FILE, NETWORK, MEMORY }

internal data class ImageViewerInfo(val mime: String, val width: Int, val height: Int, val size: Long?, val source: String,
    val lastModified: Long? = null, val origin: ImageOrigin = ImageOrigin.FILE)

private val ImageViewerItem.originKind: ImageOrigin get() = when {
    (source as? ImageViewerSource.Bytes)?.embedded == true -> ImageOrigin.EMBEDDED
    origin == null -> ImageOrigin.MEMORY
    origin?.toUri()?.scheme in listOf("http", "https") -> ImageOrigin.NETWORK
    else -> ImageOrigin.FILE
}

private val ImageViewerItem.origin: String? get() = when (source) {
    is ImageViewerSource.Location -> source.uri
    is ImageViewerSource.Bytes -> source.origin
    is ImageViewerSource.Artwork -> null
}

private fun ImageViewerItem.modifiedTime(context: Context): Long? {
    if ((source as? ImageViewerSource.Bytes)?.embedded == true) return null
    val uri = origin?.toUri() ?: return null
    if (uri.scheme in listOf("http", "https")) return null
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use {
            if (!it.moveToFirst()) return@use null
            val documentColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val mediaColumn = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
            when {
                documentColumn >= 0 && !it.isNull(documentColumn) -> it.getLong(documentColumn)
                mediaColumn >= 0 && !it.isNull(mediaColumn) -> it.getLong(mediaColumn) * 1000L
                else -> null
            }?.takeIf { time -> time > 0 }
        }
    } catch (_: Exception) { null }
}

private fun ImageViewerItem.sourceLocation(context: Context): String {
    val location = origin ?: return name
    val uri = location.toUri()
    if (uri.scheme in listOf("http", "https")) return location
    return try {
        if (uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.getDocumentId(uri).substringAfter(':')
        } else {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                val column = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && it.moveToFirst()) it.getString(column) else null
            } ?: Uri.decode(location)
        }
    } catch (_: Exception) { Uri.decode(location) }
}

internal suspend fun ImageViewerItem.readInfo(context: Context, reader: ArtistPosterReader, fetcher: ImageBytesFetcher): ImageViewerInfo {
    val item = resolved(context, reader)
    val location = item.sourceLocation(context)
    val modified = item.modifiedTime(context)
    if (item.isVideo) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, item.videoUri.toUri())
            val size = context.contentResolver.query(item.videoUri.toUri(), arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
            }
            return ImageViewerInfo("video/mp4",
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                size, location, modified)
        } finally { retriever.release() }
    }
    val bytes = requireNotNull(item.readBytes(fetcher)) { "Image unavailable" }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot read image dimensions" }
    return ImageViewerInfo(bounds.outMimeType ?: "image/*", bounds.outWidth, bounds.outHeight, bytes.size.toLong(),
        location, modified, item.originKind)
}

internal suspend fun ImageViewerItem.shareIntent(context: Context, reader: ArtistPosterReader, fetcher: ImageBytesFetcher): Intent {
    val item = resolved(context, reader)
    // Read once so metadata and the shared file describe exactly the same image.
    val bytes = if (item.isVideo) null else requireNotNull(item.readBytes(fetcher))
    val sharedItem = if (bytes != null) item.copy(source = ImageViewerSource.Bytes(bytes, item.origin)) else item
    val mime = sharedItem.readInfo(context, reader, fetcher).mime
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (isVideo) "mp4" else "img"
    val directory = File(context.cacheDir, "image-shares").apply { mkdirs() }
    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }
        ?.forEach { it.delete() }
    val file = File.createTempFile("image-", ".$extension", directory)
    try {
        file.outputStream().use { output ->
            if (bytes != null) output.write(bytes)
            else requireNotNull(context.contentResolver.openInputStream(item.videoUri.toUri())).use { it.copyTo(output) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } catch (error: Exception) { file.delete(); throw error }
}
