package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import kotlin.math.abs
import kotlin.math.sqrt

object MusicMatchUtils {

    // ==================== 1. 文本清洗 ====================

    private val versionNoiseRegex = Regex(
        """[(\[（【《]?\s*(?:official\s*(?:video|audio|mv)|music\s*video|lyric[s]?\s*video|lyrics?|完整版|高清|无损|动态歌词|歌词版|instrumental|inst\.?|off\s*vocal|伴奏|纯音乐|live|现场版?|remix|remaster(?:ed)?|acoustic|cover|sped\s*up|slowed|nightcore|demo|edit|radio\s*edit|deluxe|bonus\s*track)\s*[)\]）】》]?""",
        RegexOption.IGNORE_CASE
    )

    // 全局分隔正则不含裸 _
    private val segmentSplitRegex = Regex(
        """\s*[-–—－/、,，&＆+×|｜]\s*|\s+_\s+|_-_|\s+(?:x|and|feat\.?|ft\.?|featuring|with)\s+""",
        RegexOption.IGNORE_CASE
    )

    private fun cleanNoise(s: String): String {
        return s
            .replace(versionNoiseRegex, " ")
            .replace(Regex("""[【】\[\]（）()《》<>「」『』"'""\-–—~·・]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun normalizeForMatch(s: String): String {
        return cleanNoise(s)
            .lowercase()
            .replace('　', ' ')
            .replace(Regex("""[._/\\|,:;，。！？!?#&＆]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun compactForm(s: String): String {
        return normalizeForMatch(s).replace(" ", "")
    }

    // ==================== 2. 片段拆分 ====================

    fun splitToSegments(s: String?): List<String> {
        if (s.isNullOrBlank()) return emptyList()
        return s.split(segmentSplitRegex)
            .map { cleanNoise(it).trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun parseFileNameSegments(fileName: String): List<String> {
        val nameWithoutExt = fileName.substringBeforeLast(".", fileName)
        val cleaned = nameWithoutExt
            .replace(Regex("""\(\d+\)$"""), "")
            .replace(Regex("""^\d+[.\s\-_]+"""), "")
            .trim()

        return splitToSegments(cleaned)
    }

    fun parseFileName(fileName: String): Pair<String?, String?> {
        val segments = parseFileNameSegments(fileName)
        return Pair(segments.getOrNull(0), segments.getOrNull(1))
    }

    // ==================== 3. 相似度基础算法 ====================

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(dp[i - 1][j - 1], dp[i - 1][j], dp[i][j - 1]) + 1
                }
            }
        }
        return dp[m][n]
    }

    private fun levenshteinSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val maxLen = maxOf(a.length, b.length)
        return 1.0 - levenshteinDistance(a, b).toDouble() / maxLen
    }

    private fun charRemainderSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val countsA = HashMap<Int, Int>()
        a.codePoints().forEach { cp ->
            countsA[cp] = (countsA[cp] ?: 0) + 1
        }

        var commonCount = 0
        b.codePoints().forEach { cp ->
            val count = countsA[cp] ?: 0
            if (count > 0) {
                commonCount++
                countsA[cp] = count - 1
            }
        }

        val lenA = a.codePointCount(0, a.length)
        val lenB = b.codePointCount(0, b.length)
        val totalChars = lenA + lenB
        return if (totalChars == 0) 1.0 else (2.0 * commonCount / totalChars).coerceIn(0.0, 1.0)
    }

    private fun containsSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0

        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a

        return if (longer.contains(shorter)) {
            val ratio = shorter.length.toDouble() / longer.length.toDouble()
            0.80 + 0.20 * ratio
        } else 0.0
    }

    private fun tokenDiceSimilarity(a: String, b: String): Double {
        val tokensA = a.split(Regex("""\s+""")).filter { it.isNotBlank() }.toSet()
        val tokensB = b.split(Regex("""\s+""")).filter { it.isNotBlank() }.toSet()

        if (tokensA.isEmpty() && tokensB.isEmpty()) return 1.0
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val intersection = tokensA.intersect(tokensB).size
        return (2.0 * intersection / (tokensA.size + tokensB.size)).coerceIn(0.0, 1.0)
    }

    /**
     * 单串对单串的综合相似度
     * 短字符串更依赖 Levenshtein，长字符串更依赖字符残差
     */
    fun smartSimilarity(a: String?, b: String?): Double {
        if (a.isNullOrBlank() && b.isNullOrBlank()) return 1.0
        if (a.isNullOrBlank() || b.isNullOrBlank()) return 0.0

        val normA = normalizeForMatch(a)
        val normB = normalizeForMatch(b)

        if (normA.isBlank() && normB.isBlank()) return 1.0
        if (normA.isBlank() || normB.isBlank()) return 0.0
        if (normA == normB) return 1.0

        val compA = compactForm(a)
        val compB = compactForm(b)

        if (compA == compB) return 1.0

        val containsScore = containsSimilarity(compA, compB)
        val charScore = charRemainderSimilarity(compA, compB)
        val levScore = levenshteinSimilarity(compA, compB)
        val diceScore = tokenDiceSimilarity(normA, normB)

        // 短字符串更依赖 Levenshtein（两个2字歌名重合1字，charScore就是0.5，偏乐观）
        // 长字符串更依赖字符残差（对额外信息更宽容）
        val minLen = minOf(compA.length, compB.length)
        val blendedScore = if (minLen <= 3) {
            charScore * 0.35 + levScore * 0.45 + diceScore * 0.20
        } else {
            charScore * 0.50 + levScore * 0.30 + diceScore * 0.20
        }

        return maxOf(containsScore, blendedScore).coerceIn(0.0, 1.0)
    }

    fun stringSimilarity(s1: String, s2: String): Double = smartSimilarity(s1, s2)

    // ==================== 4. 片段匹配（核心） ====================

    /**
     * 片段匹配分数
     *
     * 加入 coverage 项：要求本地片段大部分都能匹配上
     * 避免"只匹配到一个片段但那个片段很强"导致分数偏高
     *
     * 例：本地 ["周杰伦", "七里香"]，结果 ["周杰伦", "晴天"]
     * localScores = [高, 低]，localAvg 中等，但 coverage 只有 0.5，会被压低
     */
    fun segmentMatchScore(
        localSegments: List<String>,
        resultTitle: String?,
        resultArtist: String?
    ): Double {
        if (localSegments.isEmpty()) return 0.0

        val resultSegments = buildList {
            if (!resultTitle.isNullOrBlank()) addAll(splitToSegments(resultTitle))
            if (!resultArtist.isNullOrBlank()) addAll(splitToSegments(resultArtist))
        }.filter { it.isNotBlank() }.distinct()

        if (resultSegments.isEmpty()) return 0.0

        // 本地每个片段在结果中的最佳匹配
        val localScores = localSegments.map { local ->
            resultSegments.maxOf { remote -> smartSimilarity(local, remote) }
        }

        // 结果每个片段在本地中的最佳匹配
        val resultScores = resultSegments.map { remote ->
            localSegments.maxOf { local -> smartSimilarity(local, remote) }
        }

        val localAvg = localScores.average()
        val resultAvg = resultScores.average()

        // 覆盖率：本地片段中有多少比例达到了"匹配"标准
        val localCoverage = localScores.count { it >= 0.75 }.toDouble() / localScores.size

        // 是否有至少一个强命中
        val strongHit = localScores.any { it >= 0.90 } || localCoverage >= 0.75

        val score = localAvg * 0.50 + resultAvg * 0.25 + localCoverage * 0.25

        // 没有强命中时整体降权，避免字符残差造成的虚高
        return if (strongHit) {
            score.coerceIn(0.0, 1.0)
        } else {
            (score * 0.85).coerceIn(0.0, 1.0)
        }
    }

    /**
     * 整体匹配分数 - 片段拆分不准时的兜底
     */
    private fun wholeMatchScore(
        localSegments: List<String>,
        resultTitle: String?,
        resultArtist: String?
    ): Double {
        val localWhole = localSegments.joinToString(" ")
        val resultWhole = buildList {
            if (!resultTitle.isNullOrBlank()) add(resultTitle)
            if (!resultArtist.isNullOrBlank()) add(resultArtist)
        }.joinToString(" ")

        return smartSimilarity(localWhole, resultWhole)
    }

    // ==================== 5. 时长匹配 ====================

    /**
     * 时长相似度，无有效时长时返回 null，不参与计算
     */
    private fun durationSimilarityOrNull(localDurationMs: Long, remoteDurationMs: Long): Double? {
        if (localDurationMs <= 0L || remoteDurationMs <= 0L) return null
        val diff = abs(localDurationMs - remoteDurationMs)
        return when {
            diff <= 1500L -> 1.0
            diff <= 3000L -> 0.85
            diff <= 5000L -> 0.60
            diff <= 8000L -> 0.30
            diff <= 12000L -> 0.10
            else -> 0.0
        }
    }

    // ==================== 6. 排序权重 ====================

    /**
     * 搜索结果排序加分
     * 只有文本分数达标时才生效，防止低匹配候选被"救活"
     */
    private fun rankBonus(
        index: Int,
        textScore: Double,
        threshold: Double = 0.60,
        maxBonus: Double = 0.06
    ): Double {
        if (index < 0 || textScore < threshold) return 0.0
        return maxBonus / sqrt((index + 1).toDouble())
    }

    // ==================== 7. 本地片段提取 ====================

    /**
     * 从歌曲信息中提取本地关键词片段
     * 支持 preferFileName 配置
     */
    fun extractLocalSegments(
        song: SongEntity,
        preferFileName: Boolean = false,
        queryTitle: String? = null,
        queryArtist: String? = null
    ): List<String> {
        val fileSegments = parseFileNameSegments(song.fileName)

        val tagSegments = buildList {
            val title = queryTitle?.takeIf { it.isNotBlank() }
                ?: song.title?.takeIf { it.isNotBlank() && !it.contains("未知", true) }

            val artist = queryArtist?.takeIf { it.isNotBlank() }
                ?: song.artist?.takeIf { it.isNotBlank() && !it.contains("未知", true) }

            if (!title.isNullOrBlank()) addAll(splitToSegments(title))
            if (!artist.isNullOrBlank()) addAll(splitToSegments(artist))
        }.filter { it.isNotBlank() }.distinct()

        return if (preferFileName) {
            fileSegments.ifEmpty { tagSegments }
        } else {
            tagSegments.ifEmpty { fileSegments }
        }
    }

    // ==================== 8. 综合匹配分数 ====================

    /**
     * 歌曲搜索结果的综合匹配分数
     */
    fun calculateMatchScore(
        result: SongSearchResult,
        song: SongEntity,
        preferFileName: Boolean = false,
        queryTitle: String? = null,
        queryArtist: String? = null,
        rankIndex: Int = -1
    ): Double {
        return calculateMatchScoreDetail(
            result = result,
            song = song,
            preferFileName = preferFileName,
            queryTitle = queryTitle,
            queryArtist = queryArtist,
            rankIndex = rankIndex
        ).finalScore
    }

    // ==================== 9. 搜索 Query 构建 ====================

    fun buildSearchQueries(
        song: SongEntity,
        preferFileName: Boolean = false
    ): List<String> {
        val segments = extractLocalSegments(
            song = song,
            preferFileName = preferFileName
        )

        val title = song.title?.takeIf { it.isNotBlank() && !it.contains("未知", true) }
        val artist = song.artist?.takeIf { it.isNotBlank() && !it.contains("未知", true) }

        return buildList {
            // 片段组合查询
            if (segments.isNotEmpty()) {
                add(segments.joinToString(" "))
                // 多片段时，单独搜第一个片段（可能是标题也可能是艺人）
                if (segments.size > 1) {
                    segments.take(2).forEach { add(it) }
                }
            }

            // tag 补充查询（如果 preferFileName，tag 信息作为后备）
            if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
                add("${cleanNoise(title)} ${cleanNoise(artist)}")
                add(cleanNoise(title))
            } else if (!title.isNullOrBlank()) {
                add(cleanNoise(title))
            }
        }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
    }

    fun buildSearchQueries(title: String?, artist: String?): List<String> {
        return buildList {
            val cleanTitle = title?.let { cleanNoise(it) }?.takeIf { it.isNotBlank() }
            val cleanArtist = artist?.let { cleanNoise(it) }?.takeIf { it.isNotBlank() }

            if (!cleanTitle.isNullOrBlank() && !cleanArtist.isNullOrBlank()) {
                add("$cleanTitle $cleanArtist")
                add(cleanTitle)
                add("$cleanArtist $cleanTitle")
            } else if (!cleanTitle.isNullOrBlank()) {
                add(cleanTitle)
            } else if (!cleanArtist.isNullOrBlank()) {
                add(cleanArtist)
            }
        }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
    }

    // ==================== 10. 封面搜索排序 ====================
    fun calculateMatchScoreDetail(
        result: SongSearchResult,
        song: SongEntity,
        preferFileName: Boolean = false,
        queryTitle: String? = null,
        queryArtist: String? = null,
        rankIndex: Int = -1
    ): MatchScoreDetail {
        val localSegments = extractLocalSegments(
            song = song,
            preferFileName = preferFileName,
            queryTitle = queryTitle,
            queryArtist = queryArtist
        )

        if (localSegments.isEmpty()) {
            return MatchScoreDetail(
                finalScore = 0.0,
                textScore = 0.0,
                segmentScore = 0.0,
                wholeScore = 0.0,
                durationScore = null,
                rankBonus = 0.0,
                localSegments = emptyList()
            )
        }

        val segScore = segmentMatchScore(localSegments, result.title, result.artist)
        val wholeScore = wholeMatchScore(localSegments, result.title, result.artist)

        val textScore = segScore * 0.75 + wholeScore * 0.25

        val durationScore = durationSimilarityOrNull(
            localDurationMs = song.durationMilliseconds.toLong(),
            remoteDurationMs = result.duration
        )

        val combined = if (durationScore != null) {
            textScore * 0.88 + durationScore * 0.12
        } else {
            textScore
        }

        val rank = rankBonus(rankIndex, textScore)

        val finalScore = (combined + rank).coerceIn(0.0, 1.0)

        return MatchScoreDetail(
            finalScore = finalScore,
            textScore = textScore,
            segmentScore = segScore,
            wholeScore = wholeScore,
            durationScore = durationScore,
            rankBonus = rank,
            localSegments = localSegments
        )
    }
    fun calculateCoverMatchScore(
        localSegments: List<String>,
        coverTitle: String?,
        coverArtist: String?,
        rankIndex: Int = -1
    ): Double {
        if (localSegments.isEmpty()) return 0.0

        val segScore = segmentMatchScore(localSegments, coverTitle, coverArtist)
        val wholeScore = wholeMatchScore(localSegments, coverTitle, coverArtist)

        val textScore = segScore * 0.75 + wholeScore * 0.25
        val rank = rankBonus(rankIndex, textScore, threshold = 0.55, maxBonus = 0.05)

        return (textScore + rank).coerceIn(0.0, 1.0)
    }
}
data class MatchScoreDetail(
    val finalScore: Double,
    val textScore: Double,
    val segmentScore: Double,
    val wholeScore: Double,
    val durationScore: Double?,
    val rankBonus: Double,
    val localSegments: List<String>
)