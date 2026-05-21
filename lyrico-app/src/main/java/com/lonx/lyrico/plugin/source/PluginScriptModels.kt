package com.lonx.lyrico.plugin.source

import kotlinx.serialization.Serializable

@Serializable
data class PluginSearchSongsRequest(
    val keyword: String,
    val page: Int = 1,
    val pageSize: Int = 20,
    val separator: String = "/",
    val config: Map<String, String> = emptyMap()
)

@Serializable
data class PluginSearchCoversRequest(
    val keyword: String,
    val pageSize: Int = 5,
    val config: Map<String, String> = emptyMap()
)

@Serializable
data class PluginGetLyricsRequest(
    val song: PluginSongRequest,
    val config: Map<String, String> = emptyMap()
)

@Serializable
data class PluginSongRequest(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val sourceId: String,
    val pluginId: String,
    val fields: Map<String, String> = emptyMap()
)
