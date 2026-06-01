package com.lonx.lyrico.data.model.plugin

import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.MetadataWriteMode
import com.lonx.lyrico.data.model.lyrics.SearchSource
import kotlinx.serialization.Serializable

@Serializable
data class PluginMetadataFieldWriteRule(
    val pluginId: String,
    val fieldKey: String,
    val target: MetadataFieldTarget = MetadataFieldTarget.COMMENT,
    val mode: MetadataWriteMode = MetadataWriteMode.DISABLED,
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
        return emptyList()
    }

    fun mergeWithDeclaredFields(
        savedRules: List<PluginMetadataFieldWriteRule>,
        searchSources: List<SearchSource>
    ): List<PluginMetadataFieldWriteRule> {
        return emptyList()
    }
}
