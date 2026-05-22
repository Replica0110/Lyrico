package com.lonx.lyrico.data.model

import com.lonx.lyrico.data.model.lyrics.SearchResultMetadataTarget
import com.lonx.lyrico.data.model.lyrics.SearchResultMetadataWriteMode
import com.lonx.lyrico.data.model.lyrics.SearchSource

object MetadataFieldWriteRuleFactory {
    fun buildDefaultRules(searchSources: List<SearchSource>): List<MetadataFieldWriteRule> {
        return searchSources.flatMap { source ->
            source.metadataFields
                .filter { it.writeable }
                .map { field ->
                    MetadataFieldWriteRule(
                        sourceId = source.id,
                        fieldKey = field.key,
                        target = field.defaultTarget.toMetadataFieldTarget(),
                        mode = field.defaultMode.toMetadataWriteMode()
                    )
                }
        }
    }

    fun mergeWithDeclaredFields(
        savedRules: List<MetadataFieldWriteRule>,
        searchSources: List<SearchSource>
    ): List<MetadataFieldWriteRule> {
        val defaults = buildDefaultRules(searchSources)
        return defaults.map { defaultRule ->
            savedRules.firstOrNull {
                it.sourceId == defaultRule.sourceId && it.normalizedKey == defaultRule.normalizedKey
            }?.let { it.copy(fieldKey = it.normalizedKey) } ?: defaultRule
        }
    }
}

fun SearchResultMetadataTarget.toMetadataFieldTarget(): MetadataFieldTarget {
    return when (this) {
        SearchResultMetadataTarget.TITLE -> MetadataFieldTarget.TITLE
        SearchResultMetadataTarget.ARTIST -> MetadataFieldTarget.ARTIST
        SearchResultMetadataTarget.ALBUM -> MetadataFieldTarget.ALBUM
        SearchResultMetadataTarget.ALBUM_ARTIST -> MetadataFieldTarget.ALBUM_ARTIST
        SearchResultMetadataTarget.DATE -> MetadataFieldTarget.DATE
        SearchResultMetadataTarget.TRACK_NUMBER -> MetadataFieldTarget.TRACK_NUMBER
        SearchResultMetadataTarget.COMMENT -> MetadataFieldTarget.COMMENT
        SearchResultMetadataTarget.SUBTITLE -> MetadataFieldTarget.SUBTITLE
        SearchResultMetadataTarget.COMPOSER -> MetadataFieldTarget.COMPOSER
        SearchResultMetadataTarget.LYRICIST -> MetadataFieldTarget.LYRICIST
        SearchResultMetadataTarget.GENRE -> MetadataFieldTarget.GENRE
        SearchResultMetadataTarget.DISC_NUMBER -> MetadataFieldTarget.DISC_NUMBER
        SearchResultMetadataTarget.LYRICS -> MetadataFieldTarget.LYRICS
        SearchResultMetadataTarget.COVER -> MetadataFieldTarget.COVER
        SearchResultMetadataTarget.REPLAY_GAIN_TRACK_GAIN -> MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN
        SearchResultMetadataTarget.REPLAY_GAIN_TRACK_PEAK -> MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK
        SearchResultMetadataTarget.REPLAY_GAIN_REFERENCE_LOUDNESS -> MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS
        SearchResultMetadataTarget.CUSTOM -> MetadataFieldTarget.CUSTOM
    }
}

fun SearchResultMetadataWriteMode.toMetadataWriteMode(): MetadataWriteMode {
    return when (this) {
        SearchResultMetadataWriteMode.DISABLED -> MetadataWriteMode.DISABLED
        SearchResultMetadataWriteMode.SUPPLEMENT -> MetadataWriteMode.SUPPLEMENT
        SearchResultMetadataWriteMode.OVERWRITE -> MetadataWriteMode.OVERWRITE
    }
}
