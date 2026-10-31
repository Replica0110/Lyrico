package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import com.lonx.lyrico.data.model.SongFile
import com.lonx.lyrico.data.model.entity.FolderEntity
import java.util.Locale
import java.io.IOException
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
    ): List<SafDocumentRow> =
        SafDocuments.children(context, treeUri, parentDocumentId)
            ?: throw IOException("SAF directory enumeration unavailable: $parentDocumentId")

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
            }.also { found ->
                if (found != true) AppLog.record(AppLogType.APP, "MediaScanner", "SAF scan root unavailable",
                    "uri=$documentUri\nresult=${if (found == null) "null-cursor" else "empty-cursor"}",
                    documentUri.toString(), AppLogLevel.WARNING)
            } ?: throw IOException("SAF root query returned no cursor")
        } catch (e: Exception) {
            AppLog.record(AppLogType.APP, "MediaScanner", "SAF scan root query failed",
                "uri=$documentUri", documentUri.toString(), AppLogLevel.WARNING, e)
            throw e
        }
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
