package com.lonx.lyrico.data.song.tag

import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.utils.LyricDecoder

object LyricsTagRoutingPolicy {
    fun preferredTarget(
        lyrics: String?,
        preferTtmlLyricsTag: Boolean
    ): MetadataFieldTarget {
        return if (preferTtmlLyricsTag && isTtml(lyrics)) {
            MetadataFieldTarget.TTML_LYRICS
        } else {
            MetadataFieldTarget.LYRICS
        }
    }

    fun route(
        mutation: AudioTagMutation,
        preferTtmlLyricsTag: Boolean
    ): AudioTagMutation {
        val routedFields = mutation.fields.toMutableMap()

        val explicitTtml = routedFields[AudioTagFieldKey.TtmlLyrics]
        if (explicitTtml is FieldMutation.Set &&
            explicitTtml.value.isNotBlank() &&
            !isTtml(explicitTtml.value)
        ) {
            routedFields.remove(AudioTagFieldKey.TtmlLyrics)
        }

        val lyricsMutation = routedFields[AudioTagFieldKey.Lyrics]
        if (preferTtmlLyricsTag &&
            lyricsMutation is FieldMutation.Set &&
            isTtml(lyricsMutation.value)
        ) {
            routedFields.remove(AudioTagFieldKey.Lyrics)
            routedFields[AudioTagFieldKey.TtmlLyrics] = lyricsMutation
        }

        return mutation.copy(fields = routedFields)
    }

    fun isTtml(lyrics: String?): Boolean {
        return lyrics?.let(LyricDecoder::detectFormat) == LyricFormat.TTML
    }
}
