package com.lonx.lyrico.utils

import com.lonx.audiotag.model.AudioTagData
import kotlinx.serialization.Serializable

@Serializable
enum class TagFindReplaceMode {
    CONTAINS,
    EXACT,
    REGEX
}

@Serializable
data class TagFindReplaceConfig(
    val fields: Set<TagTextField> = setOf(TagTextField.GENRE, TagTextField.COMMENT),
    val find: String = "",
    val replacement: String = "",
    val ignoreCase: Boolean = true,
    val mode: TagFindReplaceMode = TagFindReplaceMode.CONTAINS
)

data class TagFindReplaceChange(
    val field: TagTextField,
    val originalValue: String,
    val newValue: String
)

object TagFindReplace {
    private val genreSeparators = Regex("""\s*(?:;|/|,|，|、|\||｜|\r?\n)+\s*""")
    private val repeatedSeparators = Regex("""\s*(?:;|/|,|，|、|\||｜)+\s*(?:;|/|,|，|、|\||｜)+\s*""")
    private val whitespace = Regex("""[ \t\r\n]+""")

    fun preview(tag: AudioTagData, config: TagFindReplaceConfig): List<TagFindReplaceChange> {
        if (config.find.isBlank() || config.fields.isEmpty()) return emptyList()
        return config.fields.mapNotNull { field ->
            val original = field.valueOf(tag) ?: return@mapNotNull null
            val updated = replaceValue(field, original, config)
            if (updated != original) {
                TagFindReplaceChange(field, original, updated)
            } else {
                null
            }
        }
    }

    fun apply(tag: AudioTagData, config: TagFindReplaceConfig): AudioTagData {
        return preview(tag, config).fold(tag) { current, change ->
            change.field.copyInto(current, change.newValue)
        }
    }

    fun replaceValue(
        field: TagTextField,
        value: String,
        config: TagFindReplaceConfig
    ): String {
        if (config.find.isBlank()) return value
        return if (field == TagTextField.GENRE) {
            replaceGenre(value, config)
        } else {
            normalizeText(replaceText(value, config))
        }
    }

    fun matches(value: String?, config: TagFindReplaceConfig): Boolean {
        if (value.isNullOrBlank() || config.find.isBlank()) return false
        return when (config.mode) {
            TagFindReplaceMode.CONTAINS -> value.contains(config.find, ignoreCase = config.ignoreCase)
            TagFindReplaceMode.EXACT -> value.trim().equals(config.find, ignoreCase = config.ignoreCase)
            TagFindReplaceMode.REGEX -> buildRegex(config).containsMatchIn(value)
        }
    }

    private fun replaceGenre(value: String, config: TagFindReplaceConfig): String {
        val tokens = value.split(genreSeparators)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val cleanedTokens = tokens.mapNotNull { token ->
            if (!matches(token, config)) {
                token
            } else {
                val replaced = replaceText(token, config).trim()
                replaced.takeIf { it.isNotBlank() }
            }
        }

        return cleanedTokens.joinToString("; ")
    }

    private fun replaceText(value: String, config: TagFindReplaceConfig): String {
        return when (config.mode) {
            TagFindReplaceMode.CONTAINS -> value.replace(
                config.find,
                config.replacement,
                ignoreCase = config.ignoreCase
            )

            TagFindReplaceMode.EXACT -> if (value.trim().equals(
                    config.find,
                    ignoreCase = config.ignoreCase
                )
            ) {
                config.replacement
            } else {
                value
            }

            TagFindReplaceMode.REGEX -> buildRegex(config).replace(value, config.replacement)
        }
    }

    private fun buildRegex(config: TagFindReplaceConfig): Regex {
        val options = if (config.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return Regex(config.find, options)
    }

    private fun normalizeText(value: String): String {
        return value
            .replace(repeatedSeparators, " ")
            .replace(whitespace, " ")
            .trim(' ', ';', '/', ',', '，', '、', '|', '｜')
            .replace(Regex("""\s+([,，;；、|｜])"""), "\$1")
            .replace(Regex("""([,，;；、|｜])\s+"""), "\$1 ")
            .trim()
    }
}
