package com.lonx.lyrico.utils

import android.util.Log
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.LyricsWord
import com.lonx.lyrico.data.model.lyrics.isWordByWord
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object LyricDecoder {
    internal fun normalizeTtmlText(text: String, trimEdges: Boolean = false): String {
        if (!text.contains('\n') && !text.contains('\r')) {
            return if (trimEdges) text.trim() else text
        }

        val collapsed = text.replace(Regex("\\s+"), " ")
        return if (trimEdges) collapsed.trim() else collapsed
    }

    fun detectFormat(lyricsText: String): LyricFormat? {
        if (lyricsText.isBlank()) return null

        val sampleLines = lyricsText.lines().filter { it.isNotBlank() }

        if (sampleLines.any { it.contains("<tt ") || it.contains("xmlns=\"http://www.w3.org/ns/ttml\"") }) {
            return LyricFormat.TTML
        }

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

    fun decode(lyricsText: String): LyricsResult? {
        val format = detectFormat(lyricsText) ?: return null

        return when (format) {
            LyricFormat.TTML -> parseTtmlToResult(lyricsText)
            LyricFormat.PLAIN_LRC,
            LyricFormat.VERBATIM_LRC,
            LyricFormat.ENHANCED_LRC -> parseLrcToResult(lyricsText)
        }
    }

    private fun parseLrcToResult(lyricsText: String): LyricsResult {
        val allLines = mutableListOf<LyricsLine>()
        val metadataTags = mutableMapOf<String, String>()

        lyricsText.lines().forEach { lineStr ->
            if (lineStr.isBlank()) return@forEach

            val tiMatch = Regex("""^\[ti:(.*)]$""").find(lineStr)
            val arMatch = Regex("""^\[ar:(.*)]$""").find(lineStr)
            val alMatch = Regex("""^\[al:(.*)]$""").find(lineStr)
            when {
                tiMatch != null -> { metadataTags["ti"] = tiMatch.groupValues[1].trim(); return@forEach }
                arMatch != null -> { metadataTags["ar"] = arMatch.groupValues[1].trim(); return@forEach }
                alMatch != null -> { metadataTags["al"] = alMatch.groupValues[1].trim(); return@forEach }
            }

            val matches = LyricFormatter.LRC_TIME_PATTERN.findAll(lineStr).toList()
            if (matches.isEmpty()) return@forEach

            val words = mutableListOf<LyricsWord>()
            val lineStart = parseLrcTimeMs(matches.first())

            var isFirstEnhancedTag = true

            for (i in matches.indices) {
                val match = matches[i]
                val time = parseLrcTimeMs(match)
                val nextMatchIdx = if (i + 1 < matches.size) matches[i + 1].range.first else lineStr.length

                val text = lineStr.substring(match.range.last + 1, nextMatchIdx)

                val isLeadingEnhancedLineTimestamp =
                    i == 0 &&
                            match.value.startsWith("[") &&
                            i + 1 < matches.size &&
                            matches[i + 1].value.startsWith("<") &&
                            text.isBlank()

                if (isLeadingEnhancedLineTimestamp) {
                    continue
                }

                val nextTime = if (i + 1 < matches.size) {
                    parseLrcTimeMs(matches[i + 1])
                } else {
                    time + 500
                }

                if (text.isNotEmpty()) {
                    words.add(
                        LyricsWord(
                            start = time,
                            end = nextTime,
                            text = text
                        )
                    )
                }
            }

            if (words.isNotEmpty()) {
                val lineEnd = words.last().end
                allLines.add(LyricsLine(start = lineStart, end = lineEnd, words = words))
            }
        }

        if (allLines.isEmpty()) {
            return LyricsResult(tags = metadataTags, original = emptyList(), translated = null, romanization = null, isWordByWord = false)
        }

        val (original, translated, romanization) = separateLrcTracks(allLines)

        return LyricsResult(
            tags = metadataTags,
            original = original,
            translated = translated,
            romanization = romanization,
            isWordByWord = original.isWordByWord()
        )
    }

    private data class TimeGroup(val time: Long, val lines: MutableList<LyricsLine>)

    private fun separateLrcTracks(allLines: List<LyricsLine>): Triple<List<LyricsLine>, List<LyricsLine>?, List<LyricsLine>?> {
        val groups = mutableListOf<TimeGroup>()
        for (line in allLines) {
            val existing = groups.find { it.time == line.start }
            if (existing != null) {
                existing.lines.add(line)
            } else {
                groups.add(TimeGroup(line.start, mutableListOf(line)))
            }
        }
        groups.sortBy { it.time }

        var hasMultiple = groups.any { it.lines.size >= 2 }
        if (!hasMultiple) return Triple(allLines, null, null)

        val originalLines = mutableListOf<LyricsLine>()
        val translatedLines = mutableListOf<LyricsLine>()
        val romanizationLines = mutableListOf<LyricsLine>()

        for (group in groups) {
            when {
                group.lines.size >= 3 -> {
                    originalLines.add(group.lines[0])
                    romanizationLines.add(group.lines[1])
                    translatedLines.add(group.lines[2])
                    for (i in 3 until group.lines.size) {
                        translatedLines.add(group.lines[i])
                    }
                }
                group.lines.size == 2 -> {
                    originalLines.add(group.lines[0])
                    translatedLines.add(group.lines[1])
                }
                else -> {
                    originalLines.add(group.lines[0])
                }
            }
        }

        return Triple(originalLines, translatedLines.ifEmpty { null }, romanizationLines.ifEmpty { null })
    }

    private fun parseTtmlToResult(ttmlText: String): LyricsResult {
        val originalLines = mutableListOf<LyricsLine>()
        val translatedLines = mutableListOf<LyricsLine>()
        val romanizationLines = mutableListOf<LyricsLine>()
        val metadataTranslations = mutableMapOf<String, String>()

        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val parser = factory.newPullParser()
        parser.setInput(StringReader(ttmlText))

        var currentPStart = 0L
        var currentPEnd = 0L
        var currentPRole: String? = null
        var currentPKey: String? = null
        var insideP = false
        var currentMetadataTranslationKey: String? = null
        var metadataTranslationTextBuilder: StringBuilder? = null

        var originalWords = mutableListOf<LyricsWord>()
        var plainTextBuilder = StringBuilder()
        var romanTextBuilder = StringBuilder()
        var transTextBuilder = StringBuilder()

        var currentSpanStart: Long? = null
        var currentSpanEnd: Long? = null
        var currentSpanRole: String? = null
        var currentSpanTextBuilder: StringBuilder? = null

        fun attr(name: String): String? {
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                val attrValue = parser.getAttributeValue(i)

                if (attrName == name || attrName.endsWith(":$name")) {
                    return attrValue
                }
            }
            return null
        }

        fun roleAttr(): String? {
            for (i in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(i)
                val attrNamespace = parser.getAttributeNamespace(i)

                if (
                    attrName == "role" ||
                    attrName == "ttm:role" ||
                    attrNamespace == "http://www.w3.org/ns/ttml#metadata"
                ) {
                    return parser.getAttributeValue(i)
                }
            }
            return null
        }

        fun finishP() {
            when (currentPRole) {
                "x-translation" -> {
                    val text = plainTextBuilder.toString()
                    if (text.isNotBlank()) {
                        translatedLines.add(
                            LyricsLine(
                                start = currentPStart,
                                end = currentPEnd,
                                words = listOf(
                                    LyricsWord(
                                        start = currentPStart,
                                        end = currentPEnd,
                                        text = text
                                    )
                                )
                            )
                        )
                    }
                }

                "x-romanization" -> {
                    val text = plainTextBuilder.toString()
                    if (text.isNotBlank()) {
                        romanizationLines.add(
                            LyricsLine(
                                start = currentPStart,
                                end = currentPEnd,
                                words = listOf(
                                    LyricsWord(
                                        start = currentPStart,
                                        end = currentPEnd,
                                        text = text
                                    )
                                )
                            )
                        )
                    }
                }

                else -> {
                    if (originalWords.isNotEmpty()) {
                        originalLines.add(
                            LyricsLine(
                                start = currentPStart,
                                end = currentPEnd,
                                words = originalWords.toList()
                            )
                        )
                    } else {
                        val text = plainTextBuilder.toString()
                        if (text.isNotBlank()) {
                            originalLines.add(
                                LyricsLine(
                                    start = currentPStart,
                                    end = currentPEnd,
                                    words = listOf(
                                        LyricsWord(
                                            start = currentPStart,
                                            end = currentPEnd,
                                            text = text
                                        )
                                    )
                                )
                            )
                        }
                    }

                    val romanText = romanTextBuilder.toString()
                    if (romanText.isNotBlank()) {
                        romanizationLines.add(
                            LyricsLine(
                                start = currentPStart,
                                end = currentPEnd,
                                words = listOf(
                                    LyricsWord(
                                        start = currentPStart,
                                        end = currentPEnd,
                                        text = romanText
                                    )
                                )
                            )
                        )
                    }

                    val transText = transTextBuilder.toString()
                    val metadataTransText = currentPKey
                        ?.let { metadataTranslations[it] }
                        .orEmpty()
                    val finalTransText = transText.ifBlank { metadataTransText }
                    if (finalTransText.isNotBlank()) {
                        translatedLines.add(
                            LyricsLine(
                                start = currentPStart,
                                end = currentPEnd,
                                words = listOf(
                                    LyricsWord(
                                        start = currentPStart,
                                        end = currentPEnd,
                                        text = finalTransText
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }

        fun appendTextOutsideSpan(text: String) {
            val normalized = normalizeTtmlText(text)

            if (originalWords.isNotEmpty()) {
                if (normalized.isBlank() && (text.contains('\n') || text.contains('\r'))) return

                val lastIndex = originalWords.lastIndex
                val lastWord = originalWords[lastIndex]
                originalWords[lastIndex] = lastWord.copy(text = lastWord.text + normalized)
            } else if (normalized.isNotBlank()) {
                plainTextBuilder.append(normalized)
            }
        }

        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "text" -> {
                            if (!insideP) {
                                val key = attr("for")
                                if (!key.isNullOrBlank()) {
                                    currentMetadataTranslationKey = key
                                    metadataTranslationTextBuilder = StringBuilder()
                                }
                            }
                        }

                        "p" -> {
                            val begin = attr("begin")
                            val end = attr("end")

                            if (begin != null && end != null) {
                                insideP = true
                                currentPStart = parseTtmlTimeMs(begin)
                                currentPEnd = parseTtmlTimeMs(end)
                                currentPRole = roleAttr()
                                currentPKey = attr("key")

                                originalWords = mutableListOf()
                                plainTextBuilder = StringBuilder()
                                romanTextBuilder = StringBuilder()
                                transTextBuilder = StringBuilder()

                                currentSpanStart = null
                                currentSpanEnd = null
                                currentSpanRole = null
                                currentSpanTextBuilder = null
                            }
                        }

                        "span" -> {
                            if (insideP) {
                                currentSpanStart = attr("begin")?.let { parseTtmlTimeMs(it) }
                                currentSpanEnd = attr("end")?.let { parseTtmlTimeMs(it) }
                                currentSpanRole = roleAttr()
                                currentSpanTextBuilder = StringBuilder()
                            }
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    if (currentMetadataTranslationKey != null && metadataTranslationTextBuilder != null) {
                        metadataTranslationTextBuilder.append(parser.text ?: "")
                    } else if (insideP) {
                        val text = parser.text ?: ""

                        if (currentSpanTextBuilder != null) {
                            currentSpanTextBuilder.append(text)
                        } else {
                            appendTextOutsideSpan(text)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "text" -> {
                            val key = currentMetadataTranslationKey
                            val text = metadataTranslationTextBuilder?.toString()
                            if (key != null && text != null) {
                                normalizeTtmlText(text, trimEdges = true)
                                    .takeIf { it.isNotBlank() }
                                    ?.let { metadataTranslations[key] = it }
                            }
                            currentMetadataTranslationKey = null
                            metadataTranslationTextBuilder = null
                        }

                        "span" -> {
                            if (insideP && currentSpanTextBuilder != null) {
                                val text = normalizeTtmlText(currentSpanTextBuilder.toString(), trimEdges = true)

                                when (currentSpanRole) {
                                    "x-translation" -> {
                                        transTextBuilder.append(text)
                                    }

                                    "x-romanization" -> {
                                        romanTextBuilder.append(text)
                                    }

                                    else -> {
                                        val spanStart = currentSpanStart
                                        val spanEnd = currentSpanEnd

                                        if (
                                            spanStart != null &&
                                            spanEnd != null &&
                                            text.isNotEmpty()
                                        ) {
                                            originalWords.add(
                                                LyricsWord(
                                                    start = spanStart,
                                                    end = spanEnd,
                                                    text = text
                                                )
                                            )
                                        } else {
                                            plainTextBuilder.append(text)
                                        }
                                    }
                                }

                                currentSpanStart = null
                                currentSpanEnd = null
                                currentSpanRole = null
                                currentSpanTextBuilder = null
                            }
                        }

                        "p" -> {
                            if (insideP) {
                                finishP()
                                insideP = false
                                currentPKey = null
                            }
                        }
                    }
                }
            }

            eventType = parser.next()
        }
        val wordLineCount = originalLines.count { it.words.size > 1 }
        val totalWordCount = originalLines.sumOf { it.words.size }

        runCatching {
            Log.d(
                "LyricDecoder",
                "parseTtmlToResult: lines=${originalLines.size}, wordLineCount=$wordLineCount, totalWordCount=$totalWordCount"
            )
        }
        return LyricsResult(
            tags = emptyMap(),
            original = originalLines,
            translated = translatedLines.ifEmpty { null },
            romanization = romanizationLines.ifEmpty { null },
            isWordByWord = originalLines.isWordByWord()
        )
    }

    private fun parseLrcTimeMs(matchResult: MatchResult): Long {
        val min = matchResult.groupValues[2].toLong()
        val sec = matchResult.groupValues[3].toLong()
        val ms = matchResult.groupValues[4].padEnd(3, '0').toLong()
        return (min * 60 + sec) * 1000 + ms
    }

    private fun parseTtmlTimeMs(timeStr: String): Long {
        val text = timeStr.trim()
        if (text.isBlank()) return 0L

        Regex("""^(\d+(?:\.\d+)?)ms$""")
            .matchEntire(text)
            ?.let { return it.groupValues[1].toDouble().toLong() }

        Regex("""^(\d+(?:\.\d+)?)s$""")
            .matchEntire(text)
            ?.let { return (it.groupValues[1].toDouble() * 1000).toLong() }

        Regex("""^(\d+(?:\.\d+)?)$""")
            .matchEntire(text)
            ?.let { return (it.groupValues[1].toDouble() * 1000).toLong() }

        Regex("""^(\d+):(\d{2}):(\d{2})(?:\.(\d+))?$""")
            .matchEntire(text)
            ?.let { match ->
                val h = match.groupValues[1].toLong()
                val m = match.groupValues[2].toLong()
                val s = match.groupValues[3].toLong()
                val fraction = match.groupValues[4]
                val ms = if (fraction.isBlank()) 0L else fraction.padEnd(3, '0').take(3).toLong()
                return (h * 3600 + m * 60 + s) * 1000 + ms
            }

        Regex("""^(\d+):(\d{2})(?:\.(\d+))?$""")
            .matchEntire(text)
            ?.let { match ->
                val m = match.groupValues[1].toLong()
                val s = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val ms = if (fraction.isBlank()) 0L else fraction.padEnd(3, '0').take(3).toLong()
                return (m * 60 + s) * 1000 + ms
            }

        return 0L
    }
}
