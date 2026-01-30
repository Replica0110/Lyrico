package com.lonx.lyrico.utils

import android.annotation.SuppressLint
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

    fun formatLrcResult(result: LyricsResult, romaEnabled: Boolean = false): String {
        val builder = StringBuilder()
        val originalLines = result.original
        val translatedLines = result.translated
        val translatedMap = translatedLines?.associateBy { it.start } ?: emptyMap()

        originalLines.forEach { originalLine ->
            val formattedOriginalLine = originalLine.words.joinToString("") { word ->
                "[${formatTimestamp(word.start)}]${word.text}"
            }
            builder.append(formattedOriginalLine)
            builder.append("\n")

            val matchedTranslation = findMatchingTranslatedLine(originalLine, translatedMap)
            if (romaEnabled) {
                val romanizationLines = result.romanization
                val romanizationMap = romanizationLines?.associateBy { it.start } ?: emptyMap()
                val matchedRomanization = findMatchingTranslatedLine(originalLine, romanizationMap)
                if (matchedRomanization != null) {
                    val formattedRomanizationLine = "[${formatTimestamp(matchedRomanization.start)}]${
                        matchedRomanization.words.joinToString(" ") { it.text }
                    }"
                    builder.append(formattedRomanizationLine)
                    builder.append("\n")
                }
            }
            if (matchedTranslation != null) {
                val formattedTranslatedLine = "[${formatTimestamp(matchedTranslation.start)}]${
                    matchedTranslation.words.joinToString(" ") { it.text }
                }"
                builder.append(formattedTranslatedLine)
                builder.append("\n")
            }

        }
        return builder.toString().trim()
    }

    private fun findMatchingTranslatedLine(
        originalLine: LyricsLine,
        translatedMap: Map<Long, LyricsLine>
    ): LyricsLine? {
        val matched = translatedMap[originalLine.start]
        if (matched != null) return matched
        return translatedMap.entries.find { abs(it.key - originalLine.start) < 500 }?.value
    }
}