package com.lonx.lyrico.data.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.model.plugin.normalizedPluginCapabilities
import com.lonx.lyrico.data.model.plugin.displaySourceTypes
import kotlinx.serialization.json.Json

const val DEFAULT_PLUGIN_CAPABILITIES_JSON = "[\"searchSongs\"]"
const val DEFAULT_PLUGIN_CAPABILITIES_SQL = "'[\"searchSongs\"]'"

@Entity(tableName = "source_plugins")
data class SourcePluginEntity(
    @PrimaryKey val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val author: String,
    val description: String,
    val apiVersion: Int,
    @ColumnInfo(defaultValue = "1")
    val minHostApiVersion: Int = 1,
    val pluginDir: String,
    val entryFile: String,
    val includeDirsJson: String,
    @ColumnInfo(defaultValue = DEFAULT_PLUGIN_CAPABILITIES_SQL)
    val capabilitiesJson: String = DEFAULT_PLUGIN_CAPABILITIES_JSON,
    val customName: String?,
    val iconPath: String?,
    val enabled: Boolean,
    @ColumnInfo(defaultValue = "0")
    val metadataEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val lyricsEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val coverEnabled: Boolean = false,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "0")
    val metadataSortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val lyricsSortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val coverSortOrder: Int = 0,
    val installedAt: Long,
    val updatedAt: Long
)

val SourcePluginEntity.displayName: String
    get() = customName?.trim()?.takeIf { it.isNotEmpty() } ?: name

val SourcePluginEntity.capabilities: Set<PluginCapability>
    get() = runCatching {
        Json.decodeFromString<Set<PluginCapability>>(capabilitiesJson)
    }.getOrDefault(emptySet()).normalizedPluginCapabilities()

val SourcePluginEntity.displaySourceTypes: Set<PluginSourceType>
    get() = capabilities.displaySourceTypes()

fun SourcePluginEntity.sortOrderFor(sourceType: PluginSourceType): Int = when (sourceType) {
    PluginSourceType.AGGREGATED -> sortOrder
    PluginSourceType.METADATA -> metadataSortOrder
    PluginSourceType.LYRICS -> lyricsSortOrder
    PluginSourceType.COVER -> coverSortOrder
}

fun SourcePluginEntity.isEnabledFor(sourceType: PluginSourceType): Boolean = when (sourceType) {
    PluginSourceType.AGGREGATED -> enabled
    PluginSourceType.METADATA -> metadataEnabled
    PluginSourceType.LYRICS -> lyricsEnabled
    PluginSourceType.COVER -> coverEnabled
}

val SourcePluginEntity.isEnabledAnywhere: Boolean
    get() = enabled || metadataEnabled || lyricsEnabled || coverEnabled
