package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsResult
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.domain.lyrics.document.LyricsDocumentPipeline
import com.lonx.lyrico.domain.lyrics.document.toLyricsResult
import kotlinx.coroutines.CancellationException

object LyricsParser {
    fun detectFormat(lyricsText: String): LyricsFormat? {
        if (lyricsText.isBlank()) return null

        // TTML 是 XML 文档：仅当文档以 <tt> 根元素开头时才判定为 TTML。
        // 不能因 LRC 正文任意位置出现 "<tt " 或 ttml 命名空间字符串而误判整份歌词。
        if (looksLikeTtml(lyricsText)) {
            return LyricsFormat.TTML
        }

        val sampleLines = lyricsText.lines().filter { it.isNotBlank() }

        var hasEnhanced = false
        var hasVerbatim = false
        var hasPlain = false

        for (line in sampleLines) {
            if (line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:")) continue

            val matches = LyricsTimestamp.lrcTimePattern.findAll(line).toList()
            if (matches.isEmpty()) continue

            val hasBracket = matches.any { it.value.startsWith("[") }
            val hasAngle = matches.any { it.value.startsWith("<") }

            if (hasBracket && hasAngle) {
                hasEnhanced = true
                break
            } else if (hasBracket && matches.size > 1) {
                hasVerbatim = true
                break
            } else if (hasBracket && matches.size == 1) {
                hasPlain = true
            }
        }

        return when {
            hasEnhanced -> LyricsFormat.ENHANCED_LRC
            hasVerbatim -> LyricsFormat.VERBATIM_LRC
            hasPlain -> LyricsFormat.PLAIN_LRC
            else -> null
        }
    }

    /**
     * 判断文本是否以 TTML 根元素 `<tt>` 开头。
     * 允许 BOM、前导空白、XML 声明（<?xml ... ?>）以及 XML 注释。
     */
    private fun looksLikeTtml(text: String): Boolean {
        var rest = text.trimStart('\uFEFF').trimStart()

        while (true) {
            rest = when {
                rest.startsWith("<?") -> {
                    val end = rest.indexOf("?>", startIndex = 2)
                    if (end < 0) return false
                    rest.substring(end + 2).trimStart()
                }

                rest.startsWith("<!--") -> {
                    val end = rest.indexOf("-->", startIndex = 4)
                    if (end < 0) return false
                    rest.substring(end + 3).trimStart()
                }

                else -> return isTtRootElement(rest)
            }
        }
    }

    private fun isTtRootElement(text: String): Boolean {
        return ttmlRootPattern.containsMatchIn(text)
    }

    private val ttmlRootPattern = Regex("^<(?:[A-Za-z_][A-Za-z0-9_.-]*:)?tt(?=[\\s/>])")

    /**
     * 安全解析入口：歌词是不可信输入，解析失败返回 null，异常不向 UI 抛出。
     */
    fun parseOrNull(lyricsText: String): LyricsResult? {
        return safely { parseDocumentOrNull(lyricsText)?.toLyricsResult() }
    }

    internal fun parseDocumentOrNull(lyricsText: String): LyricsDocument? {
        val format = detectFormat(lyricsText) ?: return null
        return safely {
            LyricsDocumentPipeline.parse(lyricsText, format)
        }
    }

    private inline fun <T> safely(parse: () -> T?): T? {
        return try {
            parse()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
    }
}
