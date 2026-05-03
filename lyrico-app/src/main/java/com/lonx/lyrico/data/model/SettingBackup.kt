package com.lonx.lyrico.data.model


import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SettingsBackup(
    @SerialName("remove_empty_lines") val removeEmptyLines: Boolean? = null,
    @SerialName("lyric_format") val lyricFormat: String? = null,
    @SerialName("sort_by") val sortBy: String? = null,
    @SerialName("sort_order") val sortOrder: String? = null,
    @SerialName("separator") val separator: String? = null,
    @SerialName("roma_enabled") val romaEnabled: Boolean? = null,
    @SerialName("check_update_enabled") val checkUpdateEnabled: Boolean? = null,
    @SerialName("translation_enabled") val translationEnabled: Boolean? = null,
    @SerialName("ignore_short_audio") val ignoreShortAudio: Boolean? = null,
    @SerialName("search_source_order") val searchSourceOrder: List<String>? = null,
    @SerialName("enabled_search_sources") val enabledSearchSources: List<String>? = null,
    @SerialName("lyrics_source_config") val lyricsSourceConfig: SourceUsageConfig? = null,
    @SerialName("cover_source_config") val coverSourceConfig: SourceUsageConfig? = null,
    @SerialName("metadata_source_config") val metadataSourceConfig: SourceUsageConfig? = null,
    @SerialName("search_page_size") val searchPageSize: Int? = null,
    @SerialName("theme_mode") val themeMode: String? = null,
    @SerialName("only_translation_if_available") val onlyTranslationIfAvailable: Boolean? = null,
    @SerialName("show_scroll_top_button") val showScrollTopButton: Boolean? = null,
    @SerialName("limit_lyrics_input_lines") val limitLyricsInputLines: Boolean? = null,
    @SerialName("character_mapping_config") val characterMappingConfig: CharacterMappingConfig? = null,
    @SerialName("batch_match_config") val batchMatchConfig: BatchMatchConfig? = null,
    @SerialName("extra_metadata_write_rules") val extraMetadataWriteRules: List<ExtraMetadataWriteRule>? = null,
    @SerialName("rename_format") val renameFormat: String? = null,
    @SerialName("conversion_mode") val conversionMode: String? = null,
    @SerialName("key_theme_color") val keyThemeColor: Int? = null,
    @SerialName("monet_enable") val monetEnable: Boolean? = null
)
