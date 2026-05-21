package com.lonx.lyrico.data.model.lyrics

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
data class LyricsData(
    val original: String?,
    val translated: String? = null,
    val type: String = "lrc",
    val romanization: String? = null
) : Parcelable

@Parcelize
data class LyricsWord(
    val start: Long,
    val end: Long,
    val text: String
) : Parcelable

@Parcelize
data class LyricsLine(
    val start: Long,
    val end: Long,
    val words: List<LyricsWord>
) : Parcelable

@Parcelize
data class LyricsResult(
    val tags: Map<String, String>,
    val original: List<LyricsLine>,
    val translated: List<LyricsLine>?,
    val romanization: List<LyricsLine>?,
    val isWordByWord: Boolean = true,
    val rawPlainLrc: String = "",
    val rawVerbatimLrc: String = "",
    val rawEnhancedLrc: String = "",
    val rawTtml: String = "",
    val rawMultiPersonEnhancedLrc: String = ""
) : Parcelable

fun List<LyricsLine>.isWordByWord(): Boolean {
    return this.any { it.words.size > 1 }
}

@Serializable
@Parcelize
data class KrcLanguageRoot(
    val content: List<KrcLanguageItem>
) : Parcelable

@Serializable
@Parcelize
data class KrcLanguageItem(
    val type: Int,
    val lyricContent: List<List<String>>
) : Parcelable
