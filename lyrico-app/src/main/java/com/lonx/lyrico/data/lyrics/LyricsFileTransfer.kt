package com.lonx.lyrico.data.lyrics

import android.content.Context
import android.net.Uri
import com.lonx.lyrico.domain.lyrics.LyricDecoder
import com.lonx.lyrico.platform.storage.SafSiblingFileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 歌词文件的读写与命名。
 *
 * 导出扩展名以歌词实际内容为准（[LyricDecoder.detectFormat]），
 * 为单曲编辑提供导入和导出，编辑页负责状态与提示。
 */
object LyricsFileTransfer {

    /** 歌词没有通用的 MIME 类型，按 opaque 二进制交给系统选择保存位置。 */
    private const val EXPORT_MIME_TYPE = "application/octet-stream"

    /**
     * 导出用的默认文件名；没有歌词时返回 null（调用方据此不发起保存）。
     */
    fun defaultFileName(baseName: String?, lyrics: String?): String? {
        if (lyrics.isNullOrBlank()) return null
        val extension = if (LyricDecoder.detectFormat(lyrics) == LyricFormat.TTML) "ttml" else "lrc"
        return "${baseName.orEmpty()}.$extension"
    }

    /** 读取 [uri] 指向的歌词文件；内容为空时返回 null。 */
    suspend fun read(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader(Charsets.UTF_8).readText()
        }?.takeIf { it.isNotBlank() }
    }

    /** 把歌词写到用户选定的 [destinationUri]。 */
    suspend fun write(context: Context, destinationUri: Uri, lyrics: String) = withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openOutputStream(destinationUri)) {
            "No lyrics output stream: $destinationUri"
        }.use { outputStream ->
            outputStream.write(lyrics.toByteArray(Charsets.UTF_8))
        }
    }

    /** 把歌词写到音频所在目录，文件名沿用 [fileName]。 */
    fun writeNextToAudio(
        context: Context,
        audioUri: Uri,
        fileName: String,
        lyrics: String,
    ): Uri = SafSiblingFileWriter.write(
        context = context,
        sourceDocumentUri = audioUri,
        mimeType = EXPORT_MIME_TYPE,
        fileName = fileName,
        bytes = lyrics.toByteArray(Charsets.UTF_8),
    )
}
