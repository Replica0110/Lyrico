package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.plugin.PluginConfigDependency
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginConfigFieldType
import com.lonx.lyrico.data.model.plugin.PluginConfigOption
import com.lonx.lyrico.data.model.plugin.PluginMetadataField
import com.lonx.lyrico.data.model.plugin.PluginMetadataWriteMode
import com.lonx.lyrico.data.model.lyrics.SearchResultExtraField
import com.lonx.lyrico.data.model.lyrics.SearchResultExtraTarget
import com.lonx.lyrico.data.model.lyrics.SearchResultExtraWriteMode
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

fun PluginMetadataField.toSearchResultExtraField(): SearchResultExtraField {
    return SearchResultExtraField(
        key = key,
        title = title,
        summary = summary,
        writeable = writeable,
        defaultTarget = defaultTarget.name.toSearchResultExtraTarget(),
        defaultMode = defaultMode.toSearchResultExtraWriteMode()
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

private fun PluginMetadataWriteMode.toSearchResultExtraWriteMode(): SearchResultExtraWriteMode {
    return when (this) {
        PluginMetadataWriteMode.DISABLED -> SearchResultExtraWriteMode.DISABLED
        PluginMetadataWriteMode.SUPPLEMENT -> SearchResultExtraWriteMode.SUPPLEMENT
        PluginMetadataWriteMode.OVERWRITE -> SearchResultExtraWriteMode.OVERWRITE
    }
}

private fun String.toSearchResultExtraTarget(): SearchResultExtraTarget {
    return SearchResultExtraTarget.entries.firstOrNull { it.name == this }
        ?: SearchResultExtraTarget.COMMENT
}
