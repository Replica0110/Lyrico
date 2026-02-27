package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.entity.SongEntity
import kotlin.math.abs

object MusicMatchUtils {
    /**
     * 标准化字符串：去除特殊字符、统一大小写
     */
    fun normalizeString(s: String): String {
        return s.lowercase()
            .replace(Regex("""[()（）\[\]【】《》<>「」『』"']"""), "")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("feat\\.?|ft\\.?|featuring", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*"), " ")
            .trim()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i - 1].lowercaseChar() == s2[j - 1].lowercaseChar()) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(dp[i - 1][j - 1], dp[i - 1][j], dp[i][j - 1]) + 1
                }
            }
        }
        return dp[m][n]
    }

    fun stringSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val clean1 = normalizeString(s1)
        val clean2 = normalizeString(s2)
        if (clean1 == clean2) return 1.0
        val maxLen = maxOf(clean1.length, clean2.length)
        val editDist = levenshteinDistance(clean1, clean2)
        val editSimilarity = 1.0 - (editDist.toDouble() / maxLen)
        val containsBonus = if (clean1.contains(clean2) || clean2.contains(clean1)) 0.15 else 0.0
        return minOf(1.0, editSimilarity + containsBonus)
    }

    fun parseFileName(fileName: String): Pair<String?, String?> {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val cleaned = nameWithoutExt.replace(Regex("\\(\\d+\\)$"), "").trim()
        val separators = listOf(" - ", " – ", "－", "_-_", " _ ")
        for (sep in separators) {
            if (cleaned.contains(sep)) {
                val parts = cleaned.split(sep, limit = 2)
                if (parts.size == 2) {
                    val first = parts[0].trim()
                    val second = parts[1].trim()
                    return if (first.length <= second.length) Pair(second, first) else Pair(first, second)
                }
            }
        }
        return Pair(cleaned, null)
    }

    fun buildSearchQueries(song: SongEntity): List<String> {
        val queries = mutableListOf<String>()
        val title = song.title?.takeIf { it.isNotBlank() && !it.contains("未知", true) }
        val artist = song.artist?.takeIf { it.isNotBlank() && !it.contains("未知", true) }
        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            queries.add("$title $artist")
        } else {
            val (parsedTitle, parsedArtist) = parseFileName(song.fileName)
            if (!parsedTitle.isNullOrBlank()) {
                if (!parsedArtist.isNullOrBlank()) queries.add("$parsedTitle $parsedArtist")
                queries.add(parsedTitle)
            }
        }
        return queries.distinct().take(3)
    }

    fun calculateMatchScore(
        result: com.lonx.lyrics.model.SongSearchResult,
        song: SongEntity,
        queryTitle: String?,
        queryArtist: String?
    ): Double {
        val targetTitle = queryTitle ?: song.title ?: song.fileName.substringBeforeLast(".")
        val titleScore = stringSimilarity(targetTitle, result.title) * 0.45
        val targetArtist = queryArtist ?: song.artist ?: ""
        val artistScore = if (targetArtist.isNotBlank()) stringSimilarity(targetArtist, result.artist) * 0.35 else 0.15
        val durationDiff = abs(result.duration - song.durationMilliseconds)
        val durationScore = when {
            durationDiff <= 2000 -> 0.20
            durationDiff <= 5000 -> 0.10
            else -> 0.0
        }
        return titleScore + artistScore + durationScore
    }
}