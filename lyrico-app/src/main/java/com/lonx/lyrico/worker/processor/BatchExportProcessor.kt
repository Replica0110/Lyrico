package com.lonx.lyrico.worker.processor

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.utils.CoverSourceType
import com.lonx.lyrico.utils.getCoverSourceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.net.URL

@Serializable
data class BatchExportTaskConfig(
    val destinationTreeUri: String,
    val concurrency: Int = 3
)

class BatchExportProcessor(
    private val context: Context,
    private val audioTagRepository: AudioTagRepository
) : BatchTaskProcessor {

    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val config = task.configJson?.let {
            Json.decodeFromString<BatchExportTaskConfig>(it)
        } ?: throw BatchTaskSkippedException("No config")

        val directory = DocumentFile.fromTreeUri(context, config.destinationTreeUri.toUri())
            ?: throw Exception("Destination folder unavailable")
        if (!directory.canWrite()) {
            throw Exception("Destination folder is not writable")
        }

        val tagData = audioTagRepository.read(item.songUri)
        val result = when (task.type) {
            BatchTaskType.EXPORT_LYRICS -> exportLyrics(item, tagData.lyrics, directory)
            BatchTaskType.EXPORT_COVER -> {
                val coverSource = tagData.pictures.firstOrNull()?.data ?: tagData.picUrl
                exportCover(item, coverSource, directory)
            }
            else -> throw IllegalArgumentException("Unsupported export task type: ${task.type}")
        }
        onProgress(1f)
        return result
    }

    private fun exportLyrics(
        item: BatchTaskItemEntity,
        lyrics: String?,
        directory: DocumentFile
    ): BatchTaskProcessResult {
        if (lyrics.isNullOrBlank()) throw BatchTaskSkippedException("No lyrics")

        val extension = if (detectLyricsFormat(lyrics) == "ttml") "ttml" else "lrc"
        val fileName = "${item.baseFileName()}.$extension"
        val file = createOrFindExportFile(directory, LYRICS_EXPORT_MIME_TYPE, fileName)
            ?: throw Exception("Failed to create lyrics file")

        context.contentResolver.openOutputStream(file.uri, "wt")?.use { outputStream ->
            outputStream.write(lyrics.toByteArray(Charsets.UTF_8))
        } ?: throw Exception("Failed to open lyrics output stream")

        return BatchTaskProcessResult(
            updatedFilePath = file.uri.toString(),
            updatedFileName = fileName
        )
    }

    private fun exportCover(
        item: BatchTaskItemEntity,
        coverSource: Any?,
        directory: DocumentFile
    ): BatchTaskProcessResult {
        if (coverSource == null) throw BatchTaskSkippedException("No cover")

        val fileName = "${item.baseFileName()}.jpg"
        val file = createOrFindExportFile(directory, "image/jpeg", fileName)
            ?: throw Exception("Failed to create cover file")

        context.contentResolver.openOutputStream(file.uri, "wt")?.use { outputStream ->
            when (getCoverSourceType(coverSource)) {
                CoverSourceType.BYTE_ARRAY -> {
                    outputStream.write(coverSource as ByteArray)
                }

                CoverSourceType.NETWORK_URL -> {
                    URL(coverSource.toString().trim()).openStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                CoverSourceType.CONTENT_OR_FILE_URI,
                CoverSourceType.URI -> {
                    val sourceUri = when (coverSource) {
                        is Uri -> coverSource
                        is String -> coverSource.trim().toUri()
                        else -> null
                    } ?: throw Exception("Unsupported cover URI")
                    context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    } ?: throw Exception("Failed to open cover source stream")
                }

                CoverSourceType.FILE_PATH -> {
                    FileInputStream(coverSource.toString().trim()).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                CoverSourceType.BITMAP,
                CoverSourceType.UNSUPPORTED -> throw Exception("Unsupported cover source")
            }
        } ?: throw Exception("Failed to open cover output stream")

        return BatchTaskProcessResult(
            updatedFilePath = file.uri.toString(),
            updatedFileName = fileName
        )
    }

    private fun createOrFindExportFile(
        directory: DocumentFile,
        mimeType: String,
        fileName: String
    ): DocumentFile? {
        return directory.findFile(fileName) ?: directory.createFile(mimeType, fileName)
    }

    private fun BatchTaskItemEntity.baseFileName(): String =
        fileName.substringBeforeLast(".", missingDelimiterValue = fileName)

    private fun detectLyricsFormat(lyrics: String): String {
        if (lyrics.contains("begin=") && lyrics.contains("end=") && lyrics.contains("<?xml")) {
            return "ttml"
        }
        return "lrc"
    }

    private companion object {
        const val LYRICS_EXPORT_MIME_TYPE = "application/octet-stream"
    }
}
