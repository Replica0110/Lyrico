package com.lonx.lyrico.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File

/** Creates a new export in Download/Lyrico using existing access only. Call on an IO thread. */
internal object DownloadsFileWriter {
    fun canWrite(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun write(context: Context, fileName: String, mimeType: String, bytes: ByteArray): Uri {
        require(fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName) { "Invalid export filename" }
        check(canWrite(context)) { "Downloads access is unavailable" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Lyrico/")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = requireNotNull(resolver.insert(collection, values)) { "Unable to create download" }
            try {
                requireNotNull(resolver.openOutputStream(uri, "w")) { "Unable to open download" }.use {
                    it.write(bytes)
                }
                val published = resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }, null, null)
                check(published > 0) { "Unable to publish download" }
                return uri
            } catch (error: Exception) {
                runCatching { resolver.delete(uri, null, null) }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }

        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Lyrico")
        check(folder.isDirectory || folder.mkdirs()) { "Unable to create export folder" }
        val file = File(folder, fileName)
        check(file.createNewFile()) { "Export file already exists" }
        try {
            file.outputStream().use { it.write(bytes) }
            return file.toUri()
        } catch (error: Exception) {
            if (!file.delete()) error.addSuppressed(IllegalStateException("Unable to remove incomplete export"))
            throw error
        }
    }
}
