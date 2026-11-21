package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsLineTrack
import com.lonx.lyrico.data.lyrics.document.LyricsDocument

interface LyricsFormatParser {
    val format: LyricsFormat
    fun parse(raw: String): LyricsDocument
}

interface LyricsFormatWriter {
    val format: LyricsFormat
    fun write(
        document: LyricsDocument,
        lineOrder: List<LyricsLineTrack> = com.lonx.lyrico.data.lyrics.defaultLyricsLineOrder,
        removeEmptyLines: Boolean = false
    ): String
}
