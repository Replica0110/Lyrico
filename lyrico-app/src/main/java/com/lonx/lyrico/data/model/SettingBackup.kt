package com.lonx.lyrico.data.model


import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldWriteRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SettingsBackup(
    @SerialName("remove_empty_lines") val removeEmptyLines: Boolean? = null,
    @SerialName("lyrics_tag_line_keywords") val lyricsTagLineKeywords: List<String>? = null,
    @SerialName("lyric_format") val lyricFormat: String? = null,
    @SerialName("sort_by") val sortBy: String? = null,
    @SerialName("sort_order") val sortOrder: String? = null,
    @SerialName("album_sort_by") val albumSortBy: String? = null,
    @SerialName("album_sort_order") val albumSortOrder: String? = null,
    @SerialName("artist_sort_by") val artistSortBy: String? = null,
    @SerialName("artist_sort_order") val artistSortOrder: String? = null,
    @SerialName("album_grid_columns") val albumGridColumns: Int? = null,
    @SerialName("separator") val separator: String? = null,
    @SerialName("roma_enabled") val romaEnabled: Boolean? = null,
    @SerialName("lyric_line_order") val lyricLineOrder: List<String>? = null,
    @SerialName("check_update_enabled") val checkUpdateEnabled: Boolean? = null,
    @SerialName("translation_enabled") val translationEnabled: Boolean? = null,
    @SerialName("ignore_short_audio") val ignoreShortAudio: Boolean? = null,
    @SerialName("search_source_order") val searchSourceOrder: List<String>? = null,
    @SerialName("enabled_search_sources") val enabledSearchSources: List<String>? = null,
    @SerialName("search_page_size") val searchPageSize: Int? = null,
    @SerialName("search_source_tab_style") val searchSourceTabStyle: String? = null,
    @SerialName("show_all_search_result_fields") val showAllSearchResultFields: Boolean? = null,
    @SerialName("theme_mode") val themeMode: String? = null,
    @SerialName("only_translation_if_available") val onlyTranslationIfAvailable: Boolean? = null,
    @SerialName("prefer_ttml_lyrics_tag") val preferTtmlLyricsTag: Boolean? = null,
    @SerialName("limit_lyrics_input_lines") val limitLyricsInputLines: Boolean? = null,
    @SerialName("character_mapping_config") val characterMappingConfig: CharacterMappingConfig? = null,
    @SerialName("batch_match_config") val batchMatchConfig: BatchMatchConfig? = null,
    @SerialName("metadata_field_write_rules") val metadataFieldWriteRules: List<PluginMetadataFieldWriteRule>? = null,
    @SerialName("source_settings") val sourceSettings: SourceSettingsStore? = null,
    @SerialName("rename_format") val renameFormat: String? = null,
    @SerialName("conversion_mode") val conversionMode: String? = null,
    @SerialName("log_retention_option") val logRetentionOption: String? = null,
    @SerialName("key_theme_color") val keyThemeColor: Int? = null,
    @SerialName("monet_enable") val monetEnable: Boolean? = null,
    @SerialName("artist_split_config") val artistSplitConfig: ArtistSplitConfig? = null,
    @SerialName("edit_field_visibility_overrides") val editFieldVisibilityOverrides: Map<String, Boolean>? = null
)
