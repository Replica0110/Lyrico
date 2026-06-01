package com.lonx.lyrico.data.model.plugin

import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.MetadataWriteMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PluginMetadataField(
    val key: String,
    val title: String,
    val summary: String = "",
    val group: String = "extended",
    val type: PluginMetadataFieldType = PluginMetadataFieldType.TEXT,
    val writeable: Boolean = true,
    val internal: Boolean = false,
    val defaultTarget: MetadataFieldTarget = MetadataFieldTarget.COMMENT,
    val defaultMode: MetadataWriteMode = MetadataWriteMode.DISABLED,
    val defaultCustomTagKey: String = "",
    val targetOptions: List<MetadataFieldTarget> = emptyList()
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
