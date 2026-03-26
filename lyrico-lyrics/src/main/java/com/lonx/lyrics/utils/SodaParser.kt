package com.lonx.lyrics.utils

import com.lonx.lyrics.model.LyricsData
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.LyricsWord

object SodaParser {

    private val LINE_PATTERN = Regex("""\[(\d+),(\d+)](.*)""")
    private val WORD_PATTERN = Regex("""<(\d+),(\d+),\d+>([^<]*)""")
    private val TAG_PATTERN = Regex("""\[(\w+):([^]]*)]""")

    fun parse(lyricsData: LyricsData): LyricsResult {
        val tags = mutableMapOf<String, String>()
        val raw = lyricsData.original.orEmpty()

        raw.lines().forEach { line ->
            val match = TAG_PATTERN.matchEntire(line.trim())
            if (match != null) {
                tags[match.groupValues[1]] = match.groupValues[2]
            }
        }

        val original = parseSoda(raw)

        val translated = lyricsData.translated
            ?.takeIf { it.isNotBlank() }
            ?.let { parseLrc(it) }

        val romanization = lyricsData.romanization
            ?.takeIf { it.isNotBlank() }
            ?.let { parseSoda(it) }

        return LyricsResult(
            tags = tags,
            original = original,
            translated = translated,
            romanization = romanization
        )
    }


    private fun parseSoda(raw: String): List<LyricsLine> {
        val result = mutableListOf<LyricsLine>()

        raw.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            val match = LINE_PATTERN.find(trimmed) ?: return@forEach

            val lineStart = match.groupValues[1].toLong()
            val content = match.groupValues[3]

            val words = mutableListOf<LyricsWord>()

            WORD_PATTERN.findAll(content).forEach { w ->
                val offset = w.groupValues[1].toLong()
                val duration = w.groupValues[2].toLong()
                val text = w.groupValues[3]

                val start = lineStart + offset
                val end = start + duration

                if (text.isNotEmpty()) {
                    words.add(
                        LyricsWord(
                            start = start,
                            end = end,
                            text = text
                        )
                    )
                }
            }

            // fallback（无逐字）
            if (words.isEmpty()) {
                val clean = content.replace(Regex("<[^>]+>"), "")
                words.add(
                    LyricsWord(
                        start = lineStart,
                        end = lineStart + 2000,
                        text = clean
                    )
                )
            }

            val lineEnd = words.maxOfOrNull { it.end } ?: (lineStart + 2000)

            result.add(
                LyricsLine(
                    start = lineStart,
                    end = lineEnd,
                    words = words
                )
            )
        }

        return result
    }

    private val LRC_PATTERN = Regex("""\[(\d+):(\d+\.\d+)](.*)""")

    private fun parseLrc(raw: String): List<LyricsLine> {
        val result = mutableListOf<LyricsLine>()

        raw.lines().forEach { line ->
            val match = LRC_PATTERN.find(line.trim()) ?: return@forEach

            val min = match.groupValues[1].toLong()
            val sec = match.groupValues[2].toDouble()
            val text = match.groupValues[3]

            val start = (min * 60_000 + (sec * 1000)).toLong()

            result.add(
                LyricsLine(
                    start = start,
                    end = start + 2000,
                    words = listOf(
                        LyricsWord(start, start + 2000, text)
                    )
                )
            )
        }

        return result
    }
}