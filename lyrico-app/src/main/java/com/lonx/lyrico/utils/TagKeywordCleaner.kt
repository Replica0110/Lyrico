package com.lonx.lyrico.utils

import kotlinx.serialization.Serializable

@Serializable
enum class TagKeywordCleanField {
    GENRE,
    COMMENT
}

@Serializable
enum class TagKeywordMatchMode {
    CONTAINS,
    EXACT,
    REGEX
}

@Serializable
data class TagKeywordCleanConfig(
    val fields: Set<TagKeywordCleanField> = setOf(
        TagKeywordCleanField.GENRE,
        TagKeywordCleanField.COMMENT
    ),
    val keyword: String = "kuwo",
    val replacement: String = "",
    val ignoreCase: Boolean = true,
    val matchMode: TagKeywordMatchMode = TagKeywordMatchMode.CONTAINS
)

object TagKeywordCleaner {
    private val genreSeparators = Regex("""\s*(?:;|/|,|，|、|\||｜|\r?\n)+\s*""")
    private val repeatedSeparators = Regex("""\s*(?:;|/|,|，|、|\||｜)+\s*(?:;|/|,|，|、|\||｜)+\s*""")
    private val whitespace = Regex("""[ \t\r\n]+""")

    fun cleanGenre(value: String?, config: TagKeywordCleanConfig): String? {
        if (value == null || value.isBlank() || config.keyword.isBlank()) return value

        val tokens = value.split(genreSeparators)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val cleanedTokens = tokens.mapNotNull { token ->
            if (!matches(token, config)) {
                token
            } else {
                cleanMatchedToken(token, config).takeIf { it.isNotBlank() }
            }
        }

        return cleanedTokens.joinToString("; ")
    }

    fun cleanComment(value: String?, config: TagKeywordCleanConfig): String? {
        if (value == null || value.isBlank() || config.keyword.isBlank()) return value

        val replaced = when (config.matchMode) {
            TagKeywordMatchMode.CONTAINS -> value.replace(
                config.keyword,
                config.replacement,
                ignoreCase = config.ignoreCase
            )

            TagKeywordMatchMode.EXACT -> if (equalsKeyword(value.trim(), config)) {
                config.replacement
            } else {
                value
            }

            TagKeywordMatchMode.REGEX -> buildRegex(config).replace(value, config.replacement)
        }

        return normalizeComment(replaced)
    }

    fun matches(value: String?, config: TagKeywordCleanConfig): Boolean {
        if (value.isNullOrBlank() || config.keyword.isBlank()) return false
        return when (config.matchMode) {
            TagKeywordMatchMode.CONTAINS -> value.contains(
                config.keyword,
                ignoreCase = config.ignoreCase
            )

            TagKeywordMatchMode.EXACT -> equalsKeyword(value.trim(), config)
            TagKeywordMatchMode.REGEX -> buildRegex(config).containsMatchIn(value)
        }
    }

    private fun cleanMatchedToken(token: String, config: TagKeywordCleanConfig): String {
        if (config.replacement.isBlank()) return ""
        return when (config.matchMode) {
            TagKeywordMatchMode.CONTAINS -> token.replace(
                config.keyword,
                config.replacement,
                ignoreCase = config.ignoreCase
            )

            TagKeywordMatchMode.EXACT -> if (equalsKeyword(token.trim(), config)) {
                config.replacement
            } else {
                token
            }

            TagKeywordMatchMode.REGEX -> buildRegex(config).replace(token, config.replacement)
        }.trim()
    }

    private fun equalsKeyword(value: String, config: TagKeywordCleanConfig): Boolean {
        return value.equals(config.keyword, ignoreCase = config.ignoreCase)
    }

    private fun buildRegex(config: TagKeywordCleanConfig): Regex {
        val options = if (config.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return Regex(config.keyword, options)
    }

    private fun normalizeComment(value: String): String {
        return value
            .replace(repeatedSeparators, " ")
            .replace(whitespace, " ")
            .trim(' ', ';', '/', ',', '，', '、', '|', '｜')
            .replace(Regex("""\s+([,，;；、|｜])"""), "\$1")
            .replace(Regex("""([,，;；、|｜])\s+"""), "\$1 ")
            .trim()
    }
}
