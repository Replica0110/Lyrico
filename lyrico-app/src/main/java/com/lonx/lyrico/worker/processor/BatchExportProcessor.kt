package com.lonx.lyrico.worker.processor

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.lonx.audiotag.model.frontCoverOrFallback
import com.lonx.lyrico.data.export.ExportDestination
import com.lonx.lyrico.data.batch.BatchTaskType
import com.lonx.lyrico.data.db.entity.BatchTaskEntity
import com.lonx.lyrico.data.db.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.artwork.CoverSourceType
import com.lonx.lyrico.platform.storage.SafSiblingFileWriter
import com.lonx.lyrico.data.artwork.getCoverSourceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import java.io.OutputStream
import java.net.URL

@Serializable
data class BatchExportTaskConfig(
    val destinationTreeUri: String? = null,
    val destination: ExportDestination = ExportDestination.SELECTED_DIRECTORY,
    val concurrency: Int = 3
)

class BatchExportProcessor(
    private val context: Context,
    private val audioTagRepository: AudioTagRepository
) : BatchTaskProcessor {

    private val audioDirectoryWriteMutex = Mutex()

    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val config = task.configJson?.let {
            Json.decodeFromString<BatchExportTaskConfig>(it)
        } ?: throw BatchTaskSkippedException("No config")

        val tagData = audioTagRepository.read(item.songUri, AudioTagReadOptions(strict = true))
        val result = when (task.type) {
            BatchTaskType.EXPORT_LYRICS -> when (config.destination) {
                ExportDestination.SELECTED_DIRECTORY -> exportLyrics(
                    item = item,
                    lyrics = tagData.lyrics,
                    directory = requireSelectedDirectory(config)
                )

                ExportDestination.AUDIO_DIRECTORY -> audioDirectoryWriteMutex.withLock {
                    exportLyricsNextToAudio(item, tagData.lyrics)
                }
            }
            BatchTaskType.EXPORT_COVER -> {
                val coverSource = tagData.pictures.frontCoverOrFallback()?.data
                when (config.destination) {
                    ExportDestination.SELECTED_DIRECTORY -> exportCover(
                        item,
                        coverSource,
                        requireSelectedDirectory(config)
                    )

                    ExportDestination.AUDIO_DIRECTORY -> audioDirectoryWriteMutex.withLock {
                        exportCoverNextToAudio(item, coverSource)
                    }
                }
            }
            else -> throw IllegalArgumentException("Unsupported export task type: ${task.type}")
        }
        onProgress(1f)
        return result
    }

    private fun requireSelectedDirectory(config: BatchExportTaskConfig): DocumentFile {
        val treeUri = config.destinationTreeUri
            ?.takeIf { it.isNotBlank() }
            ?.toUri()
            ?: throw Exception("Destination folder unavailable")
        val directory = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw Exception("Destination folder unavailable")
        if (!directory.canWrite()) {
            throw Exception("Destination folder is not writable")
        }
        return directory
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

    private fun exportLyricsNextToAudio(
        item: BatchTaskItemEntity,
        lyrics: String?
    ): BatchTaskProcessResult {
        if (lyrics.isNullOrBlank()) throw BatchTaskSkippedException("No lyrics")

        val extension = if (detectLyricsFormat(lyrics) == "ttml") "ttml" else "lrc"
        val fileName = "${item.baseFileName()}.$extension"
        val audioUri = item.songUri.toUri()
        val outputUri = SafSiblingFileWriter.write(
            context = context,
            sourceDocumentUri = audioUri,
            mimeType = LYRICS_EXPORT_MIME_TYPE,
            fileName = fileName,
            bytes = lyrics.toByteArray(Charsets.UTF_8)
        )

        return BatchTaskProcessResult(
            updatedFilePath = outputUri.toString(),
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
            writeCoverSource(outputStream, coverSource)
        } ?: throw Exception("Failed to open cover output stream")

        return BatchTaskProcessResult(
            updatedFilePath = file.uri.toString(),
            updatedFileName = fileName
        )
    }

    private fun exportCoverNextToAudio(
        item: BatchTaskItemEntity,
        coverSource: Any?
    ): BatchTaskProcessResult {
        if (coverSource == null) throw BatchTaskSkippedException("No cover")

        val fileName = "${item.baseFileName()}.jpg"
        val outputUri = SafSiblingFileWriter.write(
            context = context,
            sourceDocumentUri = item.songUri.toUri(),
            mimeType = "image/jpeg",
            fileName = fileName
        ) { outputStream ->
            writeCoverSource(outputStream, coverSource)
        }

        return BatchTaskProcessResult(
            updatedFilePath = outputUri.toString(),
            updatedFileName = fileName
        )
    }

    private fun writeCoverSource(outputStream: OutputStream, coverSource: Any) {
        when (getCoverSourceType(coverSource)) {
            CoverSourceType.BYTE_ARRAY -> outputStream.write(coverSource as ByteArray)

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
