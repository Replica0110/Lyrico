package com.lonx.lyrico.data.model

import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.plugin.source.toMetadataFieldTarget
import com.lonx.lyrico.plugin.source.toMetadataWriteMode

object MetadataFieldWriteRuleFactory {
    fun buildDefaultRules(searchSources: List<SearchSource>): List<MetadataFieldWriteRule> {
        return searchSources.flatMap { source ->
            source.metadataFields
                .filter { it.writeable && !it.internal }
                .map { field ->
                    MetadataFieldWriteRule(
                        pluginId = source.id,
                        fieldKey = field.key,
                        target = field.defaultTarget.toMetadataFieldTarget(),
                        mode = field.defaultMode.toMetadataWriteMode(),
                        customTagKey = field.defaultCustomTagKey.takeIf { it.isNotBlank() }
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
                it.pluginId == defaultRule.pluginId && it.normalizedKey == defaultRule.normalizedKey
            }?.let { it.copy(fieldKey = it.normalizedKey) } ?: defaultRule
        }
    }
}
