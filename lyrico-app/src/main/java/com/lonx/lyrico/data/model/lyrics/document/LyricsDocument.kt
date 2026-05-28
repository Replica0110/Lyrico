package com.lonx.lyrico.data.model.lyrics.document

import com.lonx.lyrico.data.model.lyrics.LyricFormat

data class LyricsDocument(
    val metadata: LyricsMetadata = LyricsMetadata(),
    val agents: List<LyricsAgent> = emptyList(),
    val tracks: List<LyricsTrack> = emptyList(),
    val extensions: ExtensionMap = ExtensionMap(),
    val sourceFormat: LyricFormat? = null
)

data class LyricsMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val language: String? = null,
    val offsetMs: Long? = null,
    val extra: Map<String, String> = emptyMap()
)

data class LyricsAgent(
    val id: String,
    val type: LyricsAgentType = LyricsAgentType.Unknown,
    val name: String? = null,
    val extensions: ExtensionMap = ExtensionMap()
)

enum class LyricsAgentType {
    Person,
    Group,
    Character,
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
    val extensions: ExtensionMap = ExtensionMap()
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
