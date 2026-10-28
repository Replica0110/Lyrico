package com.lonx.lyrico.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType

/** One child of a SAF tree, as returned by [SafDocuments.children]. */
data class SafDocumentRow(
    val documentId: String,
    val displayName: String,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long,
    val uri: Uri,
    val isDirectory: Boolean
)

/**
 * 通过 [DocumentsContract] 读取 SAF 文件夹。音乐扫描与艺术家海报共用，保证看到同一批文件；
 * 不用 `DocumentFile`：它在 Provider 拒绝请求时返回空目录而不是失败。
 */
object SafDocuments {
    private const val TAG = "SafDocuments"

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    /**
     * [parentDocumentId] 的直接子项，为 `null` 时取树的根目录。Provider 无法查询（目录被删、
     * 授权失效）时返回 `null`，调用方据此区分「文件夹不可用」与「文件夹为空」。
     */
    fun children(
        context: Context,
        treeUri: Uri,
        parentDocumentId: String? = null
    ): List<SafDocumentRow>? {
        var resolvedParent = parentDocumentId
        var childrenUri: Uri? = null
        return try {
            val parentId = parentDocumentId ?: DocumentsContract.getTreeDocumentId(treeUri)
            resolvedParent = parentId
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val documentIdIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                )
                val displayNameIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                )
                val mimeTypeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                val sizeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_SIZE
                )
                val lastModifiedIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )

                buildList {
                    while (cursor.moveToNext()) {
                        val documentId = cursor.getStringOrNull(documentIdIndex) ?: continue
                        val displayName = cursor.getStringOrNull(displayNameIndex) ?: continue
                        val mimeType = cursor.getStringOrNull(mimeTypeIndex)

                        add(
                            SafDocumentRow(
                                documentId = documentId,
                                displayName = displayName,
                                mimeType = mimeType,
                                size = cursor.getLongOrZero(sizeIndex),
                                lastModified = cursor.getLongOrZero(lastModifiedIndex),
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                                isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                            )
                        )
                    }
                }
            }.also { rows ->
                if (rows == null) AppLog.record(AppLogType.APP, TAG, "SAF directory enumeration unavailable",
                    "tree=$treeUri\nparent=$resolvedParent\nquery=$childrenUri\nreason=null-cursor", treeUri.toString(), AppLogLevel.WARNING,
                    repeatedFailureKey = "saf:$childrenUri:null")
            }
        } catch (e: Exception) {
            AppLog.record(AppLogType.APP, TAG, "SAF directory enumeration failed",
                "tree=$treeUri\nparent=$resolvedParent\nquery=$childrenUri", treeUri.toString(), AppLogLevel.WARNING, e,
                repeatedFailureKey = "saf:$treeUri:$resolvedParent:${e.javaClass.name}")
            null
        }
    }
}

private fun Cursor.getStringOrNull(index: Int): String? {
    if (index < 0 || isNull(index)) return null
    return getString(index)
}

private fun Cursor.getLongOrZero(index: Int): Long {
    if (index < 0 || isNull(index)) return 0L
    return runCatching { getLong(index) }.getOrDefault(0L)
}
