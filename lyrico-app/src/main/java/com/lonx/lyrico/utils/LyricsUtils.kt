package com.lonx.lyrico.utils

import android.annotation.SuppressLint
import com.github.houbb.opencc4j.util.ZhHkConverterUtil
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat.*
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

    /**
     * @param result 原始歌词结果
     * @param conversionMode 转换模式
     * @return 转换后的 LyricsResult
     */
    fun convertLyricsResult(
        result: LyricsResult,
        conversionMode: ConversionMode
    ): LyricsResult {
        if (conversionMode == ConversionMode.NONE) return result
        
        return result.copy(
            original = convertLyricsLineList(result.original, conversionMode),
            translated = result.translated?.let { convertLyricsLineList(it, conversionMode) },
            romanization = result.romanization?.let { convertLyricsLineList(it, conversionMode) },
            tags = convertTags(result.tags, conversionMode)
        )
    }

    /**
     * 转换一个 List<LyricsLine> 中的所有文本
     */
    private fun convertLyricsLineList(
        lines: List<LyricsLine>,
        conversionMode: ConversionMode
    ): List<LyricsLine> {
        return lines.map { line ->
            line.copy(
                words = line.words.map { word ->
                    word.copy(text = convertText(word.text, conversionMode))
                }
            )
        }
    }

    /**
     * 转换元数据 tags（如歌手、歌名、专辑等）
     */
    private fun convertTags(
        tags: Map<String, String>,
        conversionMode: ConversionMode
    ): Map<String, String> {
        return tags.mapValues { (_, value) ->
            convertText(value, conversionMode)
        }
    }

    /**
     * 转换单个文本段
     */
    private fun convertText(text: String, conversionMode: ConversionMode): String {
        return when (conversionMode) {
            ConversionMode.TRADITIONAL_TO_SIMPLIFIED -> ZhHkConverterUtil.toSimple(text)
            ConversionMode.SIMPLIFIED_TO_TRADITIONAL -> ZhHkConverterUtil.toTraditional(text)
            else -> text
        }
    }

    /**
     * 对纯文本歌词字符串进行简繁转换
     * @param lyricsText 歌词全文
     * @param conversionMode 转换模式
     * @return 转换后的歌词字符串
     */
    fun convertLyricsText(lyricsText: String, conversionMode: ConversionMode): String {
        if (conversionMode == ConversionMode.NONE || lyricsText.isBlank()) return lyricsText

        // 匹配所有时间戳 token（LRC 和 TTML），对非时间戳部分做转换
        val timeTokenPattern = Regex(
            """[\[<]\d{2,}:\d{2}\.\d{2,3}[>\]]""" +          // LRC: [01:23.456] 或 <01:23.456>
            """|begin="\d{2,}:\d{2}:\d{2}\.\d{2,3}"""" +     // TTML begin
            """|end="\d{2,}:\d{2}:\d{2}\.\d{2,3}""""         // TTML end
        )

        val result = StringBuilder()
        var lastEnd = 0

        timeTokenPattern.findAll(lyricsText).forEach { match ->
            // 转换时间戳之前的文本部分
            if (match.range.first > lastEnd) {
                val textSegment = lyricsText.substring(lastEnd, match.range.first)
                result.append(convertText(textSegment, conversionMode))
            }
            // 时间戳原样保留
            result.append(match.value)
            lastEnd = match.range.last + 1
        }

        // 处理最后一段文本
        if (lastEnd < lyricsText.length) {
            result.append(convertText(lyricsText.substring(lastEnd), conversionMode))
        }

        return result.toString()
    }

    fun formatLrcResult(
        result: LyricsResult,
        config: LyricRenderConfig,
        offset: Long = 0L,
    ): String {
        val convertedResult = convertLyricsResult(result, config.conversionMode)
        
        val builder = StringBuilder()
        val isWordLevel = convertedResult.isWordByWord
        val isTtml = config.format == TTML
        // 如果是 TTML，先追加 XML 头部和根节点
        if (isTtml) {
            builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            builder.append("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" xmlns:itunes=\"http://music.apple.com/itunes/ttml\">\n")
            builder.append("  <body>\n    <div>\n")
        }

        val romanMap = if (config.showRomanization) {
            convertedResult.romanization?.associateBy { it.start } ?: emptyMap()
        } else emptyMap()

        val translatedMap = if (config.showTranslation) {
            convertedResult.translated?.associateBy { it.start } ?: emptyMap()
        } else emptyMap()

        convertedResult.original.forEach { line ->
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

            if (isTtml) {
                appendTtmlCombinedLine(
                    builder, line, matchedRoman, matchedTranslation, offset, config, isWordLevel
                )
                builder.append("\n")
                return@forEach // TTML 处理完毕直接返回下一行
            }

            val skipOriginal = config.onlyTranslationIfAvailable && matchedTranslation != null

            if (!skipOriginal) {
                when (config.format) {
                    PLAIN_LRC -> appendLineByLine(builder, line, offset)
                    ENHANCED_LRC -> {
                        if (isWordLevel) appendEnhancedLine(builder, line, offset)
                        else appendLineByLine(builder, line, offset) //  LRC 降级
                    }
                    VERBATIM_LRC -> {
                        if (isWordLevel) appendWordByWord(builder, line, offset)
                        else appendLineByLine(builder, line, offset) // LRC 降级
                    }
                }
                builder.append("\n")
            }

            // 处理音译的非 TTML 输出
            if (matchedRoman != null && !skipOriginal) {
                appendLineByLine(builder, matchedRoman, offset)
                builder.append("\n")
            }

            if (matchedTranslation != null) {
                appendLineByLine(builder, matchedTranslation, offset)
                builder.append("\n")
            }
        }

        // 如果是 TTML，追加闭合标签
        if (isTtml) {
            builder.append("    </div>\n  </body>\n</tt>")
        }

        return builder.toString().trim()
    }


    private fun appendTtmlCombinedLine(
        builder: StringBuilder,
        line: LyricsLine,
        romanLine: LyricsLine?,
        transLine: LyricsLine?,
        offset: Long,
        config: LyricRenderConfig,
        isWordLevel: Boolean // 歌词数据是否是逐字
    ) {
        if (line.words.isEmpty()) return

        val start = applyOffset(line.start, offset)
        // 确定该行的结束时间：以原文最后一个词的结束时间为准
        val lastWord = line.words.last()
        val end = when {
            lastWord.end > 0 -> lastWord.end
            lastWord.start > 0 -> lastWord.start + 300
            else -> line.start + 2000
        }

        val startStr = formatTtmlTimestamp(start)
        val endStr = formatTtmlTimestamp(applyOffset(end, offset))

        builder.append("      <p begin=\"").append(startStr).append("\" end=\"").append(endStr).append("\">")

        val showOriginal = !(config.onlyTranslationIfAvailable && transLine != null)
        if (showOriginal) {
            if (isWordLevel) {
                // 如果支持逐字，输出详细的 <span>
                line.words.forEach { word ->
                    val wordStart = formatTtmlTimestamp(applyOffset(word.start, offset))
                    val wordEnd = if (word.end > 0) word.end else word.start + 300
                    val wordEndStr = formatTtmlTimestamp(applyOffset(wordEnd, offset))

                    builder.append("<span begin=\"").append(wordStart).append("\" end=\"").append(wordEndStr).append("\">")
                    builder.append(escapeXml(word.text))
                    builder.append("</span>")
                }
            } else {
                val fullText = line.words.joinToString("") { it.text }
                builder.append(escapeXml(fullText))
            }
        }

        if (romanLine != null && showOriginal) {
            val romanText = romanLine.words.joinToString(" ") { it.text }
            if (romanText.isNotEmpty()) {
                builder.append("<span ttm:role=\"x-romanization\">")
                builder.append(escapeXml(romanText))
                builder.append("</span>")
            }
        }

        if (transLine != null) {
            val transText = transLine.words.joinToString("") { it.text }
            if (transText.isNotEmpty()) {
                builder.append(" <span ttm:role=\"x-translation\">")
                builder.append(escapeXml(transText))
                builder.append("</span>")
            }
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
//        val endTime = line.words.lastOrNull()?.end

        // 应用 offset
        val startTimeFormatted = formatTimestamp(applyOffset(line.start, offset))

        builder.append("[$startTimeFormatted]$lineText")
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