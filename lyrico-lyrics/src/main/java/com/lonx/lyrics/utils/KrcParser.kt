package com.lonx.lyrics.utils

import android.util.Base64
import android.util.Log
import com.lonx.lyrics.model.*
import com.lonx.lyrics.source.kg.KrcLanguageItem
import kotlinx.serialization.json.Json
import java.util.regex.Pattern

object KrcParser {
    private const val TAG = "KrcParser"

    // JSON 解析器 (用于解析 language 标签)
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    // 1. 标签正则: [ar:歌手]
    private val TAG_PATTERN = Pattern.compile("^\\[(\\w+):([^]]*)]$")

    // 2. 行正则: [100,200]内容...
    // 注意：Group 3 捕获的是去掉时间前缀后的所有内容
    private val LINE_PATTERN = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$")

    // 3. 字正则 (核心修复): <开始,时长,0>内容
    // 移除了行首锚点 ^，移除了复杂的负向断言。
    // [^<]* 表示匹配所有非 '<' 的字符，这包含了空格、标点符号等。
    private val WORD_PATTERN = Pattern.compile("<(\\d+),(\\d+),(\\d+)>([^<]*)")

    fun parse(krcText: String): LyricsResult {
        val tags = mutableMapOf<String, String>()
        val origList = ArrayList<LyricsLine>()

        val lines = krcText.lines()
        for (rawLine in lines) {
            val line = rawLine.trim() // 去除首尾空白，不影响中间空格
            if (!line.startsWith("[")) continue

            // A. 解析元数据标签
            val tagMatcher = TAG_PATTERN.matcher(line)
            if (tagMatcher.matches()) {
                tags[tagMatcher.group(1)!!] = tagMatcher.group(2) ?: ""
                continue
            }

            // B. 解析歌词行
            val lineMatcher = LINE_PATTERN.matcher(line)
            if (lineMatcher.matches()) {
                val lineStart = lineMatcher.group(1)!!.toLong()
                val lineDuration = lineMatcher.group(2)!!.toLong()
                val lineEnd = lineStart + lineDuration
                val lineContent = lineMatcher.group(3) ?: "" // 获取纯文本部分

                val words = ArrayList<LyricsWord>()

                val wordMatcher = WORD_PATTERN.matcher(lineContent)

                val wordOffsets = mutableListOf<Pair<Long, String>>() // 用于计算每个字 start
                while (wordMatcher.find()) {
                    val wordStartOffset = wordMatcher.group(1)!!.toLong()
                    val wordText = wordMatcher.group(4) ?: ""
                    wordOffsets.add(wordStartOffset to wordText)
                }

                for (i in wordOffsets.indices) {
                    val (offset, text) = wordOffsets[i]
                    val wordStart = lineStart + offset
                    val wordEnd = if (i < wordOffsets.size - 1) {
                        lineStart + wordOffsets[i + 1].first
                    } else {
                        lineEnd
                    }
                    words.add(LyricsWord(start = wordStart, end = wordEnd, text = text))
                }


                // 容错：如果该行没有 KRC 标签（普通 LRC 行），则整行当做一个字
                if (words.isEmpty() && lineContent.isNotEmpty()) {
                    words.add(LyricsWord(lineStart, lineEnd, lineContent))
                }

                origList.add(LyricsLine(lineStart, lineEnd, words))
            }
        }

        // C. 解析多语言 (翻译/罗马音)
        var tsList: ArrayList<LyricsLine>? = null
        var romaList: ArrayList<LyricsLine>? = null

        val languageTag = tags["language"]?.trim()
        if (!languageTag.isNullOrEmpty()) {
            try {
                // 解码 Base64 -> JSON
                val jsonStr = String(Base64.decode(languageTag, Base64.DEFAULT), Charsets.UTF_8)
                val langRoot = jsonParser.decodeFromString<KrcLanguageRoot>(jsonStr)
                Log.d(TAG, "KRC language: $langRoot")
                for (item in langRoot.content) {
                    // Type 0: 罗马音/日文假名 (逐行对应)
                    if (item.type == 0) {
                        romaList = ArrayList()
                        var offset = 0 // 用于跳过原文中的空行

                        for ((i, origLine) in origList.withIndex()) {
                            val isLineHasText = origLine.words.any { it.text.trim().isNotEmpty() }
                            if (!isLineHasText) {
                                offset += 1
                                continue
                            }

                            val contentIndex = i - offset
                            if (contentIndex >= 0 && contentIndex < item.lyricContent.size) {
                                val romaSyllables = item.lyricContent[contentIndex]
                                // 清理并用单个空格拼接所有罗马音音节
                                val fullRomaLine = romaSyllables.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

                                if (fullRomaLine.isNotEmpty()) {
                                    val romaWord = LyricsWord(origLine.start, origLine.end, fullRomaLine)
                                    romaList.add(LyricsLine(origLine.start, origLine.end, listOf(romaWord)))
                                } else {
                                    // 占位，保持行数对齐
                                    romaList.add(LyricsLine(origLine.start, origLine.end, emptyList()))
                                }
                            }
                        }
                    }
                    // Type 1: 翻译 (逐行对应)
                    else if (item.type == 1) {
                        tsList = ArrayList()
                        for ((i, origLine) in origList.withIndex()) {
                            if (i < item.lyricContent.size) {
                                val lineContentList = item.lyricContent[i]
                                // 翻译通常是一个数组的第一个元素
                                val transText = if (lineContentList.isNotEmpty()) lineContentList[0] else ""

                                if (transText.isNotEmpty()) {
                                    val transWord = LyricsWord(origLine.start, origLine.end, transText)
                                    tsList.add(LyricsLine(origLine.start, origLine.end, listOf(transWord)))
                                } else {
                                    // 占位，保持行数对齐（可选）
                                    tsList.add(LyricsLine(origLine.start, origLine.end, emptyList()))
                                }
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse language tag: ${e.message}")
            }
        }
        Log.d(TAG, "Parsed KRC lyrics: $tsList")
        Log.d(TAG, "Parsed KRC lyrics: $romaList")
        return LyricsResult(
            tags = tags,
            original = origList,
            translated = tsList,
            romanization = romaList
        )
    }
}