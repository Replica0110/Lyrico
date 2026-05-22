package com.lonx.lyrico.data.model.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val author: String = "",
    val description: String = "",
    val apiVersion: Int,
    val entry: String = "source.js",
    val includeDirs: List<String> = emptyList(),
    val icon: String? = null,
    val capabilities: Set<PluginCapability> = emptySet(),
    val requiredHostApis: Set<String> = emptySet(),
    val configFields: List<PluginConfigField> = emptyList(),
    val metadataFields: List<PluginMetadataField> = emptyList()
)

@Serializable
enum class PluginCapability {
    @SerialName("searchSongs")
    SEARCH_SONGS,
    @SerialName("getLyrics")
    GET_LYRICS,
    @SerialName("searchCovers")
    SEARCH_COVERS
}

@Serializable
data class PluginConfigField(
    val key: String,
    val title: String,
    val summary: String = "",
    val type: PluginConfigFieldType,
    val required: Boolean = false,
    val defaultValue: String = "",
    val options: List<PluginConfigOption> = emptyList(),
    val dependency: PluginConfigDependency? = null
)

@Serializable
enum class PluginConfigFieldType {
    @SerialName("text")
    TEXT,
    @SerialName("password")
    PASSWORD,
    @SerialName("number")
    NUMBER,
    @SerialName("switch")
    SWITCH,
    @SerialName("dropdown")
    DROPDOWN
}

@Serializable
data class PluginConfigOption(
    val value: String,
    val label: String
)

@Serializable
sealed interface PluginConfigDependency {
    @Serializable
    data class Match(
        val key: String,
        val value: String
    ) : PluginConfigDependency

    @Serializable
    data class And(
        val conditions: List<PluginConfigDependency>
    ) : PluginConfigDependency

    @Serializable
    data class Or(
        val conditions: List<PluginConfigDependency>
    ) : PluginConfigDependency

    @Serializable
    data class Not(
        val condition: PluginConfigDependency
    ) : PluginConfigDependency
}

@Serializable
data class PluginMetadataField(
    val key: String,
    val title: String,
    val summary: String = "",
    val group: String = "extended",
    val type: PluginMetadataFieldType = PluginMetadataFieldType.TEXT,
    val writeable: Boolean = true,
    val internal: Boolean = false,
    val defaultTarget: PluginMetadataFieldTarget = PluginMetadataFieldTarget.COMMENT,
    val defaultMode: PluginMetadataWriteMode = PluginMetadataWriteMode.DISABLED,
    val targetOptions: List<PluginMetadataFieldTarget> = emptyList()
)

@Serializable
enum class PluginMetadataFieldType {
    @SerialName("text")
    TEXT,
    @SerialName("number")
    NUMBER,
    @SerialName("date")
    DATE,
    @SerialName("lyrics")
    LYRICS,
    @SerialName("cover")
    COVER,
    @SerialName("binary")
    BINARY,
    @SerialName("url")
    URL
}

@Serializable
enum class PluginMetadataWriteMode {
    DISABLED,
    SUPPLEMENT,
    OVERWRITE
}

@Serializable
enum class PluginMetadataFieldTarget {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    GENRE,
    DATE,
    TRACK_NUMBER,
    DISC_NUMBER,
    COMPOSER,
    LYRICIST,
    COMMENT,
    SUBTITLE,
    LYRICS,
    COVER,
    CUSTOM
}
