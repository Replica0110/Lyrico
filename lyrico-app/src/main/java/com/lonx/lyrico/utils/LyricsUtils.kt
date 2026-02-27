package com.lonx.lyrico.utils

import android.annotation.SuppressLint
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsResult
import kotlin.math.abs

object LyricsUtils {
    @SuppressLint("DefaultLocale")
    private fun formatTimestamp(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val ms = millis % 1000
        return String.format("%02d:%02d.%03d", minutes, seconds, ms)
    }

    private fun isBlankOrPlaceholder(line: LyricsLine): Boolean {
        // 将所有单词拼接成一行文本
        val text = line.words.joinToString("") { it.text }.trim()
        return text.isEmpty() || text.matches(Regex("^[\\s/]*$"))
    }

    fun formatLrcResult(
        result: LyricsResult,
        config: LyricRenderConfig
    ): String {
        val builder = StringBuilder()

        val romanMap = if (config.showRomanization) {
            result.romanization?.associateBy { it.start } ?: emptyMap()
        } else emptyMap()

        val translatedMap = if (config.showTranslation) {
            result.translated?.associateBy { it.start } ?: emptyMap()
        } else emptyMap()

        result.original.forEach { line ->
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

            val skipOriginal = config.onlyTranslationIfAvailable && matchedTranslation != null

            if (!skipOriginal) {
                when (config.format) {
                    LyricFormat.ENHANCED_LRC -> appendEnhancedLine(builder, line)
                    LyricFormat.PLAIN_LRC -> appendLineByLine(builder, line)
                    LyricFormat.VERBATIM_LRC -> appendWordByWord(builder, line)
                }
                builder.append('\n')
            }

            if (matchedRoman != null && !skipOriginal) {
                builder.append(formatPlainLine(matchedRoman)).append('\n')
            }

            if (matchedTranslation != null) {
                builder.append(formatPlainLine(matchedTranslation)).append('\n')
            }
        }
        return builder.toString().trim()
    }

    private fun appendEnhancedLine(builder: StringBuilder, line: LyricsLine) {
        if (line.words.isEmpty()) return

        builder.append("[${formatTimestamp(line.start)}] ")

        line.words.forEach { word ->
            builder.append("<${formatTimestamp(word.start)}>")
            builder.append(word.text)
        }

        val lastEnd = line.words.last().end
        builder.append(" <${formatTimestamp(lastEnd)}>")
    }

    private fun appendLineByLine(builder: StringBuilder, line: LyricsLine) {
        val lineText = line.words.joinToString("") { it.text }
        val endTime = line.words.lastOrNull()?.end

        if (endTime != null) {
            builder.append("[${formatTimestamp(line.start)}]$lineText[${formatTimestamp(endTime)}]")
        } else {
            builder.append("[${formatTimestamp(line.start)}]$lineText")
        }
    }

    private fun appendWordByWord(builder: StringBuilder, line: LyricsLine) {
        line.words.forEachIndexed { index, word ->
            if (index == line.words.lastIndex) {
                builder.append("[${formatTimestamp(word.start)}]${word.text}[${formatTimestamp(word.end)}]")
            } else {
                builder.append("[${formatTimestamp(word.start)}]${word.text}")
            }
        }
    }

    private fun formatPlainLine(line: LyricsLine): String {
        return "[${formatTimestamp(line.start)}]" +
                line.words.joinToString(" ") { it.text }
    }

    private fun matchingSubLine(
        originalLine: LyricsLine,
        subLineMap: Map<Long, LyricsLine>
    ): LyricsLine? {
        val matched = subLineMap[originalLine.start]
        if (matched != null) return matched
        return subLineMap.entries.find { abs(it.key - originalLine.start) < 300 }?.value
    }
}