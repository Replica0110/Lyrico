package com.lonx.lyrico.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.OutputStream
import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType

object SafSiblingFileWriter {

    fun write(
        context: Context,
        sourceDocumentUri: Uri,
        mimeType: String,
        fileName: String,
        bytes: ByteArray
    ): Uri = write(context, sourceDocumentUri, mimeType, fileName) { outputStream ->
        outputStream.write(bytes)
    }

    fun write(
        context: Context,
        sourceDocumentUri: Uri,
        mimeType: String,
        fileName: String,
        writeContent: (OutputStream) -> Unit
    ): Uri {
        var stage = "resolve-parent"
        try {
            val parentDocumentId = findParentDocumentId(context, sourceDocumentUri)
                ?: throw IllegalStateException("Source folder unavailable")
            stage = "create-sibling"
            val outputUri = createOrFindSiblingFile(
                context = context,
                treeUri = sourceDocumentUri,
                parentDocumentId = parentDocumentId,
                mimeType = mimeType,
                fileName = fileName
            ) ?: throw IllegalStateException("Failed to create sibling file")

            stage = "write-output"
            context.contentResolver.openOutputStream(outputUri, "wt")?.use { outputStream ->
                writeContent(outputStream)
            } ?: throw IllegalStateException("Failed to open sibling output stream")

            AppLog.record(AppLogType.METADATA, "SiblingExport", "Sibling file exported",
                "source=$sourceDocumentUri\ndestination=$outputUri\nname=$fileName\nmime=$mimeType", sourceDocumentUri.toString(), AppLogLevel.INFO)
            return outputUri
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            AppLog.record(AppLogType.METADATA, "SiblingExport", "Sibling file export failed",
                "source=$sourceDocumentUri\nname=$fileName\nmime=$mimeType\nstage=$stage", sourceDocumentUri.toString(), AppLogLevel.ERROR, error)
            throw error
        }
    }

    internal fun findParentDocumentId(context: Context, sourceDocumentUri: Uri): String? {
        if (!DocumentsContract.isDocumentUri(context, sourceDocumentUri)) return null

        val pathParent = runCatching {
            DocumentsContract.findDocumentPath(context.contentResolver, sourceDocumentUri)
                ?.path
                ?.takeIf { it.size >= 2 }
                ?.let { it[it.lastIndex - 1] }
        }.getOrNull()
        if (pathParent != null) return pathParent

        val targetDocumentId = runCatching {
            DocumentsContract.getDocumentId(sourceDocumentUri)
        }.getOrNull() ?: return null
        val rootDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(sourceDocumentUri)
        }.getOrNull() ?: return null

        val pendingDirectories = ArrayDeque<String>()
        val visitedDirectories = mutableSetOf<String>()
        pendingDirectories.add(rootDocumentId)

        while (pendingDirectories.isNotEmpty()) {
            val parentDocumentId = pendingDirectories.removeFirst()
            if (!visitedDirectories.add(parentDocumentId)) continue

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                sourceDocumentUri,
                parentDocumentId
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val documentIdIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                )
                val mimeTypeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(documentIdIndex)
                    if (documentId == targetDocumentId) return parentDocumentId
                    if (cursor.getString(mimeTypeIndex) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pendingDirectories.add(documentId)
                    }
                }
            }
        }
        return null
    }

    private fun createOrFindSiblingFile(
        context: Context,
        treeUri: Uri,
        parentDocumentId: String,
        mimeType: String,
        fileName: String
    ): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val documentIdIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val displayNameIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            while (cursor.moveToNext()) {
                if (cursor.getString(displayNameIndex) == fileName) {
                    return DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        cursor.getString(documentIdIndex)
                    )
                }
            }
        }

        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            mimeType,
            fileName
        )
    }
}
