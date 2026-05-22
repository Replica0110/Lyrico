package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.MetadataFieldTarget
import com.lonx.lyrico.data.model.MetadataWriteMode
import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldTarget
import com.lonx.lyrico.data.model.plugin.PluginMetadataWriteMode
import com.lonx.lyrico.data.model.lyrics.SearchSourceCapability

fun PluginCapability.toSearchSourceCapability(): SearchSourceCapability {
    return when (this) {
        PluginCapability.SEARCH_SONGS -> SearchSourceCapability.SEARCH_SONGS
        PluginCapability.GET_LYRICS -> SearchSourceCapability.GET_LYRICS
        PluginCapability.SEARCH_COVERS -> SearchSourceCapability.SEARCH_COVERS
    }
}

fun PluginMetadataWriteMode.toMetadataWriteMode(): MetadataWriteMode {
    return when (this) {
        PluginMetadataWriteMode.DISABLED -> MetadataWriteMode.DISABLED
        PluginMetadataWriteMode.SUPPLEMENT -> MetadataWriteMode.SUPPLEMENT
        PluginMetadataWriteMode.OVERWRITE -> MetadataWriteMode.OVERWRITE
    }
}

fun PluginMetadataFieldTarget.toMetadataFieldTarget(): MetadataFieldTarget {
    return MetadataFieldTarget.entries.firstOrNull { it.name == name }
        ?: MetadataFieldTarget.COMMENT
}
