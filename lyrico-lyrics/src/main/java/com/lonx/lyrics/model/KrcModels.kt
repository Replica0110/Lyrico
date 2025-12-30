package com.lonx.lyrics.model


import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * 最小单位：字
 */
@Parcelize
data class LyricsWord(
    val start: Long, // 绝对开始时间 (毫秒)
    val end: Long,   // 绝对结束时间 (毫秒)
    val text: String
) : Parcelable

/**
 * 行单位
 */
@Parcelize
data class LyricsLine(
    val start: Long,
    val end: Long,
    val words: List<LyricsWord>
) : Parcelable

/**
 * 解析后的完整歌词结果
 */
@Parcelize
data class LyricsResult(
    val tags: Map<String, String>,         // 元数据 tags (ar, ti, al, etc.)
    val original: List<LyricsLine>,        // 原始逐字歌词
    val translated: List<LyricsLine>?,     // 翻译 (通常是逐行)
    val romanization: List<LyricsLine>?    // 罗马音 (逐字)
) : Parcelable


@Serializable
data class KrcLanguageRoot(
    val content: List<KrcLanguageItem>
)

@Serializable
data class KrcLanguageItem(
    val type: Int, // 0: 罗马音(逐字), 1: 翻译(逐行)
    val lyricContent: List<List<String>> // 二维数组
)