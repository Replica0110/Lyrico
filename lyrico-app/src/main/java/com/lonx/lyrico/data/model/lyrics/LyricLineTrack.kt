package com.lonx.lyrico.data.model.lyrics

import androidx.annotation.StringRes
import com.lonx.lyrico.R
import kotlinx.serialization.Serializable

@Serializable
enum class LyricLineTrack(
    @field:StringRes val labelRes: Int
) {
    ORIGINAL(R.string.lyric_line_original),
    ROMANIZATION(R.string.lyric_line_romanization),
    TRANSLATION(R.string.lyric_line_translation);
}

val DefaultLyricLineOrder = listOf(
    LyricLineTrack.ORIGINAL,
    LyricLineTrack.ROMANIZATION,
    LyricLineTrack.TRANSLATION
)

fun List<LyricLineTrack>.normalizedLyricLineOrder(): List<LyricLineTrack> {
    return (this + DefaultLyricLineOrder).distinct()
        .filter { it in DefaultLyricLineOrder }
}

fun visibleLyricLineTracks(
    showRomanization: Boolean,
    showTranslation: Boolean,
    onlyTranslationIfAvailable: Boolean
): List<LyricLineTrack> {
    if (showTranslation && onlyTranslationIfAvailable) {
        return listOf(LyricLineTrack.TRANSLATION)
    }

    return buildList {
        add(LyricLineTrack.ORIGINAL)
        if (showRomanization) add(LyricLineTrack.ROMANIZATION)
        if (showTranslation) add(LyricLineTrack.TRANSLATION)
    }
}
