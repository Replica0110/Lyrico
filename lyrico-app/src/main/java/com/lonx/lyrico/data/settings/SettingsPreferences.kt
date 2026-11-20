package com.lonx.lyrico.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lonx.lyrico.data.lyrics.LyricLineTrack
import com.lonx.lyrico.data.lyrics.normalizedLyricLineOrder
import com.lonx.lyrico.data.plugin.SourceSettingsStore
import kotlinx.serialization.json.Json

internal object SettingsPreferenceKeys {
    val RENAME_FORMAT = stringPreferencesKey("rename_format")
    val REMOVE_EMPTY_LINES = booleanPreferencesKey("remove_empty_lines")
    val LYRICS_TAG_LINE_KEYWORDS = stringPreferencesKey("lyrics_tag_line_keywords")
    val LIMIT_LYRICS_INPUT_LINES = booleanPreferencesKey("limit_lyrics_input_lines")
    val LYRIC_FORMAT = stringPreferencesKey("lyric_display_mode")
    val LAST_SCAN_TIME = longPreferencesKey("last_scan_time")
    val SORT_BY = stringPreferencesKey("sort_by")
    val SORT_ORDER = stringPreferencesKey("sort_order")
    val ALBUM_SORT_BY = stringPreferencesKey("album_sort_by")
    val ALBUM_SORT_ORDER = stringPreferencesKey("album_sort_order")
    val ARTIST_SORT_BY = stringPreferencesKey("artist_sort_by")
    val ARTIST_SORT_ORDER = stringPreferencesKey("artist_sort_order")
    val ALBUM_GRID_COLUMNS = intPreferencesKey("album_grid_columns")
    val SEPARATOR = stringPreferencesKey("separator")
    val LYRIC_LINE_ORDER = stringPreferencesKey("lyric_line_order")
    val ROMA_ENABLED = booleanPreferencesKey("roma_enabled")
    val CHECK_UPDATE_ENABLED = booleanPreferencesKey("check_update_enabled")
    val TRANSLATION_ENABLED = booleanPreferencesKey("translation_enabled")
    val LYRIC_INDEX_ENABLED = booleanPreferencesKey("lyric_index_enabled")
    val IGNORE_SHORT_AUDIO = booleanPreferencesKey("ignore_short_audio")
    val DEFAULT_PLAYER_COMPONENT = stringPreferencesKey("default_player_component")
    val SEARCH_SOURCE_ORDER = stringPreferencesKey("search_source_order")
    val ENABLED_SEARCH_SOURCES = stringPreferencesKey("enabled_search_sources")
    val SEARCH_PAGE_SIZE = intPreferencesKey("search_page_size")
    val SEARCH_SOURCE_TAB_STYLE = stringPreferencesKey("search_source_tab_style")
    val SHOW_ALL_SEARCH_RESULT_FIELDS = booleanPreferencesKey("show_all_search_result_fields")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val UI_ENGINE = stringPreferencesKey("ui_engine")
    val MONET_ENABLE = booleanPreferencesKey("monet_enable")
    val FLOATING_BOTTOM_BAR_ENABLED = booleanPreferencesKey("floating_bottom_bar_enabled")
    val BAR_BLUR_ENABLED = booleanPreferencesKey("bar_blur_enabled")
    val FLOATING_BAR_EFFECT = stringPreferencesKey("floating_bar_effect")

    val KEY_THEME_COLOR = intPreferencesKey("theme_color_argb")
    val ONLY_TRANSLATION_IF_AVAILABLE = booleanPreferencesKey("only_translation_if_available")
    val CHARACTER_MAPPING_CONFIG = stringPreferencesKey("character_mapping_config")
    val BATCH_MATCH_CONFIG = stringPreferencesKey("batch_match_config")
    val METADATA_FIELD_WRITE_RULES = stringPreferencesKey("metadata_field_write_rules")
    val SOURCE_SETTINGS = stringPreferencesKey("source_settings")
    val CONVERSION_MODE = stringPreferencesKey("conversion_mode")
    val LOG_RETENTION_OPTION = stringPreferencesKey("log_retention_option")
    val ARTIST_SPLIT_CONFIG = stringPreferencesKey("artist_split_config")
    val LIBRARY_INDEX_VERSION = intPreferencesKey("library_index_version")
    val REPLAY_GAIN_TARGET_LOUDNESS = doublePreferencesKey("replay_gain_target_loudness")
    val REPLAY_GAIN_PEAK_MODE = stringPreferencesKey("replay_gain_peak_mode")
}

internal fun Preferences.resolveBarBlurEnabled(): Boolean =
    this[SettingsPreferenceKeys.BAR_BLUR_ENABLED]
        ?: SettingsDefaults.BAR_BLUR_ENABLED

internal fun Preferences.resolveFloatingBarEffect(): FloatingBarEffect =
    this[SettingsPreferenceKeys.FLOATING_BAR_EFFECT]
        ?.let(FloatingBarEffect::fromName)
        ?: SettingsDefaults.FLOATING_BAR_EFFECT

internal fun Json.decodeSourceSettingsStore(raw: String?): SourceSettingsStore {
    return raw
        ?.takeIf { it.isNotBlank() }
        ?.let {
            runCatching {
                decodeFromString<SourceSettingsStore>(it)
            }.getOrNull()
        }
        ?: SourceSettingsStore()
}

internal fun Json.decodeLyricsTagLineKeywords(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return SettingsDefaults.LYRICS_TAG_LINE_KEYWORDS
    return runCatching {
        normalizeLyricsTagLineKeywords(decodeFromString<List<String>>(raw))
    }.getOrElse {
        normalizeLyricsTagLineKeywords(raw.lines()).ifEmpty {
            SettingsDefaults.LYRICS_TAG_LINE_KEYWORDS
        }
    }
}

internal fun normalizeLyricsTagLineKeywords(keywords: List<String>): List<String> {
    return keywords
        .flatMap { it.split('\n', ',', '，', ';', '；') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

internal fun decodeLyricLineOrder(raw: String?): List<LyricLineTrack> {
    if (raw.isNullOrBlank()) return SettingsDefaults.LYRIC_LINE_ORDER
    return raw
        .split(',', '\n', ';')
        .mapNotNull { name ->
            runCatching { LyricLineTrack.valueOf(name.trim()) }.getOrNull()
        }
        .normalizedLyricLineOrder()
}

internal fun String?.csvToIds(): List<String> {
    return this
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        .orEmpty()
}

internal fun Iterable<String>.idsToCsv(): String {
    return map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(",")
}
