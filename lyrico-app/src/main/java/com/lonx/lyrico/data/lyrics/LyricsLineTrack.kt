package com.lonx.lyrico.data.lyrics

import androidx.annotation.StringRes
import com.lonx.lyrico.R
import kotlinx.serialization.Serializable

@Serializable
enum class LyricsLineTrack(
    @field:StringRes val labelRes: Int
) {
    ORIGINAL(R.string.lyric_line_original),
    ROMANIZATION(R.string.lyric_line_romanization),
    TRANSLATION(R.string.lyric_line_translation);
}

val defaultLyricsLineOrder = listOf(
    LyricsLineTrack.ORIGINAL,
    LyricsLineTrack.ROMANIZATION,
    LyricsLineTrack.TRANSLATION
)

fun List<LyricsLineTrack>.normalizedLyricsLineOrder(): List<LyricsLineTrack> {
    return (this + defaultLyricsLineOrder).distinct()
        .filter { it in defaultLyricsLineOrder }
}

fun visibleLyricsLineTracks(
    showRomanization: Boolean,
    showTranslation: Boolean,
    onlyTranslationIfAvailable: Boolean
): List<LyricsLineTrack> {
    if (showTranslation && onlyTranslationIfAvailable) {
        return listOf(LyricsLineTrack.TRANSLATION)
    }

    return buildList {
        add(LyricsLineTrack.ORIGINAL)
        if (showRomanization) add(LyricsLineTrack.ROMANIZATION)
        if (showTranslation) add(LyricsLineTrack.TRANSLATION)
    }
}
