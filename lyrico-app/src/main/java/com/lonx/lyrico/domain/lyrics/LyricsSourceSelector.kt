package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsResult

internal data class RawLyricsSource(
    val text: String,
    val format: LyricsFormat,
    val isTargetPayload: Boolean = false
)

/** Owns the existing preferred-source and secondary-source order in one place. */
internal object LyricsSourceSelector {
    fun select(result: LyricsResult, config: LyricsRenderConfig): RawLyricsSource? {
        fun source(raw: String, format: LyricsFormat, target: Boolean = false): RawLyricsSource? =
            raw.takeIf { it.isNotBlank() }?.let { RawLyricsSource(it, format, target) }

        val matching = when (config.format) {
            LyricsFormat.PLAIN_LRC -> result.rawPlainLrc
            LyricsFormat.VERBATIM_LRC -> result.rawVerbatimLrc
            LyricsFormat.ENHANCED_LRC -> result.rawEnhancedLrc
            LyricsFormat.TTML -> result.rawTtml
        }
        source(matching, config.format, target = true)?.let { return it }

        val plain = source(result.rawPlainLrc, LyricsFormat.PLAIN_LRC)
        val verbatim = source(result.rawVerbatimLrc, LyricsFormat.VERBATIM_LRC)
        val enhanced = source(result.rawEnhancedLrc, LyricsFormat.ENHANCED_LRC)
        val ttml = source(result.rawTtml, LyricsFormat.TTML)
        val multiPerson = source(result.rawMultiPersonEnhancedLrc, LyricsFormat.ENHANCED_LRC)

        // Preserve the document pipeline's source preference before considering secondary fields.
        if (config.showTranslation || config.onlyTranslationIfAvailable) {
            ttml?.let { return it }
        }
        enhanced?.let { return it }
        verbatim?.let { return it }
        plain?.let { return it }

        // A multi-person enhanced payload uses the same time model and can feed every writer.
        return multiPerson ?: ttml
    }
}
