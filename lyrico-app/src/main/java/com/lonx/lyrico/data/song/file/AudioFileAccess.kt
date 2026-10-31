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

    fun openReadableDescriptor(uriString: String, trace: (String) -> Unit = {}): ParcelFileDescriptor? {
        val uri = uriString.toUri()
        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val file = if (uri.scheme == "file") {
                File(uri.path ?: throw FileNotFoundException(uriString))
            } else {
                File(uriString)
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        return openContentDescriptor(uri, "r", trace)
    }

    fun openWritableDescriptor(uriString: String, trace: (String) -> Unit = {}): ParcelFileDescriptor? {
        val uri = uriString.toUri()
        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val file = if (uri.scheme == "file") {
                File(uri.path ?: throw FileNotFoundException(uriString))
            } else {
                File(uriString)
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
        }

        return openContentDescriptor(uri, "rw", trace)
    }

    fun createWritePermissionIntentSender(uriString: String, trace: (String) -> Unit = {}): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return try {
            val uri = uriString.toUri()
            val writableUri = uri.toWritableMediaStoreUri()
            trace("permissionMapping original=$uri mapped=$writableUri")
            if (writableUri == null) return null

            MediaStore.createWriteRequest(context.contentResolver, listOf(writableUri))
                .intentSender
        } catch (e: Exception) {
            trace("permissionRequest error=${e.stackTraceToString()}")
            Log.w(TAG, "Unable to create write permission request: $uriString", e)
            null
        }
    }

    fun writePermissionFromThrowable(uriString: String, throwable: Throwable, trace: (String) -> Unit = {}): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && throwable is RecoverableSecurityException) {
            trace("permissionRequest recoverableSecurityException uri=$uriString")
            return throwable.userAction.actionIntent.intentSender
        }
        if (throwable is SecurityException) {
            return createWritePermissionIntentSender(uriString, trace)
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

    private fun openContentDescriptor(uri: Uri, mode: String, trace: (String) -> Unit = {}): ParcelFileDescriptor? {
        var originalFailure: Exception? = null
        var mappedFailure: Exception? = null
        fun attempt(target: Uri, mapped: Boolean = false): ParcelFileDescriptor? =
            tryOpenContentDescriptor(target, mode, trace) { error ->
                if (mapped) mappedFailure = error else originalFailure = error
            }
        val mediaStoreAudioUri = uri.toMediaStoreAudioUri()
        if (mediaStoreAudioUri != null && mediaStoreAudioUri != uri) {
            attempt(mediaStoreAudioUri, mapped = true)?.let { return it }
        }

        attempt(uri)?.let { return it }

        if (mediaStoreAudioUri == null && uri.scheme == "content") {
            findMediaStoreAudioUri(uri)?.let { matchedUri ->
                trace("descriptorMapping original=$uri matched=$matchedUri")
                attempt(matchedUri, mapped = true)?.let { return it }
            }
        }

        // Authorization applies to the mapped MediaStore item. Preserve its actual failure,
        // rather than turning every failed open (including missing files) into a permission request.
        if (mode == "rw") {
            // A provider-supplied recovery action is stronger evidence than a failed inferred mapping.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (originalFailure is RecoverableSecurityException) throw originalFailure!!
                if (mappedFailure is RecoverableSecurityException) throw mappedFailure!!
            }
            throw mappedFailure ?: originalFailure
                ?: FileNotFoundException("Provider returned no writable descriptor: $uri")
        }
        return null
    }

    private fun tryOpenContentDescriptor(
        uri: Uri,
        mode: String,
        trace: (String) -> Unit,
        onFailure: (Exception) -> Unit,
    ): ParcelFileDescriptor? {
        return try {
            context.contentResolver.openFileDescriptor(uri, mode).also {
                trace("openDescriptor uri=$uri mode=$mode result=${if (it == null) "null" else "opened"}")
                if (it == null) onFailure(FileNotFoundException("Provider returned no descriptor: $uri"))
            }
        } catch (e: SecurityException) {
            onFailure(e)
            trace("openDescriptor uri=$uri mode=$mode error=${e.stackTraceToString()}")
            null
        } catch (e: FileNotFoundException) {
            onFailure(e)
            trace("openDescriptor uri=$uri mode=$mode error=${e.stackTraceToString()}")
            null
        } catch (e: IllegalArgumentException) {
            onFailure(e)
            trace("openDescriptor uri=$uri mode=$mode error=${e.stackTraceToString()}")
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
        // A name alone cannot identify an external file safely enough for editing.
        if (sourceSize == null && sourceRelativePath == null) return null

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

                var matchedUri: Uri? = null
                while (cursor.moveToNext()) {
                    val mediaUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idIndex)
                    )
                    if (sourceRelativePath != null) {
                        if (relativePathIndex < 0 ||
                            cursor.getString(relativePathIndex)?.trimEnd('/') != sourceRelativePath.trimEnd('/')) {
                            continue
                        }
                    }
                    // Do not choose an arbitrary copy when multiple items match.
                    if (matchedUri != null) return@use null
                    matchedUri = mediaUri
                }
                matchedUri
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
                MediaStore.Audio.Media.getContentUri(pathSegments.first()),
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
