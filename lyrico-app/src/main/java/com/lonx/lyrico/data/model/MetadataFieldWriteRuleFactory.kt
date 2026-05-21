package com.lonx.lyrico.data.model

import com.lonx.lyrico.data.model.lyrics.SearchResultExtraTarget
import com.lonx.lyrico.data.model.lyrics.SearchResultExtraWriteMode
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

fun SearchResultExtraTarget.toMetadataFieldTarget(): MetadataFieldTarget {
    return when (this) {
        SearchResultExtraTarget.TITLE -> MetadataFieldTarget.TITLE
        SearchResultExtraTarget.ARTIST -> MetadataFieldTarget.ARTIST
        SearchResultExtraTarget.ALBUM -> MetadataFieldTarget.ALBUM
        SearchResultExtraTarget.ALBUM_ARTIST -> MetadataFieldTarget.ALBUM_ARTIST
        SearchResultExtraTarget.DATE -> MetadataFieldTarget.DATE
        SearchResultExtraTarget.TRACK_NUMBER -> MetadataFieldTarget.TRACK_NUMBER
        SearchResultExtraTarget.COMMENT -> MetadataFieldTarget.COMMENT
        SearchResultExtraTarget.SUBTITLE -> MetadataFieldTarget.SUBTITLE
        SearchResultExtraTarget.COMPOSER -> MetadataFieldTarget.COMPOSER
        SearchResultExtraTarget.LYRICIST -> MetadataFieldTarget.LYRICIST
        SearchResultExtraTarget.GENRE -> MetadataFieldTarget.GENRE
        SearchResultExtraTarget.DISC_NUMBER -> MetadataFieldTarget.DISC_NUMBER
        SearchResultExtraTarget.LYRICS -> MetadataFieldTarget.LYRICS
        SearchResultExtraTarget.COVER -> MetadataFieldTarget.COVER
        SearchResultExtraTarget.REPLAY_GAIN_TRACK_GAIN -> MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN
        SearchResultExtraTarget.REPLAY_GAIN_TRACK_PEAK -> MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK
        SearchResultExtraTarget.REPLAY_GAIN_REFERENCE_LOUDNESS -> MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS
        SearchResultExtraTarget.CUSTOM -> MetadataFieldTarget.CUSTOM
    }
}

fun SearchResultExtraWriteMode.toMetadataWriteMode(): MetadataWriteMode {
    return when (this) {
        SearchResultExtraWriteMode.DISABLED -> MetadataWriteMode.DISABLED
        SearchResultExtraWriteMode.SUPPLEMENT -> MetadataWriteMode.SUPPLEMENT
        SearchResultExtraWriteMode.OVERWRITE -> MetadataWriteMode.OVERWRITE
    }
}
