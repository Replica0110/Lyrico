package com.lonx.lyrico.utils

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.CharacterMappingRule
import com.lonx.lyrico.data.model.RenamePreview
import kotlinx.coroutines.delay
import java.io.File

object RenameEngine {
    data class RenameRequest(
        val songs: List<SongForRename>,
        val format: String,
        val characterMappingRules: List<CharacterMappingRule> = emptyList(),
        val createSubdirectories: Boolean = false
    )

    data class SongForRename(
        val originalPath: String,
        val tagData: AudioTagData
    )

    fun generatePreviews(request: RenameRequest): List<RenamePreview> {
        val tokens = FormatParser.parseFormat(request.format)
        val previews = mutableListOf<RenamePreview>()
        val generatedPaths = mutableSetOf<String>()

        for (song in request.songs) {
            val file = File(song.originalPath)
            val parentDir = file.parent ?: ""

            // Generate new file name
            var newFileName = FormatParser.buildFileName(tokens, song.tagData)
            newFileName = FileNameSanitizer.sanitize(newFileName, request.characterMappingRules)

            if (newFileName.isEmpty()) {
                newFileName = file.nameWithoutExtension
            }

            // Append extension
            val extension = file.extension
            if (extension.isNotEmpty()) {
                newFileName = "$newFileName.$extension"
            }

            // Build full path
            var newPath = if (parentDir.isNotEmpty()) {
                File(parentDir, newFileName).absolutePath
            } else {
                newFileName
            }

            // Resolve conflicts
            val conflict = generatedPaths.contains(newPath)
            newPath = ConflictResolver.resolveConflict(newPath, generatedPaths)
            generatedPaths.add(newPath)

            previews.add(
                RenamePreview(
                    originalPath = song.originalPath,
                    newPath = newPath,
                    conflict = conflict
                )
            )
        }

        return previews
    }

    suspend fun renameFiles(previews: List<RenamePreview>): Result {
        val result = Result(
            successful = mutableListOf(),
            failed = mutableListOf()
        )

        for (preview in previews) {
            try {
                val oldFile = File(preview.originalPath)
                val newFile = File(preview.newPath)

                newFile.parentFile?.mkdirs()

                if (oldFile.renameTo(newFile)) {
                    result.successful.add(preview)
                    delay(50L)
                } else {
                    result.failed.add(preview to "Failed to rename file")
                }
            } catch (e: Exception) {
                result.failed.add(preview to e.message.orEmpty())
            }
        }

        return result
    }

    data class Result(
        val successful: MutableList<RenamePreview> = mutableListOf(),
        val failed: MutableList<Pair<RenamePreview, String>> = mutableListOf()
    ) {
        val totalCount: Int get() = successful.size + failed.size
        val successCount: Int get() = successful.size
        val failureCount: Int get() = failed.size
        val isSuccessful: Boolean get() = failed.isEmpty()
    }

    fun getPresetFormats(): List<String> {
        return listOf(
            "${TagField.placeholder(TagField.TITLE)} - ${TagField.placeholder(TagField.ARTIST)}",
            "${TagField.placeholder(TagField.ARTIST)} - ${TagField.placeholder(TagField.TITLE)}",
            "${TagField.placeholder(TagField.TRACK)} - ${TagField.placeholder(TagField.TITLE)}",
            "${TagField.placeholder(TagField.ALBUM)} - ${TagField.placeholder(TagField.TITLE)}",
            "${TagField.placeholder(TagField.ARTIST)} - ${TagField.placeholder(TagField.ALBUM)} - ${TagField.placeholder(TagField.TRACK)} - ${TagField.placeholder(TagField.TITLE)}"
        )
    }
}
