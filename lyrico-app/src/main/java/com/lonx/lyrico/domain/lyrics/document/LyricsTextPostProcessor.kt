package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.plugin.ResolvedFieldProcessRule
import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.domain.lyrics.LyricsTextTransform

/** One text policy for every lyric track and Ruby; timestamps and protocol attributes are untouched. */
internal class LyricsTextPostProcessor(private val rule: ResolvedFieldProcessRule) : LyricsPostProcessor {
    override fun process(document: LyricsDocument): LyricsDocument {
        if (!rule.trim && !rule.normalizeWhitespace && rule.scriptConversion == ConversionMode.NONE) return document
        return document.copy(
            metadata = document.metadata.copy(
                title = document.metadata.title?.let(::text),
                artist = document.metadata.artist?.let(::text),
                album = document.metadata.album?.let(::text),
                extra = document.metadata.extra.mapValues { (_, value) -> text(value) }
            ),
            tracks = document.tracks.map { track ->
                track.copy(lines = track.lines.map { line ->
                    if (line.words.isEmpty()) line.copy(text = text(line.text)) else {
                        val contents = fragments(line.words.map { it.text })
                        val words = line.words.mapIndexed { index, word ->
                            word.copy(text = contents[index], ruby = word.ruby.map { syllable ->
                                // Each timed annotation is a separate label, unlike adjacent lyric words.
                                syllable.copy(text = text(syllable.text))
                            })
                        }
                        line.copy(text = words.joinToString("") { it.text }, words = words)
                    }
                })
            }
        )
    }

    fun text(value: String): String {
        val converted = LyricsTextTransform.convertScript(value, rule.scriptConversion)
        val normalized = if (rule.normalizeWhitespace) converted.replace(horizontalWhitespace, " ") else converted
        return if (rule.trim) normalized.trim() else normalized
    }

    private fun fragments(values: List<String>): List<String> {
        val processed = values.map { value ->
            val converted = LyricsTextTransform.convertScript(value, rule.scriptConversion)
            if (rule.normalizeWhitespace) converted.replace(horizontalWhitespace, " ") else converted
        }.toMutableList()
        if (rule.normalizeWhitespace) {
            var previousSpace = false
            processed.indices.forEach { index ->
                if (previousSpace) processed[index] = processed[index].trimStart(' ', '\t', '\u00A0')
                if (processed[index].isNotEmpty()) previousSpace = processed[index].last().isWhitespace()
            }
        }
        if (rule.trim) {
            for (index in processed.indices) {
                processed[index] = processed[index].trimStart()
                if (processed[index].isNotEmpty()) break
            }
            for (index in processed.indices.reversed()) {
                processed[index] = processed[index].trimEnd()
                if (processed[index].isNotEmpty()) break
            }
        }
        return processed
    }

    private val horizontalWhitespace = Regex("[ \\t\\u00A0]+")
}
