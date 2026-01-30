package com.lonx.lyrics.utils

import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.LyricsData
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsWord
import java.util.regex.Pattern

object QrcParser {

    private val QRC_XML_PATTERN = Pattern.compile("<Lyric_1 LyricType=\"1\" LyricContent=\"(.*?)\"/>", Pattern.DOTALL)
    private val QRC_LINE_PATTERN = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$")
    private val WORD_PATTERN = Pattern.compile("(?:^\\[\\d+,\\d+])?((?:(?!\\(\\d+,\\d+\\)).)*)\\((\\d+),(\\d+)\\)")
    private val TAG_PATTERN = Pattern.compile("^\\[(\\w+):([^]]*)]$")
    private val LRC_PATTERN = Pattern.compile("^\\[(\\d+):(\\d+\\.\\d+)](.*)$")

    fun parse(lyricsData: LyricsData): LyricsResult {
        val tags = mutableMapOf<String, String>()
        val origList = ArrayList<LyricsLine>()
        val qrcText = lyricsData.original ?: ""

        // 1. 解析 QRC 原文
        var content = qrcText
        val xmlMatcher = QRC_XML_PATTERN.matcher(qrcText)
        if (xmlMatcher.find()) {
            content = xmlMatcher.group(1) ?: ""
        }

        val lines = content.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val tagMatcher = TAG_PATTERN.matcher(line)
            if (tagMatcher.matches()) {
                tags[tagMatcher.group(1)!!] = tagMatcher.group(2) ?: ""
                continue
            }

            val lineMatcher = QRC_LINE_PATTERN.matcher(line)
            if (lineMatcher.matches()) {
                val lineStart = lineMatcher.group(1)!!.toLong()
                val lineDuration = lineMatcher.group(2)!!.toLong()
                val lineEnd = lineStart + lineDuration
                val lineContent = lineMatcher.group(3) ?: ""

                val words = ArrayList<LyricsWord>()
                val wordMatcher = WORD_PATTERN.matcher(lineContent)

                val wordList = mutableListOf<Pair<Long, String>>() // Pair<start, text>
                while (wordMatcher.find()) {
                    val wordText = wordMatcher.group(1) ?: ""
                    val wordStart = wordMatcher.group(2)!!.toLong()
                    wordList.add(wordStart to wordText)
                }

                for (i in wordList.indices) {
                    val (wordStart, wordText) = wordList[i]
                    val wordEnd = if (i < wordList.size - 1) {
                        wordList[i + 1].first
                    } else {
                        lineEnd
                    }
                    words.add(LyricsWord(start = wordStart, end = wordEnd, text = wordText))
                }

                if (words.isEmpty()) {
                    words.add(LyricsWord(lineStart, lineEnd, lineContent))
                }
                origList.add(LyricsLine(lineStart, lineEnd, words))
            }
        }

        // 解析翻译和罗马音（先解析出原始列表）
        val rawTransList = if (!lyricsData.translated.isNullOrBlank()) parseLrcFormat(lyricsData.translated) else null
        val rawRomaList = if (!lyricsData.romanization.isNullOrBlank()) parseQrcFormatAsLineByLine(lyricsData.romanization) else null
        // 将翻译/罗马音 对齐 到原文的时间轴上
        // 这样返回的 transList 长度将和 origList 完全一致，且一一对应
        val alignedTransList = lyricsMerge(origList, rawTransList)
        val alignedRomaList = lyricsMerge(origList, rawRomaList)

        return LyricsResult(tags, origList, alignedTransList, alignedRomaList)
    }

    /**
     * 解析 LRC 格式
     */
    private fun parseLrcFormat(text: String): List<LyricsLine>? {
        if (text.isBlank()) return null
        data class TempLine(val start: Long, val content: String)
        val tempLines = ArrayList<TempLine>()

        val lines = text.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val matcher = LRC_PATTERN.matcher(line)
            if (matcher.matches()) {
                val minutes = matcher.group(1)!!.toLong()
                val secondsStr = matcher.group(2)!!
                val totalMillis = (minutes * 60 * 1000) + (secondsStr.toDouble() * 1000).toLong()
                val content = matcher.group(3) ?: ""
                tempLines.add(TempLine(totalMillis, content))
            }
        }
        if (tempLines.isEmpty()) return null
        tempLines.sortBy { it.start }

        val resultList = ArrayList<LyricsLine>()
        for (i in tempLines.indices) {
            val current = tempLines[i]
            val next = if (i < tempLines.size - 1) tempLines[i + 1] else null
            val endTime = if (next != null) maxOf(current.start, next.start - 10) else current.start + 2000

            val words = listOf(LyricsWord(current.start, endTime, current.content))
            resultList.add(LyricsLine(current.start, endTime, words))
        }
        return resultList
    }

    /**
     * 解析 QRC 格式作为逐行歌词 (用于罗马音)
     */
    private fun parseQrcFormatAsLineByLine(text: String): List<LyricsLine>? {
        if (text.isBlank()) return null

        var content = text
        val xmlMatcher = QRC_XML_PATTERN.matcher(text)
        if (xmlMatcher.find()) {
            content = xmlMatcher.group(1) ?: ""
        }
        if (content.isBlank()) return null

        val resultList = ArrayList<LyricsLine>()
        val lines = content.lines()
        // Regex to remove word-level timings like (123,456)
        val wordTimingPattern = Pattern.compile("\\(\\d+,\\d+\\)")

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // Skip metadata tags like [ti: ...]
            val tagMatcher = TAG_PATTERN.matcher(line)
            if (tagMatcher.matches()) {
                continue
            }

            val lineMatcher = QRC_LINE_PATTERN.matcher(line)
            if (lineMatcher.matches()) {
                val lineStart = lineMatcher.group(1)!!.toLong()
                val lineDuration = lineMatcher.group(2)!!.toLong()
                val lineEnd = lineStart + lineDuration
                val lineContent = lineMatcher.group(3) ?: ""

                // Strip word timings to get plain text
                val plainText = wordTimingPattern.matcher(lineContent).replaceAll("").trim()

                // To avoid multiple spaces, replace them with a single space
                val cleanedText = plainText.replace(Regex("\\s+"), " ")

                if (cleanedText.isNotEmpty()) {
                    val words = listOf(LyricsWord(lineStart, lineEnd, cleanedText))
                    resultList.add(LyricsLine(lineStart, lineEnd, words))
                }
            }
        }
        return resultList.ifEmpty { null }
    }

    /**
     * 遍历原文的每一行，在翻译列表中寻找时间最接近的一行。
     *
     * @param originalLines 原文列表（基准）
     * @param transLines 翻译/罗马音列表（待匹配）
     * @return 一个和 originalLines 长度完全一致的列表。如果没有匹配到翻译，该位置为 null 或空行。
     */
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
}