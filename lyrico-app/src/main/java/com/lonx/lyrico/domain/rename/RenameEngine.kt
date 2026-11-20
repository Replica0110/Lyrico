package com.lonx.lyrico.domain.rename

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.rename.CharacterMappingRule
import java.io.File

object RenameEngine {
    private const val TAG = "RenameEngine"
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

            var newFileName = FormatParser.buildFileName(tokens, song.tagData)
            newFileName = FileNameSanitizer.sanitize(newFileName, request.characterMappingRules)

            if (newFileName.isEmpty()) {
                newFileName = file.nameWithoutExtension
            }

            val extension = file.extension
            if (extension.isNotEmpty()) {
                newFileName = "$newFileName.$extension"
            }

            var newPath = if (parentDir.isNotEmpty()) {
                File(parentDir, newFileName).absolutePath
            } else {
                newFileName
            }

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
