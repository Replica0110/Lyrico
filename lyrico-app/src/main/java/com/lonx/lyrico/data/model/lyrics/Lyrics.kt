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
    val words: List<LyricsWord>,
    // 行级扩展属性透传（structured 协议 Line 第 4 元素）：
    // key 为带命名空间前缀的 TTML 属性名（如 "ttm:agent"、"itunes:song-part"），value 为属性值。
    // 写回 TTML 时输出到对应 <p>；song-part、key 和 div 时间由 writer 统一管理。
    val extensions: Map<String, String> = emptyMap()
) : Parcelable

/**
 * structured 协议：演唱者信息（对应 TTML head 的 <ttm:agent>）。
 * 插件以对象数组形式提供：[{ "id": "v1", "type": "person", "name": "艺人A" }]
 *
 * @param id   演唱者唯一引用 ID（写入 xml:id，正文 <p> 通过 ttm:agent="id" 引用）
 * @param type 类型：person / character / organization / group / other（AMLL 规范 4.1）
 * @param name 演唱者名称（写入 <ttm:name type="full">）
 */
@Parcelize
data class LyricsAgentEntry(
    val id: String,
    val type: String? = null,
    val name: String? = null
) : Parcelable

/**
 * structured 协议：head 元数据元素树节点（与 TTML <head> 内元素一一对应）。
 * 插件自行构造树形结构（同级就同级、children 就 children），宿主校验官方 key 后原样写回。
 *
 * 解析规则（宿主 PluginJsonParser）：
 * 1. 官方 key + 官方结构（如 songwriters 包裹 songwriter children）→ 按官方规范写回；
 * 2. 非官方 key → 原样透传写回（保留元素树）；
 * 3. 官方 key + 不符合官方的结构 → 丢弃并输出 warn 日志。
 *
 * @param name       元素名（小写为 AMLL 官方元素，如 "songwriters"；可带命名空间前缀如 "amll:meta"）
 * @param namespace  命名空间 URI（name 带前缀且非 ttm/itunes 时必须提供，宿主据此在根节点补 xmlns 声明）
 * @param attributes 元素属性（如 amll:meta 的 {"key": "musicName", "value": "歌曲名"}）
 * @param text       元素文本内容（如 <songwriter>作者名</songwriter> 的作者名）
 * @param children   子元素列表（同级重复的同名元素 = 列表中多个同名节点）
 */
@Parcelize
data class LyricsMetadataElement(
    val name: String,
    val namespace: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val text: String? = null,
    val children: List<LyricsMetadataElement> = emptyList()
) : Parcelable

@Parcelize
data class LyricsResult(
    val tags: Map<String, String>,
    val original: List<LyricsLine>,
    val translated: List<LyricsLine>?,
    val romanization: List<LyricsLine>?,
    val payloadType: LyricsPayloadType = LyricsPayloadType.STRUCTURED,
    val isWordByWord: Boolean = true,
    val rawPlainLrc: String = "",
    val rawVerbatimLrc: String = "",
    val rawEnhancedLrc: String = "",
    val rawTtml: String = "",
    val rawMultiPersonEnhancedLrc: String = "",
    // structured 协议扩展：演唱者列表（写回 TTML head <ttm:agent>）；旧插件为空
    val agents: List<LyricsAgentEntry> = emptyList(),
    // structured 协议扩展：head 元数据元素树（官方 key 按规范写回 / 非官方原样透传）；旧插件为空
    val metadata: List<LyricsMetadataElement> = emptyList(),
    // structured 协议扩展：根 <tt> 属性与轨语言码（写回 TTML 用）；旧插件不传为空。
    // timing = 词级时间标志（根 <tt itunes:timing="...">，如 "Word"）；
    // language = 原文语言码（根 <tt xml:lang="...">，BCP47 如 "zh-Hans"）；
    // translatedLang / romanizationLang = 翻译/音译轨语言码（写回对应轨元素的 xml:lang）
    val timing: String = "",
    val language: String = "",
    val translatedLang: String = "",
    val romanizationLang: String = ""
) : Parcelable

data class LyricsCandidateResult(
    val song: SongSearchResult,
    val lyrics: LyricsResult
)

enum class LyricsPayloadType {
    STRUCTURED,
    RAW_PLAIN_LRC,
    RAW_VERBATIM_LRC,
    RAW_ENHANCED_LRC,
    RAW_TTML,
    RAW_MULTI_PERSON_ENHANCED_LRC
}

fun LyricsPayloadType.isRaw(): Boolean {
    return this != LyricsPayloadType.STRUCTURED
}

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
