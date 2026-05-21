package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.LyricsWord
import com.lonx.lyrico.data.model.lyrics.isWordByWord
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object LyricDecoder {
    private val TTML_P_PATTERN = Regex("""<p\s+begin="([^"]+)"\s+end="([^"]+)".*?>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val TTML_SPAN_PATTERN = Regex("""<span\s+begin="([^"]+)"\s+end="([^"]+)".*?>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val TTML_ROLE_PATTERN = Regex("""ttm:role="([^"]+)"""")

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

        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }

        val parser = factory.newPullParser()
        parser.setInput(StringReader(ttmlText))

        var currentPStart = 0L
        var currentPEnd = 0L
        var currentPRole: String? = null
        var insideP = false

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
                if (parser.getAttributeName(i) == name) {
                    return parser.getAttributeValue(i)
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
                    if (transText.isNotBlank()) {
                        translatedLines.add(
                            LyricsLine(
                                start = currentPStart,
                                end = currentPEnd,
                                words = listOf(
                                    LyricsWord(
                                        start = currentPStart,
                                        end = currentPEnd,
                                        text = transText
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }

        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "p" -> {
                            val begin = attr("begin")
                            val end = attr("end")

                            if (begin != null && end != null) {
                                insideP = true
                                currentPStart = parseTtmlTimeMs(begin)
                                currentPEnd = parseTtmlTimeMs(end)
                                currentPRole = roleAttr()

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
                    if (insideP) {
                        val text = parser.text ?: ""

                        if (currentSpanTextBuilder != null) {
                            currentSpanTextBuilder.append(text)
                        } else if (text.isNotBlank()) {
                            plainTextBuilder.append(text)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "span" -> {
                            if (insideP && currentSpanTextBuilder != null) {
                                val text = currentSpanTextBuilder.toString()

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
                            }
                        }
                    }
                }
            }

            eventType = parser.next()
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
        val parts = timeStr.split(":", ".")
        if (parts.size < 4) return 0L
        val h = parts[0].toLong()
        val m = parts[1].toLong()
        val s = parts[2].toLong()
        val ms = parts[3].padEnd(3, '0').toLong()
        return (h * 3600 + m * 60 + s) * 1000 + ms
    }
}
