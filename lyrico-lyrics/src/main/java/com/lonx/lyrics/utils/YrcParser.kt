package com.lonx.lyrics.utils

import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.LyricsWord
import java.util.regex.Pattern

object YrcParser {
    // YRC line: [123,456]content
    private val YRC_LINE_PATTERN: Pattern = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$")
    // YRC word: (123,456,0)word
    private val YRC_WORD_PATTERN: Pattern = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)([^()]*)")

    // LRC line: [00:12.345]content
    private val LRC_TIME_TAG_PATTERN: Pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]")

    fun parse(yrc: String?, lrc: String?, tlyric: String?, romalrc: String?): LyricsResult? {
        if (yrc.isNullOrEmpty() && lrc.isNullOrEmpty()) return null

        val originalLines = (if (!yrc.isNullOrEmpty()) {
            parseYrc(yrc)
        } else {
            parseLrc(lrc!!)
        }).sortedBy { it.start }

        val translatedLinesRaw = tlyric?.takeIf { it.isNotEmpty() }?.let { parseLrc(it) }
        val romanizationLinesRaw = romalrc?.takeIf { it.isNotEmpty() }?.let { parseLrc(it) }

        val translatedLinesAligned = lyricsMerge(originalLines, translatedLinesRaw)
        val romanizationLinesAligned = lyricsMerge(originalLines, romanizationLinesRaw)

        return LyricsResult(
            tags = emptyMap(), // TODO: parse tags
            original = originalLines,
            translated = translatedLinesAligned,
            romanization = romanizationLinesAligned
        )
    }


    private fun lyricsMerge(
        originalLines: List<LyricsLine>,
        transLines: List<LyricsLine>?
    ): List<LyricsLine>? {
        if (transLines.isNullOrEmpty()) return null

        // 1. 确保翻译列表按时间排序（通常已经是排序的，但为了安全起见）
        val sortedTransLines = transLines.sortedBy { it.start }
        val alignedList = ArrayList<LyricsLine>()

        var transIdx = 0
        val transCount = sortedTransLines.size

        // 遍历每一行原文
        for (i in originalLines.indices) {
            val orig = originalLines[i]

            // 定义当前行的有效时间窗口
            val winStart = orig.start
            // 窗口结束时间是下一行的开始时间；如果是最后一行，则是无限大(Long.MAX_VALUE)
            val winEnd = if (i < originalLines.size - 1) {
                originalLines[i + 1].start
            } else {
                Long.MAX_VALUE
            }

            var matchedText = ""

            // 在翻译列表中寻找属于当前窗口的行
            // 使用 while 循环配合外部索引 transIdx，避免 O(N*M) 的全遍历，性能提升为 O(N+M)
            while (transIdx < transCount) {
                val trans = sortedTransLines[transIdx]

                // 情况A: 翻译时间太早 (早于当前行 > 500ms)
                // 说明这是上一行的遗留或者无效的早起行，跳过它
                // TOLERANCE_EARLY: 允许翻译稍微抢拍 500ms
                if (trans.start < winStart - 500) {
                    transIdx++
                    continue
                }

                // 情况B: 翻译时间已经到了下一行的范围
                // 说明这句翻译属于后面的歌词，停止当前行的匹配，保留 transIdx 给下一次循环
                if (trans.start >= winEnd) {
                    break
                }

                // 情况C: 命中，时间在 [winStart - 500, winEnd) 之间
                // 找到了对应的翻译

                // 这里我们取匹配到的第一条翻译内容
                // 如果是逐字对象，拼接入 matchedText；如果是普通LRC，它通常只有一个Word
                matchedText = trans.words.joinToString("") { it.text }

                // 标记该翻译已使用，准备检查下一条
                transIdx++

                // 匹配到一条后，通常跳出（对应一行原文只有一行翻译的情况）
                // 如果你想支持一行原文对应多行翻译（罕见），可以不break，而是追加文本
                break
            }

            // 构造结果
            if (matchedText.isNotEmpty()) {
                val newWords = listOf(LyricsWord(orig.start, orig.end, matchedText))
                alignedList.add(LyricsLine(orig.start, orig.end, newWords))
            } else {
                // 没找到匹配，填入空行占位
                val emptyWords = listOf(LyricsWord(orig.start, orig.end, ""))
                alignedList.add(LyricsLine(orig.start, orig.end, emptyWords))
            }
        }

        return alignedList
    }

    private fun parseYrc(yrc: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        yrc.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEach
            val lineMatcher = YRC_LINE_PATTERN.matcher(trimmedLine)
            if (lineMatcher.find()) {
                val lineStart = lineMatcher.group(1)?.toLongOrNull() ?: 0L
                val lineDuration = lineMatcher.group(2)?.toLongOrNull() ?: 0L
                val content = lineMatcher.group(3) ?: ""
                val lineEnd = lineStart + lineDuration

                val words = mutableListOf<LyricsWord>()
                val wordMatcher = YRC_WORD_PATTERN.matcher(content)
                val wordList = mutableListOf<Pair<Long, String>>()
                while (wordMatcher.find()) {
                    val wordStart = wordMatcher.group(1)?.toLongOrNull() ?: 0L
                    val wordText = wordMatcher.group(3) ?: ""
                    wordList.add(wordStart to wordText)
                }

                for (i in wordList.indices) {
                    val (wordStartOffset, wordText) = wordList[i]
                    val absWordStart = lineStart + wordStartOffset
                    val wordEnd = if (i < wordList.size - 1) {
                        lineStart + wordList[i + 1].first
                    } else {
                        lineEnd
                    }
                    words.add(LyricsWord(start = absWordStart, end = wordEnd, text = wordText))
                }
                if (words.isEmpty() && content.isNotEmpty()) {
                    words.add(LyricsWord(start = lineStart, end = lineEnd, text = content))
                }

                if (words.isNotEmpty()) {
                    words.sortBy { it.start }
                    lines.add(LyricsLine(start = lineStart, end = lineEnd, words = words))
                }
            }
        }
        return lines
    }

    private fun parseLrc(lrc: String): List<LyricsLine> {
        val timedLines = mutableListOf<Pair<Long, String>>()

        lrc.lines().forEach { line ->
            val trimmedLine = line.trim()
            val timeTagMatcher = LRC_TIME_TAG_PATTERN.matcher(trimmedLine)

            val timestamps = mutableListOf<Long>()
            var contentStart = 0

            while(timeTagMatcher.find()) {
                val min = timeTagMatcher.group(1)?.toLongOrNull() ?: 0L
                val sec = timeTagMatcher.group(2)?.toLongOrNull() ?: 0L
                val msPart = (timeTagMatcher.group(3) ?: "0").padEnd(3, '0')
                val ms = msPart.toLongOrNull() ?: 0L
                timestamps.add(min * 60000 + sec * 1000 + ms)
                contentStart = timeTagMatcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val content = trimmedLine.substring(contentStart).trim()
                timestamps.forEach { time ->
                    timedLines.add(time to content)
                }
            }
            // TODO: Parse metadata tags like [ti:Title]
        }

        timedLines.sortBy { it.first }

        val lines = mutableListOf<LyricsLine>()
        for (i in timedLines.indices) {
            val (startTime, text) = timedLines[i]

            if (text.isEmpty()) continue

            val endTime = if (i + 1 < timedLines.size) {
                timedLines[i + 1].first
            } else {
                startTime + 3000
            }

            val word = LyricsWord(start = startTime, end = endTime, text = text)
            lines.add(LyricsLine(start = startTime, end = endTime, words = listOf(word)))
        }
        return lines
    }
}