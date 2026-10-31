package com.lonx.lyrico.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import androidx.core.net.toUri
import com.lonx.lyrico.domain.poster.ArtistPosterFileNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把一张海报写进艺术家海报文件夹（SAF 授权目录，应用只允许配置一个）。
 *
 * 先写完整的临时文件再替换旧文件，避免把 JPEG/PNG 字节直接写进旧的 `.mp4`/`.webp`，
 * 也避免写到一半破坏原海报。
 */
object ArtistPosterFileWriter {

    private const val TAG = "ArtistPosterWriter"

    /**
     * 写入 [data]；返回真正写下去的文件名，`null` 表示没有写入。
     */
    suspend fun write(
        context: Context,
        artistName: String,
        data: ByteArray,
        mimeType: String,
        posterFolder: String?
    ): String? = withContext(Dispatchers.IO) {
        if (data.isEmpty() || posterFolder == null) {
            AppLog.record(AppLogType.METADATA, TAG, "Poster write skipped",
                "folder=$posterFolder\nartist=$artistName\nbytes=${data.size}\nreason=empty-data-or-no-folder", level = AppLogLevel.WARNING)
            return@withContext null
        }

        val stem = ArtistPosterFileNaming.stemFor(artistName) ?: return@withContext null
        val extension = ArtistPosterFileNaming.extensionFor(mimeType)
        // Provider 要的是具体类型；选图器偶尔给出 image/* 这类模糊值，退回 jpg 更稳妥
        val folderMimeType = mimeType.substringBefore(';').trim()
            .takeIf { it.startsWith("image/") && it != "image/*" }
            ?: "image/jpeg"

        runCatching {
            writeToFolder(
                context = context,
                folder = posterFolder,
                stem = stem,
                extension = extension,
                artistName = artistName,
                mimeType = folderMimeType,
                data = data
            )
        }.getOrElse { error ->
            AppLog.record(AppLogType.METADATA, TAG, "Poster folder write failed",
                "folder=$posterFolder\nartist=$artistName\nmime=$folderMimeType\nbytes=${data.size}",
                level = AppLogLevel.ERROR, error = error)
            null
        }
    }

    private fun writeToFolder(
        context: Context,
        folder: String,
        stem: String,
        extension: String,
        artistName: String,
        mimeType: String,
        data: ByteArray
    ): String? {
        fun report(stage: String, detail: String, level: AppLogLevel = AppLogLevel.WARNING) =
            AppLog.record(AppLogType.METADATA, TAG, stage, "folder=$folder\nartist=$artistName\n$detail", folder, level)
        val treeUri = folder.toUri()
        // `null` 表示文件夹被删除或授权已失效，不能与空文件夹混为一谈。
        val children = SafDocuments.children(context, treeUri) ?: run {
            report("Poster write failed", "stage=enumerate-folder reason=provider-unavailable")
            return null
        }
        val files = children.filterNot { it.isDirectory }
        val existing = ArtistPosterFileNaming
            .matchesArtist(files.map { it.displayName }, artistName)
            .firstOrNull()

        val existingUri = existing?.let { name -> files.firstOrNull { it.displayName == name }?.uri }
        val parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        val desiredName = "$stem.$extension"
        val stagedName = if (existingUri == null) desiredName else {
            "${stem}_lyrico_${System.currentTimeMillis()}.$extension"
        }
        val documentUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            mimeType,
            stagedName
        ) ?: run {
            report("Poster write failed", "stage=create-document name=$stagedName reason=provider-returned-null")
            return null
        }

        val written = try {
            context.contentResolver.openOutputStream(documentUri, "wt")?.use { outputStream ->
                outputStream.write(data)
                outputStream.flush()
                true
            } ?: false
        } catch (error: Exception) {
            // Only this newly created file is ours to remove; leave the previous poster untouched.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, documentUri) }
                .exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }

        if (!written) {
            report("Poster write failed", "stage=open-output uri=$documentUri reason=provider-returned-null")
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, documentUri) }
            return null
        }

        if (existingUri == null) {
            report("Poster file written", "uri=$documentUri\nbytes=${data.size}\nmime=$mimeType", AppLogLevel.INFO)
            return desiredName
        }

        // 新文件已经完整落盘，现在才动旧文件。删不掉时保留两者，至少不会丢图。
        val oldDeleted = runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, existingUri)
        }.getOrDefault(false)
        if (!oldDeleted) {
            report("Poster replacement failed", "stage=delete-original original=$existingUri staged=$documentUri")
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, documentUri) }
            return null
        }

        val renamed = runCatching {
            DocumentsContract.renameDocument(context.contentResolver, documentUri, desiredName)
        }.getOrNull()
        report("Poster replacement finished", "original=$existingUri\nstaged=$documentUri\nrenamed=$renamed\nbytes=${data.size}",
            if (renamed == null) AppLogLevel.WARNING else AppLogLevel.INFO)
        return if (renamed != null) desiredName else stagedName
    }
    /** Replace an existing image only after a complete staged file has been written. */
    fun replaceFile(context: Context, tree: Uri, uri: Uri, name: String, bitmap: Bitmap, expectedBytes: ByteArray? = null) {
        val children = SafDocuments.children(context, tree) ?: error("Folder unavailable")
        check(children.any { it.uri == uri && it.displayName == name }) { "Poster changed" }
        if (expectedBytes != null) {
            val current = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            check(current != null && current.contentEquals(expectedBytes)) { "Poster changed" }
        }
        val extension = name.substringAfterLast('.').lowercase()
        val format = when (extension) {
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
            else -> error("Unsupported crop format")
        }
        val mime = when (extension) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val token = java.util.UUID.randomUUID().toString()
        val staged = checkNotNull(DocumentsContract.createDocument(resolver, parent, mime, ".lyrico-$token.$extension"))
        var backup: Uri? = null
        var committed = false
        try {
            check(resolver.openOutputStream(staged, "wt")?.use { output ->
                bitmap.compress(format, 95, output).also { output.flush() }
            } == true)
            // Keep the original recoverable until the complete replacement has its final name.
            backup = checkNotNull(DocumentsContract.renameDocument(resolver, uri, ".lyrico-$token.bak"))
            checkNotNull(DocumentsContract.renameDocument(resolver, staged, name))
            committed = true
            runCatching { DocumentsContract.deleteDocument(resolver, backup) }
        } finally {
            if (!committed) {
                backup?.let { runCatching { DocumentsContract.renameDocument(resolver, it, name) } }
                runCatching { DocumentsContract.deleteDocument(resolver, staged) }
            }
        }
    }

}
