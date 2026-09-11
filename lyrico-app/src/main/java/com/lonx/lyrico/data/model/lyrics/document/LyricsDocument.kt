package com.lonx.lyrico.data.model.lyrics.document

import com.lonx.lyrico.data.model.lyrics.LyricFormat

data class LyricsDocument(
    val metadata: LyricsMetadata = LyricsMetadata(),
    val agents: List<LyricsAgent> = emptyList(),
    val tracks: List<LyricsTrack> = emptyList(),
    val extensions: ExtensionMap = ExtensionMap(),
    val bodyExtensions: ExtensionMap = ExtensionMap(),
    val headMetadataElements: List<ExtensionElement> = emptyList(),
    val itunesMetadataElements: List<ExtensionElement> = emptyList(),
    val sourceFormat: LyricFormat? = null
)

data class LyricsMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val language: String? = null,
    // 根 <tt itunes:timing="..."> 词级时间标志（如 "Word"），保真往返
    val timing: String? = null,
    val offsetMs: Long? = null,
    val extra: Map<String, String> = emptyMap()
)

data class LyricsAgent(
    val id: String,
    val type: LyricsAgentType = LyricsAgentType.Unknown,
    val name: String? = null,
    val rawType: String? = null,
    val extensions: ExtensionMap = ExtensionMap()
)

enum class LyricsAgentType {
    Person,
    Group,
    Character,
    Organization,
    Other,
    Narrator,
    Unknown
}

data class LyricsTrack(
    val type: LyricsTrackType,
    val language: String? = null,
    val lines: List<LyricsDocumentLine> = emptyList(),
    val extensions: ExtensionMap = ExtensionMap()
)

enum class LyricsTrackType {
    Original,
    Translation,
    Romanization,
    Background,
    Other
}

data class LyricsDocumentLine(
    val id: String? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val text: String = "",
    val words: List<LyricsDocumentWord> = emptyList(),
    val linkKey: String? = null,
    val agentId: String? = null,
    val extensions: ExtensionMap = ExtensionMap()
)

data class LyricsDocumentWord(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val text: String,
    // 单注音文本（无独立音节时间）：仅兼容旧形态/非 TTML 解析路径；
    // 与 rubySyllables 二选一——多音节解析（TTML / structured 协议）一律走 rubySyllables
    val rubyText: String? = null,
    // 多音节注音（AMLL TTML 规范 6.x Ruby 标注）：一个基文本（汉字）可对应多个
    // <span tts:ruby="text"> 注音音节，各自带独立 begin/end（如「詮」→ せ / ん）。
    // 写回时在 textContainer 内逐音节输出；空列表 = 无注音
    val rubySyllables: List<LyricsRubySyllable> = emptyList(),
    val extensions: ExtensionMap = ExtensionMap()
)

/**
 * Ruby 注音音节（对应一个 <span tts:ruby="text" begin end>注音</span>）。
 * @param text    注音文本（假名/拼音等）
 * @param startMs 音节开始时间（绝对毫秒）；源文件缺时间戳时为 null，写回省略 begin
 * @param endMs   音节结束时间（绝对毫秒）；源文件缺时间戳时为 null，写回省略 end
 */
data class LyricsRubySyllable(
    val text: String,
    val startMs: Long? = null,
    val endMs: Long? = null
)

data class ExtensionMap(
    val attributes: Map<QualifiedName, String> = emptyMap(),
    val elements: List<ExtensionElement> = emptyList(),
    val values: Map<String, String> = emptyMap()
)

data class ExtensionElement(
    val name: QualifiedName,
    val attributes: Map<QualifiedName, String> = emptyMap(),
    val text: String? = null,
    val children: List<ExtensionElement> = emptyList()
)

data class QualifiedName(
    val namespaceUri: String? = null,
    val localName: String,
    val prefix: String? = null
)
