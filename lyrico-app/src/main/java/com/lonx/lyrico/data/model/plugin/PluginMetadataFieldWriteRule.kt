package com.lonx.lyrico.data.model.plugin

import com.lonx.lyrico.data.model.lyrics.SearchSource
import kotlinx.serialization.Serializable

@Serializable
data class PluginMetadataFieldWriteRule(
    val pluginId: String,
    val fieldKey: String,
    val target: PluginMetadataFieldTarget = PluginMetadataFieldTarget.COMMENT,
    val mode: PluginMetadataWriteMode = PluginMetadataWriteMode.DISABLED,
    val customTagKey: String? = null
) {
    val normalizedKey: String
        get() = PluginMetadataFieldKeyAlias.normalize(fieldKey)
}

object PluginMetadataFieldKeyAlias {
    private val aliases = mapOf(
        "NETEASE_163_KEY" to "netease_163_key",
        "REPLAY_GAIN_TRACK_GAIN" to "replaygain_track_gain",
        "REPLAY_GAIN_TRACK_PEAK" to "replaygain_track_peak",
        "REPLAY_GAIN_REFERENCE_LOUDNESS" to "replaygain_reference_loudness"
    )

    fun normalize(key: String): String {
        return aliases[key] ?: key
    }
}

object PluginMetadataFieldWriteRuleFactory {
    fun buildDefaultRules(searchSources: List<SearchSource>): List<PluginMetadataFieldWriteRule> {
        return searchSources.flatMap { source ->
            source.metadataFields
                .filter { it.writeable && !it.internal }
                .map { field ->
                    PluginMetadataFieldWriteRule(
                        pluginId = source.id,
                        fieldKey = field.key,
                        target = field.defaultTarget,
                        mode = field.defaultMode,
                        customTagKey = field.defaultCustomTagKey.takeIf { it.isNotBlank() }
                    )
                }
        }
    }

    fun mergeWithDeclaredFields(
        savedRules: List<PluginMetadataFieldWriteRule>,
        searchSources: List<SearchSource>
    ): List<PluginMetadataFieldWriteRule> {
        val defaults = buildDefaultRules(searchSources)

        val mergedDefaults = defaults.map { defaultRule ->
            savedRules.firstOrNull {
                it.pluginId == defaultRule.pluginId &&
                        it.normalizedKey == defaultRule.normalizedKey
            }?.let { savedRule ->
                savedRule.copy(fieldKey = savedRule.normalizedKey)
            } ?: defaultRule
        }

        val defaultKeys = defaults
            .map { it.pluginId to it.normalizedKey }
            .toSet()

        val extraSavedRules = savedRules
            .filter { savedRule ->
                savedRule.pluginId to savedRule.normalizedKey !in defaultKeys
            }
            .map { savedRule ->
                savedRule.copy(fieldKey = savedRule.normalizedKey)
            }

        return mergedDefaults + extraSavedRules
    }
}
