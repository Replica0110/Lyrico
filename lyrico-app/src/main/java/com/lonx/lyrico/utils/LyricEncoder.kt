package com.lonx.lyrico.utils

import android.annotation.SuppressLint
import com.github.houbb.opencc4j.util.ZhHkConverterUtil
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat.*
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsResult

object LyricEncoder {
    // 匹配 TTML 格式: begin="00:01:23.456" 或 end="00:01:23.456"
    private val TTML_TIME_PATTERN = Regex("(begin=\"|end=\")(\\d{2,}):(\\d{2}):(\\d{2})\\.(\\d{2,3})(\")")
    
    /**
     * 计算应用偏移量，保证结果大于等于 0
     */
    private fun applyOffset(time: Long, offset: Long): Long {
        return LyricFormatter.applyOffset(time, offset)
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
            """|begin="\d{2,}:\d{2}:\d{2}\.\d{2,3}""" +     // TTML begin
            """|end="\d{2,}:\d{2}:\d{2}\.\d{2,3}"""         // TTML end
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
    
    /**
     * 从 LyricsResult 提取纯文本歌词（不包含时间轴和格式标记）
     * @param result 歌词结果
     * @param config 渲染配置（控制是否包含翻译、音译等）
     * @param conversionMode 简繁转换模式
     * @return 纯文本歌词，每行一句
     */
    fun encodePlainText(
        result: LyricsResult,
        config: LyricRenderConfig,
        conversionMode: ConversionMode = ConversionMode.NONE
    ): String {

        val convertedResult = convertLyricsResult(result, conversionMode)
        val builder = StringBuilder()
    
        val romanMap = if (config.showRomanization) {
            alignSubLines(convertedResult.original, convertedResult.romanization)
        } else {
            emptyMap()
        }
    
        val translatedMap = if (config.showTranslation) {
            alignSubLines(convertedResult.original, convertedResult.translated)
        } else {
            emptyMap()
        }
    
        convertedResult.original.forEach { line ->
            if (config.removeEmptyLines && isBlankOrPlaceholder(line)) {
                return@forEach
            }
    
            val matchedTranslation = if (config.showTranslation) {
                val match = translatedMap[line.start]
                if (config.removeEmptyLines && match != null && isBlankOrPlaceholder(match)) null else match
            } else null
    
            val matchedRoman = if (config.showRomanization) {
                val match = romanMap[line.start]
                if (config.removeEmptyLines && match != null && isBlankOrPlaceholder(match)) null else match
            } else null
    
            val skipOriginal = config.onlyTranslationIfAvailable && matchedTranslation != null
    
            // 添加原文
            if (!skipOriginal) {
                val originalText = line.words.joinToString("") { it.text }
                if (originalText.isNotBlank()) {
                    builder.append(originalText)
                    builder.append("\n")
                }
            }
    
            // 添加音译
            if (matchedRoman != null && !skipOriginal) {
                val romanText = matchedRoman.words.joinToString(" ") { it.text }
                if (romanText.isNotBlank()) {
                    builder.append(romanText)
                    builder.append("\n")
                }
            }
    
            // 添加翻译
            if (matchedTranslation != null) {
                val transText = matchedTranslation.words.joinToString("") { it.text }
                if (transText.isNotBlank()) {
                    builder.append(transText)
                    builder.append("\n")
                }
            }
        }
    
        return builder.toString().trim()
    }

    fun encode(
        result: LyricsResult,
        config: LyricRenderConfig,
        offset: Long = 0L,
    ): String {
        selectRawLyrics(result, config)?.let { raw ->
            val converted = convertLyricsText(raw, config.conversionMode)
            return shiftLyricsOffset(converted, offset).trim()
        }
        encodeFallbackRawLyrics(result, config, offset)?.let {
            return it
        }

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
            alignSubLines(convertedResult.original, convertedResult.romanization)
        } else {
            emptyMap()
        }

        val translatedMap = if (config.showTranslation) {
            alignSubLines(convertedResult.original, convertedResult.translated)
        } else {
            emptyMap()
        }

        convertedResult.original.forEach { line ->
            if (config.removeEmptyLines && isBlankOrPlaceholder(line)) {
                return@forEach
            }

            val matchedTranslation = if (config.showTranslation) {
                val match = translatedMap[line.start]
                if (config.removeEmptyLines && match != null && isBlankOrPlaceholder(match)) null else match
            } else null

            val matchedRoman = if (config.showRomanization) {
                val match = romanMap[line.start]
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

    private fun selectRawLyrics(
        result: LyricsResult,
        config: LyricRenderConfig
    ): String? {
        val raw = when (config.format) {
            PLAIN_LRC -> result.rawPlainLrc
            VERBATIM_LRC -> result.rawVerbatimLrc
            ENHANCED_LRC -> result.rawEnhancedLrc
            TTML -> result.rawTtml
        }

        return raw.takeIf { it.isNotBlank() }
    }

    private fun encodeFallbackRawLyrics(
        result: LyricsResult,
        config: LyricRenderConfig,
        offset: Long
    ): String? {
        val fallbackRaw = when (config.format) {
            PLAIN_LRC -> listOf(result.rawVerbatimLrc, result.rawEnhancedLrc, result.rawTtml)
            VERBATIM_LRC -> listOf(result.rawEnhancedLrc, result.rawPlainLrc, result.rawTtml)
            ENHANCED_LRC -> listOf(result.rawVerbatimLrc, result.rawPlainLrc, result.rawTtml)
            TTML -> listOf(result.rawEnhancedLrc, result.rawVerbatimLrc, result.rawPlainLrc)
        }

        fallbackRaw
            .firstOrNull { it.isNotBlank() }
            ?.let { raw ->
                LyricDecoder.decode(raw)?.let { decoded ->
                    return encode(decoded, config, offset).takeIf { it.isNotBlank() }
                }
            }


        return null
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

        val startStr = LyricFormatter.formatTtmlTimestamp(start)
        val endStr = LyricFormatter.formatTtmlTimestamp(LyricFormatter.applyOffset(end, offset))

        builder.append("      <p begin=\"").append(startStr).append("\" end=\"").append(endStr).append("\">")

        val showOriginal = !(config.onlyTranslationIfAvailable && transLine != null)
        if (showOriginal) {
            if (isWordLevel) {
                // 如果支持逐字，输出详细的 <span>
                line.words.forEach { word ->
                    val wordStart = LyricFormatter.formatTtmlTimestamp(LyricFormatter.applyOffset(word.start, offset))
                    val wordEnd = if (word.end > 0) word.end else word.start + 300
                    val wordEndStr = LyricFormatter.formatTtmlTimestamp(LyricFormatter.applyOffset(wordEnd, offset))

                    builder.append("<span begin=\"").append(wordStart).append("\" end=\"").append(wordEndStr).append("\">")
                    builder.append(LyricFormatter.escapeXml(word.text))
                    builder.append("</span>")
                }
            } else {
                val fullText = line.words.joinToString("") { it.text }
                builder.append(LyricFormatter.escapeXml(fullText))
            }
        }

        if (romanLine != null && showOriginal) {
            val romanText = romanLine.words.joinToString("") { it.text }
            if (romanText.isNotEmpty()) {
                builder.append("<span ttm:role=\"x-romanization\">")
                builder.append(LyricFormatter.escapeXml(romanText))
                builder.append("</span>")
            }
        }

        if (transLine != null) {
            val transText = transLine.words.joinToString("") { it.text }
            if (transText.isNotEmpty()) {
                builder.append("<span ttm:role=\"x-translation\">")
                builder.append(LyricFormatter.escapeXml(transText))
                builder.append("</span>")
            }
        }

        builder.append("</p>")
    }

    private fun appendEnhancedLine(builder: StringBuilder, line: LyricsLine, offset: Long) {
        if (line.words.isEmpty()) return

        val start = LyricFormatter.applyOffset(line.start, offset)
        builder.append("[${LyricFormatter.formatTimestamp(start)}] ")

        line.words.forEach { word ->
            val wordStart = LyricFormatter.applyOffset(word.start, offset)
            builder.append("<${LyricFormatter.formatTimestamp(wordStart)}>")
            builder.append(word.text)
        }

        val lastWord = line.words.last()

        val end = when {
            lastWord.end > 0 -> lastWord.end
            lastWord.start > 0 -> lastWord.start + 100
            else -> line.start + 2000
        }

        builder.append("<${LyricFormatter.formatTimestamp(LyricFormatter.applyOffset(end, offset))}>")
    }

    private fun appendLineByLine(builder: StringBuilder, line: LyricsLine, offset: Long) {
        val lineText = line.words.joinToString("") { it.text }
//        val endTime = line.words.lastOrNull()?.end

        // 应用 offset
        val startTimeFormatted = LyricFormatter.formatTimestamp(LyricFormatter.applyOffset(line.start, offset))

        builder.append("[$startTimeFormatted]$lineText")
    }

    private fun appendWordByWord(builder: StringBuilder, line: LyricsLine, offset: Long) {
        line.words.forEachIndexed { index, word ->

            val startFormatted = LyricFormatter.formatTimestamp(LyricFormatter.applyOffset(word.start, offset))

            if (index == line.words.lastIndex) {

                val end = if (word.end > 0) word.end else word.start + 100
                val endFormatted = LyricFormatter.formatTimestamp(LyricFormatter.applyOffset(end, offset))

                builder.append("[$startFormatted]${word.text}[$endFormatted]")

            } else {
                builder.append("[$startFormatted]${word.text}")
            }
        }
    }

    private fun alignSubLines(
        originalLines: List<LyricsLine>,
        subLines: List<LyricsLine>?
    ): Map<Long, LyricsLine> {
        if (originalLines.isEmpty() || subLines.isNullOrEmpty()) return emptyMap()

        val subLinesByStart = subLines.groupBy { it.start }
        val usedCounts = mutableMapOf<Long, Int>()

        return originalLines.mapNotNull { originalLine ->
            val sameTimeSubLines = subLinesByStart[originalLine.start].orEmpty()
            val usedCount = usedCounts[originalLine.start] ?: 0
            val matchedLine = sameTimeSubLines.getOrNull(usedCount) ?: return@mapNotNull null
            usedCounts[originalLine.start] = usedCount + 1
            originalLine.start to matchedLine
        }.toMap()
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
        resultText = LyricFormatter.LRC_TIME_PATTERN.replace(resultText) { match ->
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
