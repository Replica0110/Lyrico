package com.lonx.lyrico.domain.rename

import com.lonx.audiotag.model.AudioTagData

object FormatParser {
    private val PLACEHOLDER_PATTERN = """@(\d+)""".toRegex()

    fun parseFormat(format: String): List<RenameToken> {
        val tokens = mutableListOf<RenameToken>()
        var lastIndex = 0

        PLACEHOLDER_PATTERN.findAll(format).forEach { matchResult ->
            val startIndex = matchResult.range.first

            if (startIndex > lastIndex) {
                tokens.add(TextToken(format.substring(lastIndex, startIndex)))
            }

            val index = matchResult.groupValues[1].toIntOrNull()
            val field = index?.let { TagField.fromIndex(it) }

            if (field != null) {
                tokens.add(FieldToken(field))
            } else {
                // 无效占位符，按文本处理
                tokens.add(TextToken(matchResult.value))
            }

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < format.length) {
            tokens.add(TextToken(format.substring(lastIndex)))
        }

        return tokens
    }

    fun buildFileName(tokens: List<RenameToken>, tag: AudioTagData): String {
        return tokens.joinToString("") { token ->
            when (token) {
                is TextToken -> token.text
                is FieldToken -> getFieldValue(token.field, tag).orEmpty()
            }
        }
    }

    private fun getFieldValue(field: TagField, tag: AudioTagData): String? {
        return when (field) {
            TagField.ARTIST -> tag.artist
            TagField.ALBUM_ARTIST -> tag.albumArtist
            TagField.ALBUM -> tag.album
            TagField.TITLE -> tag.title
            TagField.TRACK -> tag.trackNumber
            TagField.DISC -> tag.discNumber?.toString()
            TagField.YEAR -> tag.date
            TagField.GENRE -> tag.genre
        }
    }
}
