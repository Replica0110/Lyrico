package com.lonx.lyrico.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import com.lonx.lyrico.data.model.SongFile
import com.lonx.lyrico.data.model.entity.FolderEntity
import java.util.Locale
import kotlin.math.abs

data class SafScanResult(
    val songs: List<SafScannedSongFile>,
    val successfulFolderIds: Set<Long>,
    val failedFolderIds: Set<Long>,
    val missingFolderIds: Set<Long>,
)

data class SafScannedSongFile(
    val songFile: SongFile,
    val rootFolderId: Long,
    val folderPath: String
)

private data class SafDocumentRow(
    val documentId: String,
    val displayName: String,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long,
    val uri: Uri,
    val isDirectory: Boolean
)

class MediaScanner(
    private val context: Context,
) {

    private val tag = "MediaScanner"

    fun querySongsFromSafFolders(folders: List<FolderEntity>): SafScanResult {
        val results = mutableListOf<SafScannedSongFile>()
        val successfulFolderIds = mutableSetOf<Long>()
        val failedFolderIds = mutableSetOf<Long>()
        val missingFolderIds = mutableSetOf<Long>()
        val visitedDocumentKeys = mutableSetOf<String>()

        for (folder in folders) {
            val treeUriString = folder.treeUri

            if (treeUriString.isNullOrBlank()) {
                failedFolderIds.add(folder.id)
                continue
            }

            try {
                val treeUri = treeUriString.toUri()
                val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

                // 用一次查询验证根目录是否可访问。不要用 DocumentFile.exists()
                val rootUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    rootDocumentId
                )

                if (!canQueryDocument(rootUri)) {
                    Log.w(tag, "SAF 根目录不可访问或已被删除: ${folder.path}, uri=$treeUriString")
                    missingFolderIds.add(folder.id)
                    continue
                }

                scanTreeByDocumentsContract(
                    treeUri = treeUri,
                    rootDocumentId = rootDocumentId,
                    displayRootPath = folder.path,
                    rootFolderId = folder.id,
                    output = results,
                    visitedDocumentKeys = visitedDocumentKeys
                )

                successfulFolderIds.add(folder.id)
            } catch (e: SecurityException) {
                Log.e(tag, "扫描 SAF 文件夹权限不足: ${folder.path}", e)
                failedFolderIds.add(folder.id)
            } catch (e: Exception) {
                Log.e(tag, "扫描 SAF 文件夹失败: ${folder.path}", e)
                failedFolderIds.add(folder.id)
            }
        }

        return SafScanResult(
            songs = results,
            successfulFolderIds = successfulFolderIds,
            failedFolderIds = failedFolderIds,
            missingFolderIds = missingFolderIds
        )
    }

    private fun scanTreeByDocumentsContract(
        treeUri: Uri,
        rootDocumentId: String,
        displayRootPath: String,
        rootFolderId: Long,
        output: MutableList<SafScannedSongFile>,
        visitedDocumentKeys: MutableSet<String>
    ) {
        val stack = ArrayDeque<Pair<String, String>>()
        stack.add(rootDocumentId to "")

        while (stack.isNotEmpty()) {
            val (parentDocumentId, currentRelativePath) = stack.removeLast()

            val children = queryChildren(
                treeUri = treeUri,
                parentDocumentId = parentDocumentId
            )

            for (child in children) {
                val name = child.displayName
                if (name.isBlank()) continue

                if (child.isDirectory) {
                    if (shouldSkipDirectory(name)) continue

                    val nextRelativePath = if (currentRelativePath.isBlank()) {
                        name
                    } else {
                        "$currentRelativePath/$name"
                    }

                    stack.add(child.documentId to nextRelativePath)
                    continue
                }

                if (!isSupportedAudioFile(name, child.mimeType)) continue

                // documentId 在同一个 treeUri 下稳定；这里比 uri 字符串更适合作去重 key
                val documentKey = "${treeUri}|${child.documentId}"
                if (!visitedDocumentKeys.add(documentKey)) continue

                val filePath = buildDisplayPath(
                    rootPath = displayRootPath,
                    relativePath = currentRelativePath,
                    fileName = name
                )

                output.add(
                    SafScannedSongFile(
                        rootFolderId = rootFolderId,
                        folderPath = buildDisplayFolderPath(
                            rootPath = displayRootPath,
                            relativePath = currentRelativePath
                        ),
                        songFile = SongFile(
                            mediaId = createVirtualMediaId(child.uri.toString()),
                            uri = child.uri,
                            filePath = filePath,
                            fileName = name,
                            lastModified = child.lastModified,
                            dateAdded = child.lastModified,
                            duration = 0L,
                            fileSize = child.size
                        )
                    )
                )
            }
        }
    }

    private fun queryChildren(
        treeUri: Uri,
        parentDocumentId: String
    ): List<SafDocumentRow> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val result = mutableListOf<SafDocumentRow>()

        try {
            context.contentResolver.query(
                childrenUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
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

                while (cursor.moveToNext()) {
                    val documentId = cursor.getStringOrNull(documentIdIndex)
                        ?: continue
                    val displayName = cursor.getStringOrNull(displayNameIndex)
                        ?: continue
                    val mimeType = cursor.getStringOrNull(mimeTypeIndex)
                    val size = cursor.getLongOrZero(sizeIndex)
                    val lastModified = cursor.getLongOrZero(lastModifiedIndex)

                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        documentId
                    )

                    result.add(
                        SafDocumentRow(
                            documentId = documentId,
                            displayName = displayName,
                            mimeType = mimeType,
                            size = size,
                            lastModified = lastModified,
                            uri = documentUri,
                            isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "读取 SAF 子文件失败: parentDocumentId=$parentDocumentId", e)
        }

        return result
    }

    private fun canQueryDocument(documentUri: Uri): Boolean {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        return try {
            context.contentResolver.query(
                documentUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } == true
        } catch (e: Exception) {
            false
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

    private val supportedAudioExtensions = setOf(
        "mp3", "flac", "m4a", "ogg", "opus", "wav", "aac", "wma", "ape"
    )

    private fun shouldSkipDirectory(name: String): Boolean {
        val normalized = name.lowercase(Locale.ROOT)
        return normalized.startsWith(".") ||
                normalized == "android" ||
                normalized == "data" ||
                normalized == "obb" ||
                normalized == "cache" ||
                normalized == "tmp"
    }

    private fun isSupportedAudioFile(fileName: String, mimeType: String?): Boolean {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)

        return extension in supportedAudioExtensions ||
                mimeType?.startsWith("audio/") == true
    }

    private fun buildDisplayPath(
        rootPath: String,
        relativePath: String,
        fileName: String
    ): String {
        return if (relativePath.isBlank()) {
            "${rootPath.trimEnd('/')}/$fileName"
        } else {
            "${rootPath.trimEnd('/')}/$relativePath/$fileName"
        }
    }

    private fun buildDisplayFolderPath(
        rootPath: String,
        relativePath: String
    ): String {
        return if (relativePath.isBlank()) {
            rootPath.trimEnd('/')
        } else {
            "${rootPath.trimEnd('/')}/$relativePath"
        }
    }

    private fun createVirtualMediaId(uriString: String): Long {
        val hash = uriString.hashCode().toLong()
        return -abs(hash).coerceAtLeast(1L)
    }
}
