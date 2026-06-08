package com.lonx.lyrico.data.song.file

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import java.io.File
import java.io.FileNotFoundException

class AudioFileAccess(
    private val context: Context
) {
    fun getDisplayName(uriString: String): String {
        val uri = uriString.toUri()
        return when (uri.scheme) {
            "content" -> getOpenableDisplayName(uri)
            "file" -> uri.path?.let(::File)?.name
            else -> File(uriString).name
        }?.takeIf { it.isNotBlank() } ?: uriString.substringAfterLast("/")
    }

    fun openReadableDescriptor(uriString: String): ParcelFileDescriptor? {
        val uri = uriString.toUri()
        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val file = if (uri.scheme == "file") {
                File(uri.path ?: throw FileNotFoundException(uriString))
            } else {
                File(uriString)
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        return openContentDescriptor(uri, "r")
    }

    fun openWritableDescriptor(uriString: String): ParcelFileDescriptor? {
        val uri = uriString.toUri()
        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val file = if (uri.scheme == "file") {
                File(uri.path ?: throw FileNotFoundException(uriString))
            } else {
                File(uriString)
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
        }

        return openContentDescriptor(uri, "rw")
    }

    fun createWritePermissionIntentSender(uriString: String): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return try {
            val uri = uriString.toUri()
            val writableUri = uri.toWritableMediaStoreUri() ?: return null

            MediaStore.createWriteRequest(context.contentResolver, listOf(writableUri))
                .intentSender
        } catch (e: Exception) {
            Log.w(TAG, "Unable to create write permission request: $uriString", e)
            null
        }
    }

    fun writePermissionFromThrowable(uriString: String, throwable: Throwable): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && throwable is RecoverableSecurityException) {
            return throwable.userAction.actionIntent.intentSender
        }
        if (throwable is SecurityException) {
            return createWritePermissionIntentSender(uriString)
        }
        return null
    }

    fun deleteDocument(uri: Uri): Boolean {
        return DocumentsContract.deleteDocument(context.contentResolver, uri)
    }

    fun renameDocument(uri: Uri, newFileName: String): Uri? {
        return DocumentsContract.renameDocument(context.contentResolver, uri, newFileName)
    }

    fun isUriMissing(uri: Uri): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { }
            false
        } catch (e: FileNotFoundException) {
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            true
        }
    }

    fun openInputBytes(uri: Uri): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    private fun openContentDescriptor(uri: Uri, mode: String): ParcelFileDescriptor? {
        val mediaStoreAudioUri = uri.toMediaStoreAudioUri()
        if (mediaStoreAudioUri != null && mediaStoreAudioUri != uri) {
            tryOpenContentDescriptor(mediaStoreAudioUri, mode)?.let { return it }
        }

        tryOpenContentDescriptor(uri, mode)?.let { return it }

        if (mediaStoreAudioUri == null && uri.scheme == "content") {
            findMediaStoreAudioUri(uri)?.let { matchedUri ->
                tryOpenContentDescriptor(matchedUri, mode)?.let { return it }
            }
        }

        return null
    }

    private fun tryOpenContentDescriptor(uri: Uri, mode: String): ParcelFileDescriptor? {
        return try {
            context.contentResolver.openFileDescriptor(uri, mode)
        } catch (e: SecurityException) {
            null
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun Uri.toWritableMediaStoreUri(): Uri? {
        return toMediaStoreAudioUri()
            ?: takeIf { scheme == "content" }?.let(::findMediaStoreAudioUri)
    }

    private fun findMediaStoreAudioUri(sourceUri: Uri): Uri? {
        sourceUri.toMediaStoreAudioUri()?.let { return it }
        if (sourceUri.scheme != "content") return null

        val displayName = getOpenableDisplayName(sourceUri)
            ?: sourceUri.lastPathSegment?.let(Uri::decode)
            ?: return null
        val sourceSize = getOpenableSize(sourceUri)
        val sourceRelativePath = inferRelativePath(sourceUri)

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val selection = buildString {
            append("${MediaStore.Audio.Media.DISPLAY_NAME} = ?")
            if (sourceSize != null) {
                append(" AND ${MediaStore.Audio.Media.SIZE} = ?")
            }
        }
        val selectionArgs = buildList {
            add(displayName)
            sourceSize?.let { add(it.toString()) }
        }.toTypedArray()

        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val relativePathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    -1
                }

                var fallbackUri: Uri? = null
                while (cursor.moveToNext()) {
                    val mediaUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idIndex)
                    )
                    if (fallbackUri == null) fallbackUri = mediaUri

                    if (
                        sourceRelativePath != null &&
                        relativePathIndex >= 0 &&
                        cursor.getString(relativePathIndex)?.trimEnd('/') == sourceRelativePath.trimEnd('/')
                    ) {
                        return@use mediaUri
                    }
                }
                fallbackUri
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to match MediaStore audio uri: $sourceUri", e)
            null
        }
    }

    private fun Uri.toMediaStoreAudioUri(): Uri? {
        if (scheme != "content" || authority != "media") return null
        val id = runCatching { ContentUris.parseId(this) }.getOrNull()
            ?.takeIf { it >= 0L }
            ?: return null

        return when {
            isMediaStoreAudioItemUri(this) -> this
            isMediaStoreFilesItemUri(this) -> ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id
            )
            else -> null
        }
    }

    private fun isMediaStoreAudioItemUri(uri: Uri): Boolean {
        if (!isMediaStoreItemUri(uri)) return false
        return uri.pathSegments.any { it == "audio" }
    }

    private fun isMediaStoreFilesItemUri(uri: Uri): Boolean {
        if (!isMediaStoreItemUri(uri)) return false
        return uri.pathSegments.any { it == "file" }
    }

    private fun isMediaStoreItemUri(uri: Uri): Boolean {
        if (uri.scheme != "content" || uri.authority != "media") return false
        return runCatching { ContentUris.parseId(uri) >= 0L }.getOrDefault(false)
    }

    private fun getOpenableDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.takeIf { it.isNotBlank() } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getOpenableSize(uri: Uri): Long? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun inferRelativePath(uri: Uri): String? {
        val documentRelativePath = inferDocumentRelativePath(uri)
        if (documentRelativePath != null) return documentRelativePath

        val pathSegments = uri.pathSegments
        val externalRootIndex = pathSegments.indexOfFirst {
            it == "external_files" || it == "external" || it == "external_storage"
        }
        if (externalRootIndex < 0 || pathSegments.size <= externalRootIndex + 2) return null

        return pathSegments
            .drop(externalRootIndex + 1)
            .dropLast(1)
            .joinToString("/")
            .takeIf { it.isNotBlank() }
            ?.let { "$it/" }
    }

    private fun inferDocumentRelativePath(uri: Uri): String? {
        return try {
            if (!DocumentsContract.isDocumentUri(context, uri)) return null

            val documentId = DocumentsContract.getDocumentId(uri)
            val relativePathWithFile = documentId.substringAfter(':', missingDelimiterValue = "")
            if (relativePathWithFile.isBlank()) return null

            relativePathWithFile.substringBeforeLast(
                delimiter = "/",
                missingDelimiterValue = ""
            ).takeIf { it.isNotBlank() }?.let { "$it/" }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val TAG = "AudioFileAccess"
    }
}
