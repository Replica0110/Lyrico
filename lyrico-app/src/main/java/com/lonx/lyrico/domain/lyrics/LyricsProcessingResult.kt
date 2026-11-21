package com.lonx.lyrico.domain.lyrics

/**
 * Processing outcomes for applying text to a draft or saved tag.
 * [Empty] means no lyric content can be written, including metadata-only TTML and empty anchors.
 * A linked translation, romanization or background line can supply content for a timed anchor.
 */
sealed interface LyricsProcessingResult {
    data class Changed(val text: String) : LyricsProcessingResult
    data object Unchanged : LyricsProcessingResult
    data object Empty : LyricsProcessingResult
    data class Unsupported(val reason: Reason) : LyricsProcessingResult

    companion object {
        internal fun fromText(raw: String, output: String?): LyricsProcessingResult = when {
            output.isNullOrBlank() -> Empty
            output == raw -> Unchanged
            else -> Changed(output)
        }
    }

    enum class Reason {
        UNRECOGNIZED_FORMAT,
        WORD_TIMING_REQUIRED,
        SOURCE_CHANGED
    }
}
