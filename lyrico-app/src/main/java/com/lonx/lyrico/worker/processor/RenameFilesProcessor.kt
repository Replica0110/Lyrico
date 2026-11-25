package com.lonx.lyrico.worker.processor

import com.lonx.lyrico.data.rename.CharacterMappingRule
import com.lonx.lyrico.data.db.entity.BatchTaskEntity
import com.lonx.lyrico.data.db.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.domain.song.usecase.RenameSongResult
import com.lonx.lyrico.domain.song.usecase.RenameSongUseCase
import com.lonx.lyrico.domain.rename.FileNameSanitizer
import com.lonx.lyrico.domain.rename.FormatParser
import kotlinx.serialization.Serializable

@Serializable
data class RenameFilesTaskConfig(
    val renameFormat: String,
    val characterMappingRules: List<CharacterMappingRule> = emptyList()
)

@Serializable
data class RenameFilesTaskResult(
    val originalUri: String? = null,
    val newUri: String? = null,
    val originalPath: String? = null,
    val newPath: String
)

class RenameFilesProcessor(
    private val songLibraryRepository: SongLibraryRepository,
    private val renameSongUseCase: RenameSongUseCase,
    private val audioTagRepository: AudioTagRepository
) : BatchTaskProcessor {

    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val config = task.configJson?.let {
            kotlinx.serialization.json.Json.decodeFromString<RenameFilesTaskConfig>(it)
        } ?: throw BatchTaskSkippedException("No config")

        val song = songLibraryRepository.getSongByUri(item.songUri)
            ?: throw BatchTaskSkippedException("Song not found")

        val tagData = audioTagRepository.read(song.uri, AudioTagReadOptions(strict = true))

        val tokens = FormatParser.parseFormat(config.renameFormat)
        var newFileName = FormatParser.buildFileName(tokens, tagData)
        newFileName = FileNameSanitizer.sanitize(newFileName, config.characterMappingRules)

        if (newFileName.isEmpty()) {
            newFileName = song.fileName.substringBeforeLast(".")
        }

        val extension = song.fileName.substringAfterLast(".", missingDelimiterValue = "")
        if (extension.isNotEmpty()) {
            newFileName = "$newFileName.$extension"
        }

        if (newFileName == song.fileName) {
            throw BatchTaskSkippedException("Same file name")
        }

        val result = renameSongUseCase(song, newFileName)
        if (result !is RenameSongResult.Success) {
            throw Exception("Failed to rename file")
        }

        return BatchTaskProcessResult(
            resultJson = kotlinx.serialization.json.Json.encodeToString(
                RenameFilesTaskResult.serializer(),
                RenameFilesTaskResult(
                    originalUri = result.oldUri,
                    newUri = result.song.uri,
                    originalPath = song.filePath,
                    newPath = result.song.filePath
                )
            ),
            updatedFilePath = result.song.filePath,
            updatedFileName = result.song.fileName
        )
    }

}
