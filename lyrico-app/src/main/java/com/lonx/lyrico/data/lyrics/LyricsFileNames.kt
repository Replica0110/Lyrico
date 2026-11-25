package com.lonx.lyrico.data.lyrics

import com.lonx.lyrico.domain.lyrics.LyricsParser

/** Callers provide an audio base name; dots inside that name are preserved. */
object LyricsFileNames {
    fun defaultName(baseName: String?, lyrics: String?): String? {
        if (lyrics.isNullOrBlank()) return null
        val extension = if (LyricsParser.detectFormat(lyrics) == LyricsFormat.TTML) "ttml" else "lrc"
        return "${baseName.orEmpty()}.$extension"
    }
}
