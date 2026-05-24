package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.model.entity.SongEntity

enum class AdvancedSearchOperator {
    IS_EMPTY,
    IS_NOT_EMPTY,
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    REGEX
}

enum class AdvancedSearchJoinMode {
    AND,
    OR
}

data class AdvancedSearchCondition(
    val field: TagTextField = TagTextField.GENRE,
    val operator: AdvancedSearchOperator = AdvancedSearchOperator.CONTAINS,
    val value: String = "",
    val ignoreCase: Boolean = true
)

object AdvancedSearch {
    private val lyricsSearchConfig = LyricRenderConfig(
        format = LyricFormat.PLAIN_LRC,
        showRomanization = true,
        showTranslation = true,
        onlyTranslationIfAvailable = false,
        removeEmptyLines = true
    )

    fun matches(
        song: SongEntity,
        conditions: List<AdvancedSearchCondition>,
        joinMode: AdvancedSearchJoinMode
    ): Boolean {
        val activeConditions = conditions.filter { it.isUsable() }
        if (activeConditions.isEmpty()) return false

        return when (joinMode) {
            AdvancedSearchJoinMode.AND -> activeConditions.all { matchesCondition(song, it) }
            AdvancedSearchJoinMode.OR -> activeConditions.any { matchesCondition(song, it) }
        }
    }

    fun AdvancedSearchCondition.isUsable(): Boolean {
        return operator == AdvancedSearchOperator.IS_EMPTY ||
            operator == AdvancedSearchOperator.IS_NOT_EMPTY ||
            value.isNotBlank()
    }

    private fun matchesCondition(song: SongEntity, condition: AdvancedSearchCondition): Boolean {
        val fieldValue = condition.field.searchValueOf(song).orEmpty()
        val trimmedValue = fieldValue.trim()
        val query = condition.value

        return when (condition.operator) {
            AdvancedSearchOperator.IS_EMPTY -> trimmedValue.isEmpty()
            AdvancedSearchOperator.IS_NOT_EMPTY -> trimmedValue.isNotEmpty()
            AdvancedSearchOperator.EQUALS -> trimmedValue.equals(query, ignoreCase = condition.ignoreCase)
            AdvancedSearchOperator.NOT_EQUALS -> !trimmedValue.equals(query, ignoreCase = condition.ignoreCase)
            AdvancedSearchOperator.CONTAINS -> fieldValue.contains(query, ignoreCase = condition.ignoreCase)
            AdvancedSearchOperator.NOT_CONTAINS -> !fieldValue.contains(query, ignoreCase = condition.ignoreCase)
            AdvancedSearchOperator.REGEX -> runCatching {
                val options = if (condition.ignoreCase) {
                    setOf(RegexOption.IGNORE_CASE)
                } else {
                    emptySet()
                }
                Regex(query, options).containsMatchIn(fieldValue)
            }.getOrDefault(false)
        }
    }

    private fun TagTextField.searchValueOf(song: SongEntity): String? {
        val value = valueOf(song)
        if (this != TagTextField.LYRICS || value.isNullOrBlank()) return value

        return LyricDecoder.decode(value)?.let { result ->
            LyricEncoder.encodePlainText(result, lyricsSearchConfig)
        }?.takeIf { it.isNotBlank() } ?: value
    }
}
