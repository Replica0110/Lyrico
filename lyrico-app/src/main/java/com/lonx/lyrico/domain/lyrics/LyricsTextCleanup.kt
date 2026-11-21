package com.lonx.lyrico.domain.lyrics

object LyricsTextCleanup {
    fun process(
        raw: String,
        removeEmptyLines: Boolean,
        tagLineKeywords: List<String>
    ): String {
        val keywords = tagLineKeywords.map { it.trim() }.filter { it.isNotEmpty() }
        return Regex("""([^\r\n]*)(\r\n|\n|\r|$)""").findAll(raw)
            .filter { it.value.isNotEmpty() }
            .filterNot { match ->
                val line = match.groupValues[1]
                val visible = visibleLineText(line)
                val trimmed = visible.trim()
                val removeEmpty = removeEmptyLines && isBlankOrPlaceholder(trimmed)
                val removeTag = keywords.any { keyword ->
                    line.contains(keyword, ignoreCase = true) ||
                            visible.contains(keyword, ignoreCase = true)
                }
                removeEmpty || removeTag
            }
            .joinToString("") { it.value }
    }

    private fun visibleLineText(line: String): String {
        return line
            .replace(LyricsTimestamp.lrcTimePattern, "")
            .replace(Regex("""<[^>]+>"""), "")
    }

    private fun isBlankOrPlaceholder(text: String): Boolean {
        return text.isEmpty() || text.matches(Regex("^[\\s/\\\\|｜·・.。…_-]*$"))
    }
}
