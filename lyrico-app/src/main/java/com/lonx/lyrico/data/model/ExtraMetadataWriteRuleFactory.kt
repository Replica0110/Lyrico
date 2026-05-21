package com.lonx.lyrico.data.model

import com.lonx.lyrics.model.SearchResultExtraTarget
import com.lonx.lyrics.model.SearchResultExtraWriteMode
import com.lonx.lyrics.model.SearchSource

object ExtraMetadataWriteRuleFactory {
    fun buildDefaultRules(
        searchSources: List<SearchSource>
    ): List<ExtraMetadataWriteRule> {
        return searchSources.flatMap { source ->
            source.extraFields
                .filter { it.writeable }
                .map { field ->
                    ExtraMetadataWriteRule(
                        key = field.key,
                        source = source.sourceType,
                        target = field.defaultTarget.toAppTarget(),
                        mode = field.defaultMode.toAppMode()
                    )
                }
        }
    }

    fun mergeWithDeclaredFields(
        savedRules: List<ExtraMetadataWriteRule>,
        searchSources: List<SearchSource>
    ): List<ExtraMetadataWriteRule> {
        val defaults = buildDefaultRules(searchSources)
        return defaults.map { defaultRule ->
            savedRules.firstOrNull {
                it.source == defaultRule.source && it.normalizedKey == defaultRule.normalizedKey
            }?.let { it.copy(key = it.normalizedKey) } ?: defaultRule
        }
    }
}

fun SearchResultExtraTarget.toAppTarget(): ExtraMetadataTarget {
    return when (this) {
        SearchResultExtraTarget.COMMENT -> ExtraMetadataTarget.COMMENT
        SearchResultExtraTarget.SUBTITLE -> ExtraMetadataTarget.SUBTITLE
        SearchResultExtraTarget.COMPOSER -> ExtraMetadataTarget.COMPOSER
        SearchResultExtraTarget.LYRICIST -> ExtraMetadataTarget.LYRICIST
        SearchResultExtraTarget.GENRE -> ExtraMetadataTarget.GENRE
        SearchResultExtraTarget.DISC_NUMBER -> ExtraMetadataTarget.DISC_NUMBER
        SearchResultExtraTarget.REPLAY_GAIN_TRACK_GAIN -> ExtraMetadataTarget.REPLAY_GAIN_TRACK_GAIN
        SearchResultExtraTarget.REPLAY_GAIN_TRACK_PEAK -> ExtraMetadataTarget.REPLAY_GAIN_TRACK_PEAK
        SearchResultExtraTarget.REPLAY_GAIN_REFERENCE_LOUDNESS -> ExtraMetadataTarget.REPLAY_GAIN_REFERENCE_LOUDNESS
    }
}

fun SearchResultExtraWriteMode.toAppMode(): ExtraWriteMode {
    return when (this) {
        SearchResultExtraWriteMode.DISABLED -> ExtraWriteMode.DISABLED
        SearchResultExtraWriteMode.SUPPLEMENT -> ExtraWriteMode.SUPPLEMENT
        SearchResultExtraWriteMode.OVERWRITE -> ExtraWriteMode.OVERWRITE
    }
}
