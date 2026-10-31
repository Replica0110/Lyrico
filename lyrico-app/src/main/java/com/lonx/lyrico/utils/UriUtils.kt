package com.lonx.lyrico.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri

object UriUtils {
    private const val TAG = "UriUtils"

    /**
     * 从 SAF Tree Uri 取绝对路径，仅适用于外部存储（内置存储与 SD 卡）。
     */
    fun getFileAbsolutePath(context: Context, treeUri: Uri): String? {
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val split = docId.split(":")
            val type = split[0]
            val relativePath = if (split.size > 1) split[1] else ""

            return if ("primary".equals(type, ignoreCase = true)) {
                val primaryPath = Environment.getExternalStorageDirectory().absolutePath
                if (relativePath.isNotEmpty()) {
                    "$primaryPath/$relativePath"
                } else {
                    primaryPath
                }
            } else {
                val extStoragePaths = getExternalStoragePaths(context)
                for (path in extStoragePaths) {
                    if (path.contains(type)) {
                        return if (relativePath.isNotEmpty()) {
                            "$path/$relativePath"
                        } else {
                            path
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun hasPersistedReadPermission(context: Context, treeUriString: String?): Boolean {
        if (treeUriString.isNullOrBlank()) return false

        return try {
            val treeUri = treeUriString.toUri()
            context.contentResolver.persistedUriPermissions.any { permission ->
                permission.isReadPermission && permission.uri == treeUri
            }
        } catch (e: Exception) {
            false
        }
    }

    fun releasePersistedPermission(contentResolver: ContentResolver, treeUriString: String?): Boolean {
        if (treeUriString.isNullOrBlank()) return true

        val treeUri = try {
            treeUriString.toUri()
        } catch (e: Exception) {
            Log.w(TAG, "Invalid tree uri: $treeUriString", e)
            return true
        }

        val matchingPermissions = contentResolver.persistedUriPermissions
            .filter { permission -> isSameTreeUri(permission.uri, treeUri) }

        matchingPermissions.forEach { permission ->
            if (permission.isReadPermission) {
                releasePersistedPermissionFlag(
                    contentResolver = contentResolver,
                    uri = permission.uri,
                    flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (permission.isWritePermission) {
                releasePersistedPermissionFlag(
                    contentResolver = contentResolver,
                    uri = permission.uri,
                    flag = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }

        return contentResolver.persistedUriPermissions.none { permission ->
            isSameTreeUri(permission.uri, treeUri)
        }
    }

    private fun releasePersistedPermissionFlag(
        contentResolver: ContentResolver,
        uri: Uri,
        flag: Int
    ) {
        try {
            contentResolver.releasePersistableUriPermission(uri, flag)
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to release persisted permission: uri=$uri flag=$flag", e)
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error releasing persisted permission: uri=$uri flag=$flag", e)
        }
    }

    private fun isSameTreeUri(left: Uri, right: Uri): Boolean {
        if (left == right) return true

        return try {
            DocumentsContract.isTreeUri(left) &&
                    DocumentsContract.isTreeUri(right) &&
                    left.authority == right.authority &&
                    DocumentsContract.getTreeDocumentId(left) == DocumentsContract.getTreeDocumentId(right)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 从 MediaStore URI（content://media/...）取文件名。
     */
    fun getMediaStoreFileName(contentResolver: ContentResolver, mediaUri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME)
            contentResolver.query(
                mediaUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    cursor.getString(displayNameIndex)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取所有可能的外部存储路径（包括 SD 卡）
     */
    private fun getExternalStoragePaths(context: Context): List<String> {
        val paths = mutableListOf<String>()
        val externalFilesDirs = context.getExternalFilesDirs(null)
        for (file in externalFilesDirs) {
            if (file != null) {
                val path = file.absolutePath
                if (path.contains("/Android/data")) {
                    paths.add(path.substringBefore("/Android/data"))
                }
            }
        }
        return paths
    }
}
