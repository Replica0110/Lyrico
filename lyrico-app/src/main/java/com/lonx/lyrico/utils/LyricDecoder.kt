package com.lonx.lyrico.utils

import android.util.Log
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.utils.lyrics.document.LyricsDocumentPipeline

object LyricDecoder {
    private const val TAG = "LyricDecoder"

    fun detectFormat(lyricsText: String): LyricFormat? {
        if (lyricsText.isBlank()) return null

        // TTML 是 XML 文档：仅当文档以 <tt> 根元素开头时才判定为 TTML。
        // 不能因 LRC 正文任意位置出现 "<tt " 或 ttml 命名空间字符串而误判整份歌词。
        if (looksLikeTtml(lyricsText)) {
            return LyricFormat.TTML
        }

        val sampleLines = lyricsText.lines().filter { it.isNotBlank() }

        var hasEnhanced = false
        var hasVerbatim = false
        var hasPlain = false

        for (line in sampleLines) {
            if (line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:")) continue

            val matches = LyricFormatter.LRC_TIME_PATTERN.findAll(line).toList()
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
            hasEnhanced -> LyricFormat.ENHANCED_LRC
            hasVerbatim -> LyricFormat.VERBATIM_LRC
            hasPlain -> LyricFormat.PLAIN_LRC
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
        if (!text.startsWith("<tt")) return false
        val boundary = text.getOrNull(3) ?: return false
        return boundary == '>' || boundary == '/' || boundary.isWhitespace()
    }

    /**
     * 对外安全解析入口：音频标签中的歌词是不可信输入，
     * parser 异常不允许穿透到 UI / 扫描流程，解析失败返回 null。
     */
    fun decode(lyricsText: String): LyricsResult? {
        val format = detectFormat(lyricsText) ?: return null
        return runCatching {
            LyricsDocumentPipeline.parse(lyricsText, format)?.let { document ->
                with(LyricsDocumentPipeline) { document.toLyricsResult() }
            }
        }.onFailure { e ->
            Log.w(
                TAG,
                "Failed to decode lyrics as $format " +
                    "(length=${lyricsText.length}, preview=${lyricsText.take(80).replace('\n', ' ')}): ${e.message}"
            )
        }.getOrNull()
    }
}
