package com.lonx.lyrico.utils

import android.annotation.SuppressLint
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.lyrics.DefaultLyricLineOrder
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricFormat.*
import com.lonx.lyrico.data.model.lyrics.LyricLineTrack
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.LyricsAgentEntry
import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsMetadataElement
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.isRaw
import com.lonx.lyrico.utils.lyrics.document.LyricsDocumentPipeline

object LyricEncoder {
    // 匹配 TTML 格式: begin="00:01:23.456" 或 end="00:01:23.456"
    private val TTML_TIME_PATTERN = Regex("(begin=\"|end=\")(\\d{2,}):(\\d{2}):(\\d{2})\\.(\\d{2,3})(\")")

    // 行级扩展属性：itunes:songPart 提取用于 <div> 分组（AMLL 规范 7.1），不输出到 <p>
    private const val SONGPART_ATTR = "itunes:songPart"
    // 行级扩展属性：段落时间窗（API 行表 div_begin/div_end，段首行携带），
    // 用于 <div> 重建时写入 begin/end，不输出到 <p>
    private const val DIV_BEGIN_ATTR = "divBegin"
    private const val DIV_END_ATTR = "divEnd"
    // head 元数据元素树中的官方 iTunesMetadata 成员（写入 <iTunesMetadata> 容器内）
    private const val META_SONGWRITERS = "songwriters"
    // 安全 XML 名称（元素/属性名白名单：字母或下划线开头，仅含字母数字 _ . : -）
    private val SAFE_XML_NAME = Regex("[A-Za-z_][A-Za-z0-9_.:-]*")
    
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
            ConversionMode.TRADITIONAL_TO_SIMPLIFIED -> ZhConverterUtil.toSimple(text)
            ConversionMode.SIMPLIFIED_TO_TRADITIONAL -> ZhConverterUtil.toTraditional(text)
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

            config.normalizedLineOrder.forEach { track ->
                val trackLine = when (track) {
                    LyricLineTrack.ORIGINAL -> line.takeUnless { skipOriginal }
                    LyricLineTrack.ROMANIZATION -> matchedRoman.takeUnless { skipOriginal }
                    LyricLineTrack.TRANSLATION -> matchedTranslation
                } ?: return@forEach

                val separator = if (track == LyricLineTrack.ROMANIZATION) " " else ""
                val transText = trackLine.words.joinToString(separator) { it.text }
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
        if (result.payloadType.isRaw()) {
            if (shouldUseDocumentPipeline(result, config, offset)) {
                LyricsDocumentPipeline.processRawResult(result, config, offset)?.let {
                    return it
                }
            }

            encodeRawWithRenderConfig(selectRawLyrics(result, config), config, offset)?.let {
                return it
            }

            selectRawLyrics(result, config)?.let { raw ->
                val converted = convertLyricsText(raw, config.conversionMode)
                return shiftLyricsOffset(converted, offset).trim()
            }

            encodeFallbackRawLyrics(result, config, offset)?.let {
                return it
            }
        }

        if (result.original.isEmpty()) {
            val selectedRaw = selectRawLyrics(result, config)

            if (shouldUseDocumentPipeline(result, config, offset)) {
                LyricsDocumentPipeline.processRawResult(result, config, offset)?.let {
                    return it
                }
            }

            encodeRawWithRenderConfig(selectedRaw, config, offset)?.let {
                return it
            }

            selectedRaw?.let { raw ->
                val converted = convertLyricsText(raw, config.conversionMode)
                return shiftLyricsOffset(converted, offset).trim()
            }

            encodeFallbackRawLyrics(result, config, offset)?.let {
                return it
            }
        }

        val convertedResult = convertLyricsResult(result, config.conversionMode)

        val builder = StringBuilder()
        val isWordLevel = convertedResult.isWordByWord
        val isTtml = config.format == TTML
        // 如果是 TTML，先追加 XML 头部和根节点；正文暂存 bodyBuilder，循环结束后按需插入 head（词级音译 sidecar）
        val bodyBuilder = StringBuilder()
        val romanSidecar = mutableListOf<Pair<String, LyricsLine>>()
        if (isTtml) {
            builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            // itunes 命名空间统一用 AMLL 规范 URI（http://music.apple.com/lyric-ttml-internal），
            // 与 TtmlWriter / head 内 <iTunesMetadata> 的 xmlns 保持一致，避免同前缀双 URI
            builder.append("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" xmlns:itunes=\"http://music.apple.com/lyric-ttml-internal\"")
            // 根属性透传（structured 协议顶层字段）：
            // itunes:timing = 词级时间标志（如 "Word"）；xml:lang = 原文语言码（BCP47 如 zh-Hans）
            if (convertedResult.timing.isNotBlank()) {
                builder.append(" itunes:timing=\"")
                    .append(LyricFormatter.escapeXml(convertedResult.timing)).append("\"")
            }
            if (convertedResult.language.isNotBlank()) {
                builder.append(" xml:lang=\"")
                    .append(LyricFormatter.escapeXml(convertedResult.language)).append("\"")
            }
            // metadata 透传元素的自定义前缀命名空间（如 amll:）需在根节点补 xmlns 声明，否则输出 XML 非法
            collectExtraNamespaces(convertedResult.metadata).forEach { (prefix, uri) ->
                builder.append(" xmlns:").append(prefix).append("=\"")
                    .append(LyricFormatter.escapeXml(uri)).append("\"")
            }
            builder.append(">\n")
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

        // TTML 正文按 itunes:songPart 分组重建 <div>（AMLL 规范 7.1）：
        // 连续相同 songPart 的行归入同一 <div itunes:songPart="...">，无 songPart 的行进默认 <div>；
        // 段首行携带 divBegin/divEnd（API 行表段落时间窗）时写入 <div> 的 begin/end（保真往返）
        var currentSongPart: String? = null
        var divOpen = false

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
                // songPart 取自行级扩展属性；变化时关闭当前 div、开新 div。
                // divBegin（段首行携带的段落时间窗起点）出现时强制开新 div：
                // 两个 songPart 相同但源文件中分属不同 <div> 的段落也能正确分开，时间窗保真往返
                val songPart = line.extensions[SONGPART_ATTR]?.takeIf { it.isNotBlank() }
                val divBegin = line.extensions[DIV_BEGIN_ATTR]?.takeIf { it.isNotBlank() }
                val divEnd = line.extensions[DIV_END_ATTR]?.takeIf { it.isNotBlank() }
                if (songPart != currentSongPart || divBegin != null) {
                    if (divOpen) bodyBuilder.append("    </div>\n")
                    bodyBuilder.append("    <div")
                    songPart?.let {
                        bodyBuilder.append(" ").append(SONGPART_ATTR).append("=\"")
                            .append(LyricFormatter.escapeXml(it)).append("\"")
                    }
                    // 段落时间窗（API 行表 div_begin/div_end → div 的 begin/end，毫秒转 TTML 时间戳）
                    divBegin?.toLongOrNull()?.let {
                        bodyBuilder.append(" begin=\"")
                            .append(LyricFormatter.formatTtmlTimestamp(it)).append("\"")
                    }
                    divEnd?.toLongOrNull()?.let {
                        bodyBuilder.append(" end=\"")
                            .append(LyricFormatter.formatTtmlTimestamp(it)).append("\"")
                    }
                    bodyBuilder.append(">\n")
                    currentSongPart = songPart
                    divOpen = true
                }
                appendTtmlCombinedLine(
                    bodyBuilder, line, matchedRoman, matchedTranslation, offset, config, isWordLevel,
                    convertedResult.translatedLang
                ) { romanLine ->
                    val key = "L${romanSidecar.size + 1}"
                    romanSidecar.add(key to romanLine)
                    key
                }
                bodyBuilder.append("\n")
                return@forEach // TTML 处理完毕直接返回下一行
            }

            val skipOriginal = config.onlyTranslationIfAvailable && matchedTranslation != null

            config.normalizedLineOrder.forEach { track ->
                val trackLine = when (track) {
                    LyricLineTrack.ORIGINAL -> line.takeUnless { skipOriginal }
                    LyricLineTrack.ROMANIZATION -> matchedRoman.takeUnless { skipOriginal }
                    LyricLineTrack.TRANSLATION -> matchedTranslation
                } ?: return@forEach

                // 该轨是否为词级（逐字）数据：
                // - 原文：用全局 isWordByWord（插件声明的逐字标志）；
                // - 音译：单独按本行词数判断——插件给逐字词数组（words.size > 1）时按逐字编码，
                //   旧协议/旧插件音译只有整行（words.size == 1）时降级整行，行为与原先一致；
                // - 翻译：无词级语义，恒整行。
                val trackWordLevel = when (track) {
                    LyricLineTrack.ORIGINAL -> isWordLevel
                    LyricLineTrack.ROMANIZATION -> trackLine.words.size > 1
                    else -> false
                }
                // 音译（拉丁音节等拼音文本）词间补空格分词，汉字原文无分隔符
                val wordSeparator = if (track == LyricLineTrack.ROMANIZATION) " " else ""

                when (config.format) {
                    PLAIN_LRC -> appendLineByLine(builder, trackLine, offset)
                    ENHANCED_LRC -> {
                        if (trackWordLevel) appendEnhancedLine(builder, trackLine, offset, wordSeparator)
                        else appendLineByLine(builder, trackLine, offset) // 无词级数据 → LRC 整行降级
                    }
                    VERBATIM_LRC -> {
                        if (trackWordLevel) appendWordByWord(builder, trackLine, offset, wordSeparator)
                        else appendLineByLine(builder, trackLine, offset) // 无词级数据 → LRC 整行降级
                    }
                    TTML -> Unit
                }
                builder.append("\n")
            }
        }

        // 如果是 TTML，按需写入 head（词级音译 sidecar / 演唱者 / 元数据元素树）后组装正文
        if (isTtml) {
            // metadata 元素树分流：官方 iTunesMetadata 成员（songwriters）进容器内，其余原样透传
            val officialMeta = convertedResult.metadata.filter { it.name == META_SONGWRITERS }
            val passthroughMeta = convertedResult.metadata.filter { it.name != META_SONGWRITERS }
            val hasHead = romanSidecar.isNotEmpty() || convertedResult.agents.isNotEmpty() ||
                officialMeta.isNotEmpty() || passthroughMeta.isNotEmpty()

            if (hasHead) {
                builder.append("  <head>\n")
                // 演唱者列表（structured 协议 agents 字段）→ <ttm:agent>（AMLL 规范 4.1）
                if (convertedResult.agents.isNotEmpty()) {
                    builder.append("    <metadata>\n")
                    convertedResult.agents.forEach { agent ->
                        appendTtmlAgent(builder, agent)
                    }
                    builder.append("    </metadata>\n")
                }
                // 词级音译 sidecar + 官方元数据（songwriters）→ <iTunesMetadata> 容器内
                if (romanSidecar.isNotEmpty() || officialMeta.isNotEmpty()) {
                    builder.append("    <metadata>\n")
                    builder.append("      <iTunesMetadata xmlns=\"http://music.apple.com/lyric-ttml-internal\">\n")
                    if (romanSidecar.isNotEmpty()) {
                        builder.append("        <transliterations>\n")
                        // 音译轨语言码（BCP47 如 zh-Latn-jyutping）：写 transliteration 的 xml:lang（AMLL 规范语言标注）
                        builder.append("          <transliteration")
                        if (convertedResult.romanizationLang.isNotBlank()) {
                            builder.append(" xml:lang=\"")
                                .append(LyricFormatter.escapeXml(convertedResult.romanizationLang)).append("\"")
                        }
                        builder.append(">\n")
                        romanSidecar.forEach { (key, romanLine) ->
                            builder.append("            <text for=\"").append(LyricFormatter.escapeXml(key)).append("\">")
                            appendSidecarRomanText(builder, romanLine, offset)
                            builder.append("</text>\n")
                        }
                        builder.append("          </transliteration>\n")
                        builder.append("        </transliterations>\n")
                    }
                    // 官方 songwriters 结构已在解析侧校验（songwriters 包裹带文本的 songwriter children）
                    officialMeta.forEach { element ->
                        appendMetadataElement(builder, element, "        ")
                    }
                    builder.append("      </iTunesMetadata>\n")
                    builder.append("    </metadata>\n")
                }
                // 非官方元素原样透传（结构解析侧已保留，此处仅序列化）
                if (passthroughMeta.isNotEmpty()) {
                    builder.append("    <metadata>\n")
                    passthroughMeta.forEach { element ->
                        appendMetadataElement(builder, element, "      ")
                    }
                    builder.append("    </metadata>\n")
                }
                builder.append("  </head>\n")
            }
            // body：div 分组结构已在循环内写入 bodyBuilder，此处收尾最后一个未闭合的 div
            if (divOpen) bodyBuilder.append("    </div>\n")
            builder.append("  <body>\n")
            builder.append(bodyBuilder)
            builder.append("  </body>\n</tt>")
        }

        return builder.toString().trim()
    }

    private fun encodeRawWithRenderConfig(
        raw: String?,
        config: LyricRenderConfig,
        offset: Long
    ): String? {
        if (raw.isNullOrBlank()) return null

        val shouldApplyTrackVisibility =
            !config.showTranslation ||
                    !config.showRomanization ||
                    config.onlyTranslationIfAvailable ||
                    lineOrderAffectsLineOutput(config) ||
                    config.removeEmptyLines

        if (!shouldApplyTrackVisibility) return null

        val sourceFormat = LyricDecoder.detectFormat(raw) ?: return null
        return LyricsDocumentPipeline.process(
            raw = raw,
            sourceFormat = sourceFormat,
            targetFormat = config.format,
            conversionMode = config.conversionMode,
            showTranslation = config.showTranslation,
            showRomanization = config.showRomanization,
            onlyTranslationIfAvailable = config.onlyTranslationIfAvailable,
            lineOrder = config.normalizedLineOrder,
            removeEmptyLines = config.removeEmptyLines,
            offset = offset
        )
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
            PLAIN_LRC -> listOf(
                LyricFormat.VERBATIM_LRC to result.rawVerbatimLrc,
                LyricFormat.ENHANCED_LRC to result.rawEnhancedLrc,
                LyricFormat.ENHANCED_LRC to result.rawMultiPersonEnhancedLrc,
                LyricFormat.TTML to result.rawTtml
            )
            VERBATIM_LRC -> listOf(
                LyricFormat.ENHANCED_LRC to result.rawEnhancedLrc,
                LyricFormat.ENHANCED_LRC to result.rawMultiPersonEnhancedLrc,
                LyricFormat.TTML to result.rawTtml,
                LyricFormat.PLAIN_LRC to result.rawPlainLrc
            )
            ENHANCED_LRC -> listOf(
                LyricFormat.VERBATIM_LRC to result.rawVerbatimLrc,
                LyricFormat.ENHANCED_LRC to result.rawMultiPersonEnhancedLrc,
                LyricFormat.TTML to result.rawTtml,
                LyricFormat.PLAIN_LRC to result.rawPlainLrc
            )
            TTML -> listOf(
                LyricFormat.ENHANCED_LRC to result.rawEnhancedLrc,
                LyricFormat.VERBATIM_LRC to result.rawVerbatimLrc,
                LyricFormat.PLAIN_LRC to result.rawPlainLrc
            )
        }

        fallbackRaw
            .firstOrNull { (_, raw) -> raw.isNotBlank() }
            ?.let { (sourceFormat, raw) ->
                LyricsDocumentPipeline.process(
                    raw = raw,
                    sourceFormat = sourceFormat,
                    targetFormat = config.format,
                    conversionMode = config.conversionMode,
                    showTranslation = config.showTranslation,
                    showRomanization = config.showRomanization,
                    onlyTranslationIfAvailable = config.onlyTranslationIfAvailable,
                    lineOrder = config.normalizedLineOrder,
                    removeEmptyLines = config.removeEmptyLines,
                    offset = offset
                )?.let { return it }
            }


        return null
    }

    private fun shouldUseDocumentPipeline(
        result: LyricsResult,
        config: LyricRenderConfig,
        offset: Long
    ): Boolean {
        return offset != 0L ||
                config.conversionMode != ConversionMode.NONE ||
                !config.showTranslation ||
                !config.showRomanization ||
                config.onlyTranslationIfAvailable ||
                lineOrderAffectsLineOutput(config) ||
                config.removeEmptyLines ||
                selectRawLyrics(result, config).isNullOrBlank()
    }

    private fun lineOrderAffectsLineOutput(config: LyricRenderConfig): Boolean {
        return config.format != TTML &&
                config.normalizedLineOrder != DefaultLyricLineOrder
    }


    private fun appendTtmlCombinedLine(
        builder: StringBuilder,
        line: LyricsLine,
        romanLine: LyricsLine?,
        transLine: LyricsLine?,
        offset: Long,
        config: LyricRenderConfig,
        isWordLevel: Boolean, // 歌词数据是否是逐字
        translatedLang: String = "", // 翻译轨语言码（BCP47），写入内联 x-translation 的 xml:lang
        registerRomanSidecar: ((LyricsLine) -> String)? = null // 注册 head 音译 sidecar 条目并返回 itunes:key
    ) {
        if (line.words.isEmpty()) return

        val start = applyOffset(line.start, offset)
        val end = resolveLineEnd(line)

        val startStr = LyricFormatter.formatTtmlTimestamp(start)
        val endStr = LyricFormatter.formatTtmlTimestamp(LyricFormatter.applyOffset(end, offset))

        val showOriginal = !(config.onlyTranslationIfAvailable && transLine != null)

        // 音译改写入 head <transliterations> sidecar（保留词级时间），正文不再内联 x-romanization
        val lineKey = registerRomanSidecarKey(romanLine, registerRomanSidecar, showOriginal)

        builder.append("      <p begin=\"").append(startStr).append("\" end=\"").append(endStr).append("\"")
        lineKey?.let { builder.append(" itunes:key=\"").append(LyricFormatter.escapeXml(it)).append("\"") }
        appendLineExtensions(builder, line.extensions)
        builder.append(">")
        appendOriginalContent(builder, line, offset, isWordLevel, showOriginal)
        appendTranslationSpan(builder, transLine, translatedLang)

        builder.append("</p>")
    }

    /** 行结束时间：以原文最后一个词的结束时间为准（无有效结束时间时逐级兜底） */
    private fun resolveLineEnd(line: LyricsLine): Long {
        val lastWord = line.words.last()
        return when {
            lastWord.end > 0 -> lastWord.end
            lastWord.start > 0 -> lastWord.start + 300
            else -> line.start + 2000
        }
    }

    /** 注册 head 音译 sidecar 条目并返回 itunes:key；不满足条件（无回调/无音译行/隐藏原文/音译文本为空）返回 null */
    private fun registerRomanSidecarKey(
        romanLine: LyricsLine?,
        registerRomanSidecar: ((LyricsLine) -> String)?,
        showOriginal: Boolean
    ): String? {
        if (registerRomanSidecar == null || romanLine == null || !showOriginal) return null
        if (romanLine.words.joinToString("") { it.text }.isEmpty()) return null
        return registerRomanSidecar(romanLine)
    }

    /** 行级扩展属性（structured 协议 Line 第 4 元素）输出到 <p> 标签。
     *  itunes:songPart 已由外层提取用于 div 分组（规范中 songPart 是 <div> 的属性），
     *  divBegin/divEnd（段落时间窗）已由外层写入 <div> 的 begin/end，均不在此输出；
     *  非安全 XML 名称跳过（防注入破坏结构） */
    private fun appendLineExtensions(builder: StringBuilder, extensions: Map<String, String>) {
        extensions.forEach { (name, value) ->
            if (name != SONGPART_ATTR && name != DIV_BEGIN_ATTR && name != DIV_END_ATTR &&
                isSafeXmlName(name)
            ) {
                builder.append(" ").append(name).append("=\"")
                    .append(LyricFormatter.escapeXml(value)).append("\"")
            }
        }
    }

    /** 原文正文：逐字数据输出带 begin/end 的 <span>，整行数据输出转义后的完整文本；隐藏原文时不输出 */
    private fun appendOriginalContent(
        builder: StringBuilder,
        line: LyricsLine,
        offset: Long,
        isWordLevel: Boolean,
        showOriginal: Boolean
    ) {
        if (!showOriginal) return
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

    /** 内联翻译 <span ttm:role="x-translation">；translatedLang（BCP47）非空时写 xml:lang（AMLL 规范语言标注）；无翻译/空文本不输出 */
    private fun appendTranslationSpan(
        builder: StringBuilder,
        transLine: LyricsLine?,
        translatedLang: String
    ) {
        if (transLine == null) return
        val transText = transLine.words.joinToString("") { it.text }
        if (transText.isEmpty()) return
        builder.append("<span ttm:role=\"x-translation\"")
        if (translatedLang.isNotBlank()) {
            builder.append(" xml:lang=\"")
                .append(LyricFormatter.escapeXml(translatedLang)).append("\"")
        }
        builder.append(">")
        builder.append(LyricFormatter.escapeXml(transText))
        builder.append("</span>")
    }

    /** head sidecar 音译文本：逐词输出带 begin/end 的 <span>（与 TtmlWriter sidecar 形态一致），无有效词时间时退化为纯文本 */
    private fun appendSidecarRomanText(builder: StringBuilder, romanLine: LyricsLine, offset: Long) {
        val timedWords = romanLine.words.filter { it.start > 0 || it.end > 0 }
        if (timedWords.isEmpty()) {
            val wholeText = romanLine.words.joinToString("") { it.text }
            if (wholeText.isNotEmpty()) {
                builder.append(LyricFormatter.escapeXml(wholeText))
            }
            return
        }
        romanLine.words.forEach { word ->
            val wordEnd = if (word.end > word.start) word.end else word.start + 300
            builder.append("<span xmlns=\"http://www.w3.org/ns/ttml\" begin=\"")
                .append(LyricFormatter.formatTtmlTimestamp(LyricFormatter.applyOffset(word.start, offset)))
                .append("\" end=\"")
                .append(LyricFormatter.formatTtmlTimestamp(LyricFormatter.applyOffset(wordEnd, offset)))
                .append("\">")
            builder.append(LyricFormatter.escapeXml(word.text))
            builder.append("</span>")
        }
    }

    /**
     * structured 协议 agents 字段 → head <ttm:agent>（AMLL 规范 4.1）：
     * <ttm:agent xml:id="v1" type="person"><ttm:name type="full">名字</ttm:name></ttm:agent>
     * 无名字时自闭合。type 原样透传（person/character/organization/group/other）。
     */
    private fun appendTtmlAgent(builder: StringBuilder, agent: LyricsAgentEntry) {
        builder.append("      <ttm:agent xml:id=\"").append(LyricFormatter.escapeXml(agent.id)).append("\"")
        agent.type?.takeIf { it.isNotBlank() }?.let {
            builder.append(" type=\"").append(LyricFormatter.escapeXml(it)).append("\"")
        }
        val name = agent.name?.takeIf { it.isNotBlank() }
        if (name == null) {
            builder.append("/>\n")
        } else {
            builder.append(">\n        <ttm:name type=\"full\">")
                .append(LyricFormatter.escapeXml(name))
                .append("</ttm:name>\n      </ttm:agent>\n")
        }
    }

    /**
     * head 元数据元素树 → XML 序列化（官方与非官方通用；结构合法性已在解析侧校验/透传，此处只负责写出）。
     * 自闭合（无 text 无 children）/ 纯文本 / 嵌套 children 三种形态均支持；元素名与属性名做安全白名单校验。
     */
    private fun appendMetadataElement(builder: StringBuilder, element: LyricsMetadataElement, indent: String) {
        // 元素名安全校验：非法名称（含引号等注入字符）整节点跳过，避免破坏 XML 结构
        if (!isSafeXmlName(element.name)) return
        builder.append(indent).append("<").append(element.name)
        element.attributes.forEach { (name, value) ->
            if (isSafeXmlName(name)) {
                builder.append(" ").append(name).append("=\"")
                    .append(LyricFormatter.escapeXml(value)).append("\"")
            }
        }
        val text = element.text?.takeIf { it.isNotEmpty() }
        if (element.children.isEmpty() && text == null) {
            builder.append("/>\n")
            return
        }
        builder.append(">")
        if (text != null) builder.append(LyricFormatter.escapeXml(text))
        if (element.children.isNotEmpty()) {
            builder.append("\n")
            element.children.forEach { child ->
                appendMetadataElement(builder, child, "$indent  ")
            }
            builder.append(indent)
        }
        builder.append("</").append(element.name).append(">\n")
    }

    /**
     * 收集 metadata 元素树中需要根节点补声明的命名空间（prefix → URI）：
     * ttm/itunes 根节点已声明、xml 为 XML 内置前缀，均跳过；
     * 其余前缀（如 amll）由插件在节点 namespace 字段显式提供 URI，宿主不编造。
     */
    private fun collectExtraNamespaces(elements: List<LyricsMetadataElement>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        fun visit(element: LyricsMetadataElement) {
            val prefix = element.name.substringBefore(':', "")
            if (prefix.isNotEmpty() && prefix != "ttm" && prefix != "itunes" && prefix != "xml") {
                element.namespace?.takeIf { it.isNotBlank() }?.let { uri ->
                    result.putIfAbsent(prefix, uri)
                }
            }
            element.children.forEach { visit(it) }
        }
        elements.forEach { visit(it) }
        return result
    }

    /** XML 名称安全校验（元素名/属性名共用）：字母或下划线开头，仅含字母数字 _ . : - */
    private fun isSafeXmlName(name: String): Boolean {
        return name.isNotEmpty() && SAFE_XML_NAME.matches(name)
    }

    private fun appendEnhancedLine(
        builder: StringBuilder,
        line: LyricsLine,
        offset: Long,
        wordSeparator: String = "" // 词间分隔符：音译传空格（拉丁音节分词），原文为空（汉字无需分隔）
    ) {
        if (line.words.isEmpty()) return

        val start = LyricFormatter.applyOffset(line.start, offset)
        builder.append("[${LyricFormatter.formatTimestamp(start)}] ")

        line.words.forEachIndexed { index, word ->
            // 词间分隔符（非首词前补）：音译剥标签后为 "nung mou ce"，避免拉丁音节挤在一起
            if (index > 0 && wordSeparator.isNotEmpty()) builder.append(wordSeparator)
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

    private fun appendWordByWord(
        builder: StringBuilder,
        line: LyricsLine,
        offset: Long,
        wordSeparator: String = "" // 词间分隔符：音译传空格（拉丁音节分词），原文为空（汉字无需分隔）
    ) {
        line.words.forEachIndexed { index, word ->

            val startFormatted = LyricFormatter.formatTimestamp(LyricFormatter.applyOffset(word.start, offset))

            if (index == line.words.lastIndex) {

                val end = if (word.end > 0) word.end else word.start + 100
                val endFormatted = LyricFormatter.formatTimestamp(LyricFormatter.applyOffset(end, offset))

                builder.append("[$startFormatted]${word.text}[$endFormatted]")

            } else {
                builder.append("[$startFormatted]${word.text}")
                // 词间分隔符（非末词后补）：音译剥标签后为 "nung mou ce"，避免拉丁音节挤在一起
                if (wordSeparator.isNotEmpty()) builder.append(wordSeparator)
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
