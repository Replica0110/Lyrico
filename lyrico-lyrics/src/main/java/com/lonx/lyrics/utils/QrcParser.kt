package com.lonx.lyrics.utils

import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.LyricsData
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsWord
import java.util.regex.Pattern
import kotlin.math.abs

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

                while (wordMatcher.find()) {
                    val wordText = wordMatcher.group(1) ?: ""
                    val wordStart = wordMatcher.group(2)!!.toLong()
                    val wordDuration = wordMatcher.group(3)!!.toLong()
                    words.add(LyricsWord(wordStart, wordStart + wordDuration, wordText))
                }

                if (words.isEmpty()) {
                    words.add(LyricsWord(lineStart, lineEnd, lineContent))
                }
                origList.add(LyricsLine(lineStart, lineEnd, words))
            }
        }

        // 解析翻译和罗马音（先解析出原始列表）
        val rawTransList = if (!lyricsData.translated.isNullOrBlank()) parseLrcFormat(lyricsData.translated) else null
        val rawRomaList = if (!lyricsData.romanization.isNullOrBlank()) parseLrcFormat(lyricsData.romanization) else null

        // 将翻译/罗马音 对齐 到原文的时间轴上
        // 这样返回的 transList 长度将和 origList 完全一致，且一一对应
        val alignedTransList = alignTranslations(origList, rawTransList)
        val alignedRomaList = alignTranslations(origList, rawRomaList)

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
     * 核心修复：对齐算法
     * 遍历原文的每一行，在翻译列表中寻找时间最接近的一行。
     *
     * @param originalLines 原文列表（基准）
     * @param transLines 翻译/罗马音列表（待匹配）
     * @return 一个和 originalLines 长度完全一致的列表。如果没有匹配到翻译，该位置为 null 或空行。
     */
    private fun alignTranslations(
        originalLines: List<LyricsLine>,
        transLines: List<LyricsLine>?
    ): List<LyricsLine>? {
        if (transLines.isNullOrEmpty()) return null

        val alignedList = ArrayList<LyricsLine>()
        val usedTransIndices = HashSet<Int>() // 防止同一句翻译被重复使用（解决 metadata 重复显示问题）

        for (orig in originalLines) {
            var bestMatchIndex = -1
            var minDiff = Long.MAX_VALUE

            // 时间匹配阈值：允许 1.5秒 的误差。
            // 如果翻译和原文相差超过 1.5秒，通常认为这不是同一句（除非是纯音乐段落后的第一句，但通常够用了）
            val TIME_THRESHOLD = 1500L

            // 不需要每次都遍历整个 transLines，但为了代码简单和健壮性，这里使用全遍历+剪枝
            // 实际数据量很小，不会有性能问题
            for (j in transLines.indices) {
                // 如果这句翻译已经被用过了，跳过 (防止多行原文匹配到同一行 Metadata)
                if (usedTransIndices.contains(j)) continue

                val trans = transLines[j]
                val diff = abs(orig.start - trans.start)

                if (diff < minDiff && diff < TIME_THRESHOLD) {
                    minDiff = diff
                    bestMatchIndex = j
                }
            }

            if (bestMatchIndex != -1) {
                // 找到了匹配的翻译
                val match = transLines[bestMatchIndex]
                usedTransIndices.add(bestMatchIndex)

                // 构造一个新的 Line，保持原文的时间轴结构，填入翻译的内容
                // 这样 UI 渲染时，start/end 完全跟随原文，不会乱跳
                val newWords = listOf(LyricsWord(orig.start, orig.end, match.words.firstOrNull()?.text ?: ""))
                alignedList.add(LyricsLine(orig.start, orig.end, newWords))
            } else {
                // 没找到匹配，填入一个空行占位，保证 List 下标一一对应
                val emptyWords = listOf(LyricsWord(orig.start, orig.end, ""))
                alignedList.add(LyricsLine(orig.start, orig.end, emptyWords))
            }
        }
        return alignedList
    }
}