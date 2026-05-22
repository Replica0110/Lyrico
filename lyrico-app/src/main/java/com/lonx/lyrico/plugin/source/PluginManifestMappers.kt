package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.plugin.PluginConfigDependency
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginConfigFieldType
import com.lonx.lyrico.data.model.plugin.PluginConfigOption
import com.lonx.lyrico.data.model.plugin.PluginMetadataField
import com.lonx.lyrico.data.model.plugin.PluginMetadataWriteMode
import com.lonx.lyrico.data.model.lyrics.SearchResultMetadataField
import com.lonx.lyrico.data.model.lyrics.SearchResultMetadataTarget
import com.lonx.lyrico.data.model.lyrics.SearchResultMetadataWriteMode
import com.lonx.lyrico.data.model.lyrics.SearchSourceCapability
import com.lonx.lyrico.data.model.lyrics.SourceConfigDependency
import com.lonx.lyrico.data.model.lyrics.SourceConfigField
import com.lonx.lyrico.data.model.lyrics.SourceConfigFieldType
import com.lonx.lyrico.data.model.lyrics.SourceConfigOption

fun PluginCapability.toSearchSourceCapability(): SearchSourceCapability {
    return when (this) {
        PluginCapability.SEARCH_SONGS -> SearchSourceCapability.SEARCH_SONGS
        PluginCapability.GET_LYRICS -> SearchSourceCapability.GET_LYRICS
        PluginCapability.SEARCH_COVERS -> SearchSourceCapability.SEARCH_COVERS
    }
}

fun PluginConfigField.toSourceConfigField(): SourceConfigField {
    return SourceConfigField(
        key = key,
        title = title,
        summary = summary,
        type = type.toSourceConfigFieldType(),
        required = required,
        defaultValue = defaultValue,
        options = options.map { it.toSourceConfigOption() },
        dependency = dependency?.toSourceConfigDependency()
    )
}

fun PluginMetadataField.toSearchResultMetadataField(): SearchResultMetadataField {
    return SearchResultMetadataField(
        key = key,
        title = title,
        summary = summary,
        writeable = writeable,
        defaultTarget = defaultTarget.name.toSearchResultMetadataTarget(),
        defaultMode = defaultMode.toSearchResultMetadataWriteMode()
    )
}

private fun PluginConfigFieldType.toSourceConfigFieldType(): SourceConfigFieldType {
    return when (this) {
        PluginConfigFieldType.TEXT -> SourceConfigFieldType.TEXT
        PluginConfigFieldType.PASSWORD -> SourceConfigFieldType.PASSWORD
        PluginConfigFieldType.NUMBER -> SourceConfigFieldType.NUMBER
        PluginConfigFieldType.SWITCH -> SourceConfigFieldType.SWITCH
        PluginConfigFieldType.DROPDOWN -> SourceConfigFieldType.DROPDOWN
    }
}

private fun PluginConfigOption.toSourceConfigOption(): SourceConfigOption {
    return SourceConfigOption(value = value, label = label)
}

private fun PluginConfigDependency.toSourceConfigDependency(): SourceConfigDependency {
    return when (this) {
        is PluginConfigDependency.Match -> SourceConfigDependency.Match(key = key, value = value)
        is PluginConfigDependency.And -> SourceConfigDependency.And(
            conditions = conditions.map { it.toSourceConfigDependency() }
        )
        is PluginConfigDependency.Or -> SourceConfigDependency.Or(
            conditions = conditions.map { it.toSourceConfigDependency() }
        )
        is PluginConfigDependency.Not -> SourceConfigDependency.Not(
            condition = condition.toSourceConfigDependency()
        )
    }
}

private fun PluginMetadataWriteMode.toSearchResultMetadataWriteMode(): SearchResultMetadataWriteMode {
    return when (this) {
        PluginMetadataWriteMode.DISABLED -> SearchResultMetadataWriteMode.DISABLED
        PluginMetadataWriteMode.SUPPLEMENT -> SearchResultMetadataWriteMode.SUPPLEMENT
        PluginMetadataWriteMode.OVERWRITE -> SearchResultMetadataWriteMode.OVERWRITE
    }
}

private fun String.toSearchResultMetadataTarget(): SearchResultMetadataTarget {
    return SearchResultMetadataTarget.entries.firstOrNull { it.name == this }
        ?: SearchResultMetadataTarget.COMMENT
}
