package com.lonx.lyrico.utils

import android.util.Log

object LyricsSearchTextExtractor {
    private const val TAG = "LyricsSearchTextExtractor"

    fun extractLines(lyrics: String?): List<String> {
        val raw = lyrics.orEmpty()
        if (raw.isBlank()) return emptyList()

        // 歌词搜索索引是 best-effort 的附加能力：任何解析失败都必须
        // 回退到纯文本提取，不允许导致整首音频扫描失败。
        val decodedLines = runCatching { LyricDecoder.decode(raw) }
            .onFailure { e ->
                Log.w(TAG, "Decode failed, fallback to plain text extraction: ${e.message}")
            }
            .getOrNull()
            ?.let { result ->
                listOfNotNull(
                    result.original,
                    result.translated,
                    result.romanization
                )
                    .flatten()
                    .map { line -> line.words.joinToString("") { word -> word.text } }
            }
            .orEmpty()

        return decodedLines.ifEmpty { fallbackLyricLines(raw) }
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() }
            .distinct()
    }

    fun toSearchText(lyrics: String?): String? {
        return extractLines(lyrics)
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
    }

    private fun fallbackLyricLines(lyrics: String): List<String> {
        return lyrics.lineSequence()
            .map { line ->
                line
                    .replace(lrcTimePattern, "")
                    .replace(ttmlTagPattern, "")
                    .trim()
            }
            .filter { line ->
                line.isNotBlank() &&
                    !line.startsWith("[") &&
                    !line.startsWith("<")
            }
            .toList()
    }

    private val lrcTimePattern = Regex("""\[(?:\d{1,2}:)?\d{1,2}:\d{1,2}(?:[.:]\d{1,3})?]""")
    private val ttmlTagPattern = Regex("""<[^>]+>""")
}
