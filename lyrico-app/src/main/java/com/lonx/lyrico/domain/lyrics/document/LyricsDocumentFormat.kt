package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.LyricFormat
import com.lonx.lyrico.data.lyrics.LyricLineTrack
import com.lonx.lyrico.data.lyrics.document.LyricsDocument

interface LyricsFormatParser {
    val format: LyricFormat
    fun parse(raw: String): LyricsDocument
}

interface LyricsFormatWriter {
    val format: LyricFormat
    fun write(
        document: LyricsDocument,
        lineOrder: List<LyricLineTrack> = com.lonx.lyrico.data.lyrics.DefaultLyricLineOrder
    ): String
}
