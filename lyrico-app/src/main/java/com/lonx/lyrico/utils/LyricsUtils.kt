package com.lonx.lyrico.utils

import android.annotation.SuppressLint
import com.github.houbb.opencc4j.util.ZhHkConverterUtil
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsResult
import kotlin.math.abs

object LyricsUtils {
    // 匹配 LRC/Enhanced LRC 格式: [01:23.456] 或 <01:23.45>
    private val LRC_TIME_PATTERN = Regex("([<\\[])(\\d{2,}):(\\d{2})\\.(\\d{2,3})([>\\]])")

    // 匹配 TTML 格式: begin="00:01:23.456" 或 end="00:01:23.456"
    private val TTML_TIME_PATTERN = Regex("(begin=\"|end=\")(\\d{2,}):(\\d{2}):(\\d{2})\\.(\\d{2,3})(\")")
    @SuppressLint("DefaultLocale")
    private fun formatTimestamp(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        val totalSeconds = safeMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val ms = safeMillis % 1000
        return String.format("%02d:%02d.%03d", minutes, seconds, ms)
    }

    /**
     * TTML 专属时间戳 (格式: HH:mm:ss.SSS)
     */
    @SuppressLint("DefaultLocale")
    private fun formatTtmlTimestamp(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        val totalSeconds = safeMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val ms = safeMillis % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms)
    }

    /**
     * XML 字符转义（防止歌词中的特殊字符破坏 TTML 结构）
     */
    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * 计算应用偏移量，保证结果大于等于 0
     */
    private fun applyOffset(time: Long, offset: Long): Long {
        return (time + offset).coerceAtLeast(0L)
    }

    private fun isBlankOrPlaceholder(line: LyricsLine): Boolean {
        val text = line.words.joinToString("") { it.text }.trim()
        return text.isEmpty() || text.matches(Regex("^[\\s/]*$"))
    }

    fun formatLrcResult(
        result: LyricsResult,
        config: LyricRenderConfig,
        offset: Long = 0L,
    ): String {
        val builder = StringBuilder()
        val isTtml = config.format == LyricFormat.TTML

        // 如果是 TTML，先追加 XML 头部和根节点
        if (isTtml) {
            builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            builder.append("<tt xmlns=\"http://www.w3.org/ns/ttml\">\n<body>\n<div>\n")
        }

        val romanMap = if (config.showRomanization) {
            result.romanization?.associateBy { it.start } ?: emptyMap()
        } else emptyMap()

        val translatedMap = if (config.showTranslation) {
            result.translated?.associateBy { it.start } ?: emptyMap()
        } else emptyMap()

        result.original.forEach { line ->
            if (config.removeEmptyLines && isBlankOrPlaceholder(line)) {
                return@forEach
            }

            val matchedTranslation = if (config.showTranslation) {
                val match = matchingSubLine(line, translatedMap)
                if (config.removeEmptyLines && match != null && isBlankOrPlaceholder(match)) null else match
            } else null

            val matchedRoman = if (config.showRomanization) {
                val match = matchingSubLine(line, romanMap)
                if (config.removeEmptyLines && match != null && isBlankOrPlaceholder(match)) null else match
            } else null

            val skipOriginal = config.onlyTranslationIfAvailable && matchedTranslation != null

            if (!skipOriginal) {
                when (config.format) {
                    LyricFormat.ENHANCED_LRC -> appendEnhancedLine(builder, line, offset)
                    LyricFormat.PLAIN_LRC -> appendLineByLine(builder, line, offset)
                    LyricFormat.VERBATIM_LRC -> appendWordByWord(builder, line, offset)
                    LyricFormat.TTML -> appendTtmlLine(builder, line, offset)
                }
                builder.append('\n')
            }

            if (matchedRoman != null && !skipOriginal) {
                when (config.format) {
                    LyricFormat.ENHANCED_LRC -> appendEnhancedLine(builder, matchedRoman, offset)
                    LyricFormat.PLAIN_LRC -> appendLineByLine(builder, matchedRoman, offset)
                    LyricFormat.VERBATIM_LRC -> appendWordByWord(builder, matchedRoman, offset)
                    LyricFormat.TTML -> appendTtmlLine(builder, matchedRoman, offset)
                }
                builder.append('\n')
            }

            if (matchedTranslation != null) {
                // 翻译行也需要区分 TTML 还是 LRC
                if (isTtml) {
                    appendTtmlLine(builder, matchedTranslation, offset)
                } else {
                    builder.append(formatPlainLine(matchedTranslation, offset))
                }
                builder.append('\n')
            }
        }

        // 如果是 TTML，追加闭合标签
        if (isTtml) {
            builder.append("</div>\n</body>\n</tt>")
        }
        val lyrics = when (config.conversionMode) {
            ConversionMode.TRADITIONAL_TO_SIMPLIFIED -> {
                ZhHkConverterUtil.toSimple(builder.toString())
            }
            ConversionMode.SIMPLIFIED_TO_TRADITIONAL -> {
                ZhHkConverterUtil.toTraditional(builder.toString())
            }
            else -> {
                builder.toString()
            }
        }

        return lyrics.trim()
    }

    /**
     * TTML 行生成逻辑 (逐字生成 span 支持卡拉OK效果)
     */
    private fun appendTtmlLine(builder: StringBuilder, line: LyricsLine, offset: Long) {
        if (line.words.isEmpty()) return

        val start = applyOffset(line.start, offset)
        val lastWord = line.words.last()
        val end = when {
            lastWord.end > 0 -> lastWord.end
            lastWord.start > 0 -> lastWord.start + 300
            else -> line.start + 2000
        }

        val startStr = formatTtmlTimestamp(start)
        val endStr = formatTtmlTimestamp(applyOffset(end, offset))

        builder.append("  <p begin=\"").append(startStr).append("\" end=\"").append(endStr).append("\">")

        line.words.forEach { word ->
            val wordStart = formatTtmlTimestamp(applyOffset(word.start, offset))
            val wordEnd = if (word.end > 0) word.end else word.start + 300
            val wordEndStr = formatTtmlTimestamp(applyOffset(wordEnd, offset))

            builder.append("<span begin=\"").append(wordStart).append("\" end=\"").append(wordEndStr).append("\">")
            builder.append(escapeXml(word.text))
            builder.append("</span>")
        }

        builder.append("</p>")
    }

    private fun appendEnhancedLine(builder: StringBuilder, line: LyricsLine, offset: Long) {
        if (line.words.isEmpty()) return

        val start = applyOffset(line.start, offset)
        builder.append("[${formatTimestamp(start)}] ")

        line.words.forEach { word ->
            val wordStart = applyOffset(word.start, offset)
            builder.append("<${formatTimestamp(wordStart)}>")
            builder.append(word.text)
        }

        val lastWord = line.words.last()

        val end = when {
            lastWord.end > 0 -> lastWord.end
            lastWord.start > 0 -> lastWord.start + 100
            else -> line.start + 2000
        }

        builder.append(" <${formatTimestamp(applyOffset(end, offset))}>")
    }

    private fun appendLineByLine(builder: StringBuilder, line: LyricsLine, offset: Long) {
        val lineText = line.words.joinToString("") { it.text }
        val endTime = line.words.lastOrNull()?.end

        // 应用 offset
        val startTimeFormatted = formatTimestamp(applyOffset(line.start, offset))

        if (endTime != null) {
            val endTimeFormatted = formatTimestamp(applyOffset(endTime, offset))
            builder.append("[$startTimeFormatted]$lineText[$endTimeFormatted]")
        } else {
            builder.append("[$startTimeFormatted]$lineText")
        }
    }

    private fun appendWordByWord(builder: StringBuilder, line: LyricsLine, offset: Long) {
        line.words.forEachIndexed { index, word ->

            val startFormatted = formatTimestamp(applyOffset(word.start, offset))

            if (index == line.words.lastIndex) {

                val end = if (word.end > 0) word.end else word.start + 100
                val endFormatted = formatTimestamp(applyOffset(end, offset))

                builder.append("[$startFormatted]${word.text}[$endFormatted]")

            } else {
                builder.append("[$startFormatted]${word.text}")
            }
        }
    }

    private fun formatPlainLine(line: LyricsLine, offset: Long): String {
        val startFormatted = formatTimestamp(applyOffset(line.start, offset))
        val end = line.words.lastOrNull()?.end ?: (line.start + 2000)
        val endFormatted = formatTimestamp(applyOffset(end, offset))

        return "[$startFormatted]" +
                line.words.joinToString(" ") { it.text } +
                "[$endFormatted]"
    }

    private fun matchingSubLine(
        originalLine: LyricsLine,
        subLineMap: Map<Long, LyricsLine>
    ): LyricsLine? {
        val matched = subLineMap[originalLine.start]
        if (matched != null) return matched
        return subLineMap.entries.find { abs(it.key - originalLine.start) < 300 }?.value
    }
    /**
     * 对纯文本歌词字符串进行整体时间偏移
     * @param lyricsText 歌词全文 (支持 LRC, Enhanced LRC, Verbatim, TTML)
     * @param offset 偏移量（毫秒），正数表示时间延后，负数表示时间提前
     * @return 调整时间戳后的歌词字符串
     */
    @SuppressLint("DefaultLocale")
    fun shiftLyricsOffset(lyricsText: String, offset: Long): String {
        if (offset == 0L || lyricsText.isBlank()) return lyricsText

        var resultText = lyricsText

        // 处理 LRC 格式的时间戳 ([mm:ss.xxx] 或 <mm:ss.xxx>)
        resultText = LRC_TIME_PATTERN.replace(resultText) { match ->
            val prefix = match.groupValues[1] // '[' 或 '<'
            val min = match.groupValues[2].toLong()
            val sec = match.groupValues[3].toLong()
            val msStr = match.groupValues[4]
            val suffix = match.groupValues[5] // ']' 或 '>'

            // 将毫秒补齐到3位，例如 .12 -> 120ms, .5 -> 500ms
            val ms = msStr.padEnd(3, '0').toLong()

            // 计算总毫秒并加上偏移量，确保不小于0
            val totalMs = (min * 60 + sec) * 1000 + ms
            val newTotalMs = (totalMs + offset).coerceAtLeast(0L)

            // 重新计算分、秒、毫秒
            val newMin = newTotalMs / 60000
            val newSec = (newTotalMs % 60000) / 1000
            val newMs = newTotalMs % 1000

            // 保持原有的括号类型，并将时间标准化为 3位毫秒
            String.format("%s%02d:%02d.%03d%s", prefix, newMin, newSec, newMs, suffix)
        }

        // 处理 TTML 格式的时间戳 (begin="HH:mm:ss.SSS" / end="HH:mm:ss.SSS")
        resultText = TTML_TIME_PATTERN.replace(resultText) { match ->
            val prefix = match.groupValues[1] // 'begin="' 或 'end="'
            val hr = match.groupValues[2].toLong()
            val min = match.groupValues[3].toLong()
            val sec = match.groupValues[4].toLong()
            val msStr = match.groupValues[5]
            val suffix = match.groupValues[6] // '"'

            val ms = msStr.padEnd(3, '0').toLong()

            val totalMs = (hr * 3600 + min * 60 + sec) * 1000 + ms
            val newTotalMs = (totalMs + offset).coerceAtLeast(0L)

            val newHr = newTotalMs / 3600000
            val newMin = (newTotalMs % 3600000) / 60000
            val newSec = (newTotalMs % 60000) / 1000
            val newMs = newTotalMs % 1000

            String.format("%s%02d:%02d:%02d.%03d%s", prefix, newHr, newMin, newSec, newMs, suffix)
        }

        return resultText
    }
}