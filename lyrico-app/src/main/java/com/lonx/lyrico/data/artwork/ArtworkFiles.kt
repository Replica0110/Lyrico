package com.lonx.lyrico.data.artwork

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.lonx.lyrico.platform.storage.SafSiblingFileWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Image source access and file exports. Callers own dispatching, errors and UI state. */
internal object ArtworkFiles {
    data class LocalImage(val bytes: ByteArray, val mimeType: String)

    enum class GalleryWriteResult {
        WRITTEN,
        SOURCE_UNAVAILABLE,
        DESTINATION_UNAVAILABLE
    }

    fun readLocal(context: Context, uri: Uri): LocalImage? {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        return LocalImage(bytes, mimeType)
    }

    fun encodeJpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        stream.toByteArray()
    }

    fun writeToUri(context: Context, destinationUri: Uri, source: Any): Boolean =
        context.contentResolver.openOutputStream(destinationUri, "wt")
            ?.use { outputStream -> writeSource(context, outputStream, source) }
            ?: false

    fun writeNextToAudio(context: Context, audioUri: Uri, fileName: String, source: Any) {
        SafSiblingFileWriter.write(
            context = context,
            sourceDocumentUri = audioUri,
            mimeType = "image/jpeg",
            fileName = fileName
        ) { outputStream ->
            check(writeSource(context, outputStream, source)) { "Cover source stream unavailable" }
        }
    }

    fun writeToGallery(
        context: Context,
        source: Any,
        albumName: String,
        filenamePrefix: String
    ): GalleryWriteResult {
        val filename = "${filenamePrefix}_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + albumName)
        }
        val resolver = context.contentResolver
        val destinationUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return GalleryWriteResult.DESTINATION_UNAVAILABLE
        val wrotePicture = resolver.openOutputStream(destinationUri)?.use { outputStream ->
            writeSource(context, outputStream, source)
        } ?: false
        return if (wrotePicture) GalleryWriteResult.WRITTEN else GalleryWriteResult.SOURCE_UNAVAILABLE
    }

    private fun writeSource(
        context: Context,
        outputStream: OutputStream,
        source: Any
    ): Boolean = when (getCoverSourceType(source)) {
        CoverSourceType.BYTE_ARRAY -> {
            outputStream.write(source as ByteArray)
            true
        }

        CoverSourceType.NETWORK_URL -> {
            java.net.URL(source.toString().trim()).openStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
            true
        }

        CoverSourceType.CONTENT_OR_FILE_URI,
        CoverSourceType.URI -> {
            val sourceUri = when (source) {
                is Uri -> source
                is String -> source.trim().toUri()
                else -> null
            }
            sourceUri?.let {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                    true
                }
            } ?: false
        }

        CoverSourceType.FILE_PATH -> {
            java.io.FileInputStream(source.toString().trim()).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
            true
        }

        CoverSourceType.BITMAP -> (source as Bitmap).compress(
            Bitmap.CompressFormat.JPEG,
            100,
            outputStream
        )

        CoverSourceType.UNSUPPORTED -> false
    }
}
