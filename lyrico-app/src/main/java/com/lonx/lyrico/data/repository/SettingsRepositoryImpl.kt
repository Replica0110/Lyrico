package com.lonx.lyrico.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lonx.lyrico.data.editfield.EditFieldVisibilityOverridesJson
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchConfigDefaults
import com.lonx.lyrico.data.model.BatchMatchField
import com.lonx.lyrico.data.model.BatchMatchMode
import com.lonx.lyrico.data.model.CharacterMappingConfig
import com.lonx.lyrico.data.model.CharacterMappingDefaults
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.model.LogRetentionOption
import com.lonx.lyrico.data.model.MetadataFieldWriteRule
import com.lonx.lyrico.data.model.SearchConfig
import com.lonx.lyrico.data.model.SettingsBackup
import com.lonx.lyrico.data.model.SourceSettingsStore
import com.lonx.lyrico.data.model.ThemeConfig
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.model.AlbumSortBy
import com.lonx.lyrico.data.model.AlbumSortInfo
import com.lonx.lyrico.data.model.ArtistSortBy
import com.lonx.lyrico.data.model.ArtistSortInfo
import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.ui.theme.KeyColor
import com.lonx.lyrico.ui.theme.KeyColors
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.first


internal val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsDefaults {
    const val MONET_ENABLE: Boolean = false
    val KEY_THEME_COLOR = null
    val CONVERSION_MODE = ConversionMode.NONE
    const val RENAME_FORMAT = "@1 - @2"
    const val SHOW_SCROLL_TOP_BUTTON = true
    val LYRIC_FORMAT = LyricFormat.VERBATIM_LRC
    val SORT_BY = SortBy.TITLE
    val SORT_ORDER = SortOrder.ASC
    val ALBUM_SORT_BY = AlbumSortBy.NAME
    val ALBUM_SORT_ORDER = SortOrder.ASC
    val ARTIST_SORT_BY = ArtistSortBy.NAME
    val ARTIST_SORT_ORDER = SortOrder.ASC
    const val ALBUM_GRID_COLUMNS = 2
    const val SEPARATOR = "/"
    const val ROMA_ENABLED = true
    const val TRANSLATION_ENABLED = true
    const val CHECK_UPDATE_ENABLED = true
    const val IGNORE_SHORT_AUDIO = true
    const val ONLY_TRANSLATION_IF_AVAILABLE = false
    const val REMOVE_EMPTY_LINES = true
    const val LIMIT_LYRICS_INPUT_LINES = false
    val LOG_RETENTION_OPTION = LogRetentionOption.THIRTY_DAYS

    val SEARCH_SOURCE_ORDER = emptyList<String>()
    val DEFAULT_ENABLED_SEARCH_SOURCES = emptySet<String>()
    const val SEARCH_PAGE_SIZE = 10

    val THEME_MODE = ThemeMode.AUTO
}

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {
    private val jsonFormatter = Json {
        ignoreUnknownKeys = true // 允许 JSON 中包含当前版本未知的字段
        prettyPrint = true       // 导出的 JSON 格式化，易于阅读
        encodeDefaults = true    // 即使是默认值也编码
    }

    private object PreferencesKeys {
        val RENAME_FORMAT = stringPreferencesKey("rename_format")
        val REMOVE_EMPTY_LINES = booleanPreferencesKey("remove_empty_lines")
        val LIMIT_LYRICS_INPUT_LINES = booleanPreferencesKey("limit_lyrics_input_lines")
        val SHOW_SCROLL_TOP_BUTTON = booleanPreferencesKey("show_scroll_top_button")
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
        val ROMA_ENABLED = booleanPreferencesKey("roma_enabled")
        val CHECK_UPDATE_ENABLED = booleanPreferencesKey("check_update_enabled")
        val TRANSLATION_ENABLED = booleanPreferencesKey("translation_enabled")
        val IGNORE_SHORT_AUDIO = booleanPreferencesKey("ignore_short_audio")
        val SEARCH_SOURCE_ORDER = stringPreferencesKey("search_source_order")
        val ENABLED_SEARCH_SOURCES = stringPreferencesKey("enabled_search_sources")
        val SEARCH_PAGE_SIZE = intPreferencesKey("search_page_size")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MONET_ENABLE = booleanPreferencesKey("monet_enable")
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
    }

    override val lyricFormat: Flow<LyricFormat>
        get() = context.settingsDataStore.data.map { preferences ->
            try {
                LyricFormat.valueOf(
                    preferences[PreferencesKeys.LYRIC_FORMAT] ?: SettingsDefaults.LYRIC_FORMAT.name
                )
            } catch (e: Exception) {
                SettingsDefaults.LYRIC_FORMAT
            }
        }

    override val sortInfo: Flow<SortInfo>
        get() = context.settingsDataStore.data.map { preferences ->
            val sortBy = try {
                SortBy.valueOf(
                    preferences[PreferencesKeys.SORT_BY] ?: SettingsDefaults.SORT_BY.name
                )
            } catch (e: Exception) {
                SettingsDefaults.SORT_BY
            }

            val sortOrder = try {
                SortOrder.valueOf(
                    preferences[PreferencesKeys.SORT_ORDER] ?: SettingsDefaults.SORT_ORDER.name
                )
            } catch (e: Exception) {
                SettingsDefaults.SORT_ORDER
            }

            SortInfo(sortBy, sortOrder)
        }

    override val albumSortInfo: Flow<AlbumSortInfo>
        get() = context.settingsDataStore.data.map { preferences ->
            val sortBy = runCatching {
                AlbumSortBy.valueOf(
                    preferences[PreferencesKeys.ALBUM_SORT_BY]
                        ?: SettingsDefaults.ALBUM_SORT_BY.name
                )
            }.getOrDefault(SettingsDefaults.ALBUM_SORT_BY)

            val sortOrder = runCatching {
                SortOrder.valueOf(
                    preferences[PreferencesKeys.ALBUM_SORT_ORDER]
                        ?: SettingsDefaults.ALBUM_SORT_ORDER.name
                )
            }.getOrDefault(SettingsDefaults.ALBUM_SORT_ORDER)

            AlbumSortInfo(sortBy, sortOrder)
        }

    override val artistSortInfo: Flow<ArtistSortInfo>
        get() = context.settingsDataStore.data.map { preferences ->
            val sortBy = runCatching {
                ArtistSortBy.valueOf(
                    preferences[PreferencesKeys.ARTIST_SORT_BY]
                        ?: SettingsDefaults.ARTIST_SORT_BY.name
                )
            }.getOrDefault(SettingsDefaults.ARTIST_SORT_BY)

            val sortOrder = runCatching {
                SortOrder.valueOf(
                    preferences[PreferencesKeys.ARTIST_SORT_ORDER]
                        ?: SettingsDefaults.ARTIST_SORT_ORDER.name
                )
            }.getOrDefault(SettingsDefaults.ARTIST_SORT_ORDER)

            ArtistSortInfo(sortBy, sortOrder)
        }

    override val albumGridColumns: Flow<Int>
        get() = context.settingsDataStore.data.map { preferences ->
            val columns = preferences[PreferencesKeys.ALBUM_GRID_COLUMNS]
                ?: SettingsDefaults.ALBUM_GRID_COLUMNS
            columns.coerceIn(2, 4)
        }

    override val separator: Flow<String>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.SEPARATOR] ?: SettingsDefaults.SEPARATOR
        }

    override val romaEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.ROMA_ENABLED] ?: SettingsDefaults.ROMA_ENABLED
        }

    override val translationEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.TRANSLATION_ENABLED] ?: SettingsDefaults.TRANSLATION_ENABLED
        }

    override val checkUpdateEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.CHECK_UPDATE_ENABLED]
                ?: SettingsDefaults.CHECK_UPDATE_ENABLED
        }

    override val ignoreShortAudio: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.IGNORE_SHORT_AUDIO] ?: SettingsDefaults.IGNORE_SHORT_AUDIO
        }

    override val searchSourceOrder: Flow<List<String>>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.SEARCH_SOURCE_ORDER].csvToIds()
        }

    override val enabledSearchSources: Flow<Set<String>>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.ENABLED_SEARCH_SOURCES].csvToIds().toSet()
        }

    override val searchPageSize: Flow<Int>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.SEARCH_PAGE_SIZE] ?: SettingsDefaults.SEARCH_PAGE_SIZE
        }

    override val themeMode: Flow<ThemeMode>
        get() = context.settingsDataStore.data.map { preferences ->
            val modeName = preferences[PreferencesKeys.THEME_MODE]
            if (modeName.isNullOrBlank()) {
                SettingsDefaults.THEME_MODE
            } else {
                try {
                    ThemeMode.valueOf(modeName)
                } catch (e: IllegalArgumentException) {
                    SettingsDefaults.THEME_MODE
                }
            }
        }
    override val keyColor: Flow<KeyColor>
        get() = context.settingsDataStore.data.map { preferences ->
            // 检查 DataStore 中是否有保存颜色的 Key
            if (preferences.contains(PreferencesKeys.KEY_THEME_COLOR)) {
                val savedColorInt = preferences[PreferencesKeys.KEY_THEME_COLOR]!!
                val savedColor = Color(savedColorInt)
                // 查找对应的颜色，找不到则返回默认项(第一个)
                KeyColors.find { it.color == savedColor } ?: KeyColors.first()
            } else {
                // 如果没有保存过，说明是“系统默认”
                KeyColors.first()
            }
        }
    override val monetEnable: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.MONET_ENABLE] ?: false
        }
    override val conversionMode: Flow<ConversionMode>
        get() = context.settingsDataStore.data.map { preferences ->
            val modeName = preferences[PreferencesKeys.CONVERSION_MODE]
            if (modeName.isNullOrBlank()) {
                ConversionMode.NONE
            } else {
                try {
                    ConversionMode.valueOf(modeName)
                } catch (e: IllegalArgumentException) {
                    ConversionMode.NONE
                }
            }
        }

    override val onlyTranslationIfAvailable: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.ONLY_TRANSLATION_IF_AVAILABLE]
                ?: SettingsDefaults.ONLY_TRANSLATION_IF_AVAILABLE
        }

    override val removeEmptyLines: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.REMOVE_EMPTY_LINES] ?: SettingsDefaults.REMOVE_EMPTY_LINES
        }
    override val limitLyricsInputLines: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.LIMIT_LYRICS_INPUT_LINES]
                ?: SettingsDefaults.LIMIT_LYRICS_INPUT_LINES
        }
    override val logRetentionOption: Flow<LogRetentionOption>
        get() = context.settingsDataStore.data.map { preferences ->
            val optionName = preferences[PreferencesKeys.LOG_RETENTION_OPTION]
            if (optionName.isNullOrBlank()) {
                SettingsDefaults.LOG_RETENTION_OPTION
            } else {
                runCatching { LogRetentionOption.valueOf(optionName) }
                    .getOrDefault(SettingsDefaults.LOG_RETENTION_OPTION)
            }
        }
    override val showScrollTopButton: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.SHOW_SCROLL_TOP_BUTTON] ?: SettingsDefaults.SHOW_SCROLL_TOP_BUTTON
        }

    override val renameFormat: Flow<String>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.RENAME_FORMAT] ?: SettingsDefaults.RENAME_FORMAT
        }
    override suspend fun getLastScanTime(): Long {
        return context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIME] ?: 0L
        }.first()
    }


    private val baseConfigFlow =
        combine(
            lyricFormat,
            romaEnabled,
            translationEnabled
        ) { format, roma, translation ->
            Triple(format, roma, translation)
        }

    private val extraConfigFlow =
        combine(
            onlyTranslationIfAvailable,
            removeEmptyLines,
            conversionMode
        ) { onlyTranslation, removeEmptyLines, conversionMode ->
            Triple(onlyTranslation, removeEmptyLines, conversionMode)
        }

    override val lyricRenderConfigFlow =
        combine(baseConfigFlow, extraConfigFlow) { base, extra ->
            LyricRenderConfig(
                format = base.first,
                showRomanization = base.second,
                showTranslation = base.third,
                onlyTranslationIfAvailable = extra.first,
                removeEmptyLines = extra.second,
                conversionMode = extra.third
            )
        }

    override val searchConfigFlow: Flow<SearchConfig> =
        combine(separator, searchSourceOrder, enabledSearchSources, searchPageSize) { sep, order, enabled, size ->
            SearchConfig(
                separator = sep,
                searchSourceOrder = order,
                enabledSearchSources = enabled,
                searchPageSize = size
            )
        }

    override val themeConfigFlow: Flow<ThemeConfig> =
        combine(themeMode, monetEnable, keyColor) { theme, monet, keyColor ->
            ThemeConfig(
                themeMode = theme,
                monetEnable = monet,
                keyColor = keyColor
            )
        }

    override suspend fun saveOnlyTranslationIfAvailable(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.ONLY_TRANSLATION_IF_AVAILABLE] = enabled
        }
    }

    override suspend fun saveLyricDisplayMode(mode: LyricFormat) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.LYRIC_FORMAT] = mode.name
        }
    }

    override suspend fun saveSortInfo(sortInfo: SortInfo) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_BY] = sortInfo.sortBy.name
            preferences[PreferencesKeys.SORT_ORDER] = sortInfo.order.name
        }
    }

    override val sourceSettingsByIdFlow: Flow<Map<String, SourceRuntimeConfig>>
        get() = context.settingsDataStore.data.map { preferences ->
            decodeSourceSettingsStore(preferences[PreferencesKeys.SOURCE_SETTINGS])
                .values
                .mapKeys { (sourceId, _) -> sourceId.toStableSourceId() }
                .mapValues { (_, values) -> SourceRuntimeConfig(values) }
        }

    override suspend fun saveAlbumSortInfo(sortInfo: AlbumSortInfo) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.ALBUM_SORT_BY] = sortInfo.sortBy.name
            preferences[PreferencesKeys.ALBUM_SORT_ORDER] = sortInfo.order.name
        }
    }

    override suspend fun saveArtistSortInfo(sortInfo: ArtistSortInfo) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.ARTIST_SORT_BY] = sortInfo.sortBy.name
            preferences[PreferencesKeys.ARTIST_SORT_ORDER] = sortInfo.order.name
        }
    }

    override suspend fun saveAlbumGridColumns(columns: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.ALBUM_GRID_COLUMNS] = columns.coerceIn(2, 4)
        }
    }

    override suspend fun saveSeparator(separator: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SEPARATOR] = separator
        }
    }

    override suspend fun saveRomaEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.ROMA_ENABLED] = enabled
        }
    }

    override suspend fun saveCheckUpdateEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.CHECK_UPDATE_ENABLED] = enabled
        }
    }

    override suspend fun saveTranslationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.TRANSLATION_ENABLED] = enabled
        }
    }

    override suspend fun saveIgnoreShortAudio(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.IGNORE_SHORT_AUDIO] = enabled
        }
    }

    override suspend fun saveLastScanTime(time: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIME] = time
        }
    }

    override suspend fun saveSearchSourceOrder(sources: List<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SEARCH_SOURCE_ORDER] =
                sources.idsToCsv()
        }
    }

    override suspend fun saveEnabledSearchSources(sources: Set<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLED_SEARCH_SOURCES] = sources.idsToCsv()
        }
    }

    override suspend fun saveSearchPageSize(size: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SEARCH_PAGE_SIZE] = size
        }
    }

    override suspend fun saveConversionMode(mode: ConversionMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.CONVERSION_MODE] = mode.name
        }
    }

    override suspend fun saveThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }
    override suspend fun saveMonetEnable(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.MONET_ENABLE] = enabled
        }
    }

    override suspend fun saveKeyColor(selectedKeyColor: KeyColor) {
        context.settingsDataStore.edit { preferences ->
            if (selectedKeyColor.color == null) {
                // 如果选了“系统默认”，直接移除保存的值
                preferences.remove(PreferencesKeys.KEY_THEME_COLOR)
            } else {
                // 否则保存颜色具体的 ARGB 值
                preferences[PreferencesKeys.KEY_THEME_COLOR] = selectedKeyColor.color.toArgb()
            }
        }
    }
    override suspend fun saveRemoveEmptyLines(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.REMOVE_EMPTY_LINES] = enabled
        }
    }

    override suspend fun saveLimitLyricsInputLines(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.LIMIT_LYRICS_INPUT_LINES] = enabled
        }
    }

    override suspend fun saveLogRetentionOption(option: LogRetentionOption) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.LOG_RETENTION_OPTION] = option.name
        }
    }

    override suspend fun saveShowScrollTopButton(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_SCROLL_TOP_BUTTON] = enabled
        }
    }

    override suspend fun getLyricRenderConfig(): LyricRenderConfig {
        val prefs = context.settingsDataStore.data.first()

        val format = LyricFormat.valueOf(
            prefs[PreferencesKeys.LYRIC_FORMAT]
                ?: SettingsDefaults.LYRIC_FORMAT.name
        )

        val roma = prefs[PreferencesKeys.ROMA_ENABLED] ?: SettingsDefaults.ROMA_ENABLED

        val showTranslation = prefs[PreferencesKeys.TRANSLATION_ENABLED] ?: SettingsDefaults.TRANSLATION_ENABLED

        val removeEmptyLines = prefs[PreferencesKeys.REMOVE_EMPTY_LINES] ?: SettingsDefaults.REMOVE_EMPTY_LINES
        val onlyTranslationIfAvailable = prefs[PreferencesKeys.ONLY_TRANSLATION_IF_AVAILABLE] ?: SettingsDefaults.ONLY_TRANSLATION_IF_AVAILABLE
        val conversionMode = ConversionMode.valueOf(
            prefs[PreferencesKeys.CONVERSION_MODE]
                ?: SettingsDefaults.CONVERSION_MODE.name
        )
        return LyricRenderConfig(
            format = format,
            showRomanization = roma,
            removeEmptyLines = removeEmptyLines,
            showTranslation = showTranslation,
            onlyTranslationIfAvailable = onlyTranslationIfAvailable,
            conversionMode = conversionMode
        )
    }

    override suspend fun exportSettings(): String {
        val prefs = context.settingsDataStore.data.first()
        val charMapping = getCharacterMappingConfig()
        val batchMatchConfig = getBatchMatchConfig()
        val artistSplitConfig = getArtistSplitConfig()
        val backup = SettingsBackup(
            removeEmptyLines = prefs[PreferencesKeys.REMOVE_EMPTY_LINES]
                ?: SettingsDefaults.REMOVE_EMPTY_LINES,

            lyricFormat = prefs[PreferencesKeys.LYRIC_FORMAT]
                ?: SettingsDefaults.LYRIC_FORMAT.name,

            sortBy = prefs[PreferencesKeys.SORT_BY]
                ?: SettingsDefaults.SORT_BY.name,

            sortOrder = prefs[PreferencesKeys.SORT_ORDER]
                ?: SettingsDefaults.SORT_ORDER.name,

            albumSortBy = prefs[PreferencesKeys.ALBUM_SORT_BY]
                ?: SettingsDefaults.ALBUM_SORT_BY.name,

            albumSortOrder = prefs[PreferencesKeys.ALBUM_SORT_ORDER]
                ?: SettingsDefaults.ALBUM_SORT_ORDER.name,

            artistSortBy = prefs[PreferencesKeys.ARTIST_SORT_BY]
                ?: SettingsDefaults.ARTIST_SORT_BY.name,

            artistSortOrder = prefs[PreferencesKeys.ARTIST_SORT_ORDER]
                ?: SettingsDefaults.ARTIST_SORT_ORDER.name,

            albumGridColumns = prefs[PreferencesKeys.ALBUM_GRID_COLUMNS]
                ?: SettingsDefaults.ALBUM_GRID_COLUMNS,

            separator = prefs[PreferencesKeys.SEPARATOR]
                ?: SettingsDefaults.SEPARATOR,

            romaEnabled = prefs[PreferencesKeys.ROMA_ENABLED]
                ?: SettingsDefaults.ROMA_ENABLED,

            checkUpdateEnabled = prefs[PreferencesKeys.CHECK_UPDATE_ENABLED]
                ?: SettingsDefaults.CHECK_UPDATE_ENABLED,

            translationEnabled = prefs[PreferencesKeys.TRANSLATION_ENABLED]
                ?: SettingsDefaults.TRANSLATION_ENABLED,

            ignoreShortAudio = prefs[PreferencesKeys.IGNORE_SHORT_AUDIO]
                ?: SettingsDefaults.IGNORE_SHORT_AUDIO,

            searchSourceOrder = (prefs[PreferencesKeys.SEARCH_SOURCE_ORDER] ?: SettingsDefaults.SEARCH_SOURCE_ORDER.idsToCsv()).csvToIds(),

            enabledSearchSources = (prefs[PreferencesKeys.ENABLED_SEARCH_SOURCES] ?: SettingsDefaults.SEARCH_SOURCE_ORDER.idsToCsv()).csvToIds(),

            searchPageSize = prefs[PreferencesKeys.SEARCH_PAGE_SIZE]
                ?: SettingsDefaults.SEARCH_PAGE_SIZE,

            themeMode = prefs[PreferencesKeys.THEME_MODE]
                ?: SettingsDefaults.THEME_MODE.name,
            monetEnable = prefs[PreferencesKeys.MONET_ENABLE]
                ?: SettingsDefaults.MONET_ENABLE,
            keyThemeColor = prefs[PreferencesKeys.KEY_THEME_COLOR] ?: SettingsDefaults.KEY_THEME_COLOR,

            onlyTranslationIfAvailable = prefs[PreferencesKeys.ONLY_TRANSLATION_IF_AVAILABLE]
                ?: SettingsDefaults.ONLY_TRANSLATION_IF_AVAILABLE,

            showScrollTopButton = prefs[PreferencesKeys.SHOW_SCROLL_TOP_BUTTON]
                ?: SettingsDefaults.SHOW_SCROLL_TOP_BUTTON,
            limitLyricsInputLines = prefs[PreferencesKeys.LIMIT_LYRICS_INPUT_LINES]
                ?: SettingsDefaults.LIMIT_LYRICS_INPUT_LINES,
            characterMappingConfig = charMapping,
            metadataFieldWriteRules = getMetadataFieldWriteRules(),
            sourceSettings = decodeSourceSettingsStore(prefs[PreferencesKeys.SOURCE_SETTINGS]),
            renameFormat = prefs[PreferencesKeys.RENAME_FORMAT]
                ?: SettingsDefaults.RENAME_FORMAT,
            batchMatchConfig = batchMatchConfig,
            conversionMode = prefs[PreferencesKeys.CONVERSION_MODE]
                ?: SettingsDefaults.CONVERSION_MODE.name,
            logRetentionOption = prefs[PreferencesKeys.LOG_RETENTION_OPTION]
                ?: SettingsDefaults.LOG_RETENTION_OPTION.name,
            artistSplitConfig = artistSplitConfig,
            editFieldVisibilityOverrides = runCatching {
                prefs[com.lonx.lyrico.data.editfield.EditFieldVisibilityRepository.EDIT_FIELD_VISIBILITY_OVERRIDES]
                    ?.let { json ->
                        jsonFormatter.decodeFromString<EditFieldVisibilityOverridesJson>(json).values
                    }
            }.getOrNull()
        )

        return jsonFormatter.encodeToString(backup)
    }


    override suspend fun importSettings(jsonString: String): Boolean {
        return try {
            val backup = jsonFormatter.decodeFromString<SettingsBackup>(jsonString)

            context.settingsDataStore.edit { prefs ->
                backup.removeEmptyLines?.let { prefs[PreferencesKeys.REMOVE_EMPTY_LINES] = it }
                backup.lyricFormat?.let { prefs[PreferencesKeys.LYRIC_FORMAT] = it }
                backup.sortBy?.let { prefs[PreferencesKeys.SORT_BY] = it }
                backup.sortOrder?.let { prefs[PreferencesKeys.SORT_ORDER] = it }
                backup.albumSortBy?.let { prefs[PreferencesKeys.ALBUM_SORT_BY] = it }
                backup.albumSortOrder?.let { prefs[PreferencesKeys.ALBUM_SORT_ORDER] = it }
                backup.artistSortBy?.let { prefs[PreferencesKeys.ARTIST_SORT_BY] = it }
                backup.artistSortOrder?.let { prefs[PreferencesKeys.ARTIST_SORT_ORDER] = it }
                backup.albumGridColumns?.let {
                    prefs[PreferencesKeys.ALBUM_GRID_COLUMNS] = it.coerceIn(2, 4)
                }
                backup.separator?.let { prefs[PreferencesKeys.SEPARATOR] = it }
                backup.romaEnabled?.let { prefs[PreferencesKeys.ROMA_ENABLED] = it }
                backup.checkUpdateEnabled?.let { prefs[PreferencesKeys.CHECK_UPDATE_ENABLED] = it }
                backup.translationEnabled?.let { prefs[PreferencesKeys.TRANSLATION_ENABLED] = it }
                backup.ignoreShortAudio?.let { prefs[PreferencesKeys.IGNORE_SHORT_AUDIO] = it }
                backup.searchSourceOrder?.let { list ->
                    prefs[PreferencesKeys.SEARCH_SOURCE_ORDER] = list.idsToCsv()
                }
                
                // 处理启用的搜索源：如果为null或为空，默认启用所有源
                if (backup.enabledSearchSources.isNullOrEmpty()) {
                    // 默认启用所有源
                    prefs[PreferencesKeys.ENABLED_SEARCH_SOURCES] =
                        SettingsDefaults.DEFAULT_ENABLED_SEARCH_SOURCES.idsToCsv()
                } else {
                    // 使用导入的启用源列表，直接映射而不补齐缺失源
                    prefs[PreferencesKeys.ENABLED_SEARCH_SOURCES] =
                        backup.enabledSearchSources.idsToCsv()
                }
                
                backup.searchPageSize?.let { prefs[PreferencesKeys.SEARCH_PAGE_SIZE] = it }
                backup.themeMode?.let { prefs[PreferencesKeys.THEME_MODE] = it }
                backup.monetEnable?.let { prefs[PreferencesKeys.MONET_ENABLE] = it }
                backup.keyThemeColor?.let { prefs[PreferencesKeys.KEY_THEME_COLOR] = it }
                backup.onlyTranslationIfAvailable?.let {
                    prefs[PreferencesKeys.ONLY_TRANSLATION_IF_AVAILABLE] = it
                }
                backup.showScrollTopButton?.let {
                    prefs[PreferencesKeys.SHOW_SCROLL_TOP_BUTTON] = it
                }
                backup.limitLyricsInputLines?.let {
                    prefs[PreferencesKeys.LIMIT_LYRICS_INPUT_LINES] = it
                }
                backup.characterMappingConfig?.let { config ->
                    prefs[PreferencesKeys.CHARACTER_MAPPING_CONFIG] = jsonFormatter.encodeToString(config)
                }
                backup.metadataFieldWriteRules?.let { rules ->
                    prefs[PreferencesKeys.METADATA_FIELD_WRITE_RULES] =
                        jsonFormatter.encodeToString(rules)
                }
                backup.sourceSettings?.let { store ->
                    prefs[PreferencesKeys.SOURCE_SETTINGS] = jsonFormatter.encodeToString(store)
                }
                backup.renameFormat?.let { prefs[PreferencesKeys.RENAME_FORMAT]  = it }
                backup.batchMatchConfig?.let { config ->
                    prefs[PreferencesKeys.BATCH_MATCH_CONFIG] = jsonFormatter.encodeToString(config)
                }
                backup.conversionMode?.let { prefs[PreferencesKeys.CONVERSION_MODE] = it }
                backup.logRetentionOption?.let { optionName ->
                    runCatching { LogRetentionOption.valueOf(optionName) }
                        .getOrNull()
                        ?.let { prefs[PreferencesKeys.LOG_RETENTION_OPTION] = it.name }
                }
                backup.artistSplitConfig?.let { config ->
                    prefs[PreferencesKeys.ARTIST_SPLIT_CONFIG] = jsonFormatter.encodeToString(config)
                    prefs[PreferencesKeys.LIBRARY_INDEX_VERSION] = 0
                }
                backup.editFieldVisibilityOverrides?.let { overrides ->
                    prefs[com.lonx.lyrico.data.editfield.EditFieldVisibilityRepository.EDIT_FIELD_VISIBILITY_OVERRIDES] =
                        jsonFormatter.encodeToString(
                            EditFieldVisibilityOverridesJson(values = overrides)
                        )
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    override val characterMappingConfig: Flow<CharacterMappingConfig>
        get() = context.settingsDataStore.data.map { preferences ->
            val configJson = preferences[PreferencesKeys.CHARACTER_MAPPING_CONFIG]
            if (configJson.isNullOrBlank()) {
                CharacterMappingConfig(
                    rules = CharacterMappingDefaults.ALL_BUILTIN_RULES
                )
            } else {
                try {
                    jsonFormatter.decodeFromString<CharacterMappingConfig>(configJson)
                } catch (e: Exception) {
                    CharacterMappingConfig(
                        rules = CharacterMappingDefaults.ALL_BUILTIN_RULES
                    )
                }
            }
        }

    override val artistSplitConfigFlow: Flow<ArtistSplitConfig>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.ARTIST_SPLIT_CONFIG]
                ?.let { json ->
                    runCatching {
                        jsonFormatter.decodeFromString<ArtistSplitConfig>(json)
                    }.getOrNull()
                }
                ?: ArtistSplitConfig()
        }

    override val batchMatchConfig: Flow<BatchMatchConfig>
        get() = context.settingsDataStore.data.map { preferences ->
            val configJson = preferences[PreferencesKeys.BATCH_MATCH_CONFIG]
            if (configJson.isNullOrBlank()) {
                BatchMatchConfigDefaults.DEFAULT_CONFIG
            } else {
                decodeBatchMatchConfig(configJson)
            }
        }

    override val metadataFieldWriteRules: Flow<List<MetadataFieldWriteRule>>
        get() = context.settingsDataStore.data.map { preferences ->
            val rulesJson = preferences[PreferencesKeys.METADATA_FIELD_WRITE_RULES]
            if (!rulesJson.isNullOrBlank()) {
                decodeMetadataFieldWriteRules(rulesJson)
            } else {
                emptyList()
            }
        }

    override suspend fun saveCharacterMappingConfig(config: CharacterMappingConfig) {
        context.settingsDataStore.edit { preferences ->
            val configJson = jsonFormatter.encodeToString(config)
            preferences[PreferencesKeys.CHARACTER_MAPPING_CONFIG] = configJson
        }
    }

    override suspend fun saveRenameFormat(format: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.RENAME_FORMAT] = format
        }
    }
    override suspend fun saveBatchMatchConfig(config: BatchMatchConfig) {
        context.settingsDataStore.edit { preferences ->
            val configJson = jsonFormatter.encodeToString(config)
            preferences[PreferencesKeys.BATCH_MATCH_CONFIG] = configJson
        }
    }

    override suspend fun saveMetadataFieldWriteRules(rules: List<MetadataFieldWriteRule>) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.METADATA_FIELD_WRITE_RULES] =
                jsonFormatter.encodeToString(rules)
        }
    }

    override suspend fun saveSourceSettings(sourceId: String, values: Map<String, String>) {
        context.settingsDataStore.edit { preferences ->
            val currentStore = decodeSourceSettingsStore(preferences[PreferencesKeys.SOURCE_SETTINGS])
            val newStore = currentStore.copy(
                values = currentStore.values + (sourceId.toStableSourceId() to values)
            )
            preferences[PreferencesKeys.SOURCE_SETTINGS] = jsonFormatter.encodeToString(newStore)
        }
    }

    override suspend fun updateCharacterMappingInRule(ruleId: String, charMappings: Map<String, String?>) {
        val currentConfig = characterMappingConfig.first()
        val updatedRules = currentConfig.rules.map { rule ->
            if (rule.id == ruleId) {
                rule.copy(charMappings = charMappings)
            } else rule
        }
        saveCharacterMappingConfig(currentConfig.copy(rules = updatedRules))
    }

    override suspend fun getCharacterMappingConfig(): CharacterMappingConfig {
        return characterMappingConfig.first()
    }
    override suspend fun getBatchMatchConfig(): BatchMatchConfig {
        return batchMatchConfig.first()
    }

    override suspend fun getMetadataFieldWriteRules(): List<MetadataFieldWriteRule> {
        return metadataFieldWriteRules.first()
    }

    override suspend fun getSourceSettings(sourceId: String): SourceRuntimeConfig {
        return sourceSettingsByIdFlow.first()[sourceId.toStableSourceId()] ?: SourceRuntimeConfig()
    }

    override suspend fun removePluginSettings(pluginId: String) {
        val stablePluginId = pluginId.toStableSourceId()
        context.settingsDataStore.edit { preferences ->
            val currentStore = decodeSourceSettingsStore(preferences[PreferencesKeys.SOURCE_SETTINGS])
            preferences[PreferencesKeys.SOURCE_SETTINGS] = jsonFormatter.encodeToString(
                currentStore.copy(values = currentStore.values - stablePluginId)
            )
            val rules = decodeMetadataFieldWriteRules(
                preferences[PreferencesKeys.METADATA_FIELD_WRITE_RULES].orEmpty()
            )
            preferences[PreferencesKeys.METADATA_FIELD_WRITE_RULES] =
                jsonFormatter.encodeToString(rules.filterNot { it.pluginId == stablePluginId })
        }
    }

    override suspend fun saveArtistSplitConfig(config: ArtistSplitConfig) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.ARTIST_SPLIT_CONFIG] = jsonFormatter.encodeToString(config)
            preferences[PreferencesKeys.LIBRARY_INDEX_VERSION] = 0
        }
    }

    override suspend fun getArtistSplitConfig(): ArtistSplitConfig {
        return artistSplitConfigFlow.first()
    }

    override suspend fun getLibraryIndexVersion(): Int {
        return context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.LIBRARY_INDEX_VERSION] ?: 0
        }.first()
    }

    override suspend fun saveLibraryIndexVersion(version: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.LIBRARY_INDEX_VERSION] = version
        }
    }

    private fun decodeBatchMatchConfig(configJson: String): BatchMatchConfig {
        return try {
            val root = jsonFormatter.parseToJsonElement(configJson).jsonObject
            val fieldsObject = root["fields"]?.jsonObject
            val fields = fieldsObject?.mapNotNull { (fieldName, modeElement) ->
                val field = BatchMatchField.entries.firstOrNull { it.name == fieldName }
                    ?: return@mapNotNull null
                val mode = modeElement.jsonPrimitive.contentOrNull
                    ?.let { runCatching { BatchMatchMode.valueOf(it) }.getOrNull() }
                    ?: return@mapNotNull null
                field to mode
            }?.toMap()

            BatchMatchConfig(
                fields = fields ?: BatchMatchConfigDefaults.DEFAULT_CONFIG.fields,
                concurrency = root["concurrency"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: BatchMatchConfigDefaults.DEFAULT_CONFIG.concurrency,
                preferFileName = root["preferFileName"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                    ?: BatchMatchConfigDefaults.DEFAULT_CONFIG.preferFileName
            )
        } catch (e: Exception) {
            BatchMatchConfigDefaults.DEFAULT_CONFIG
        }
    }

    private fun decodeMetadataFieldWriteRules(raw: String): List<MetadataFieldWriteRule> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            jsonFormatter.decodeFromString<List<MetadataFieldWriteRule>>(
                raw.replace("\"sourceId\"", "\"pluginId\"")
            )
        }.getOrDefault(emptyList())
    }

    private fun decodeSourceSettingsStore(raw: String?): SourceSettingsStore {
        return raw
            ?.takeIf { it.isNotBlank() }
            ?.let {
                runCatching {
                    jsonFormatter.decodeFromString<SourceSettingsStore>(it)
                }.getOrNull()
            }
            ?: SourceSettingsStore()
    }

    private fun String.toStableSourceId(): String {
        return trim()
    }

    private fun String?.csvToIds(): List<String> {
        return this
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()
    }

    private fun Iterable<String>.idsToCsv(): String {
        return map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")
    }
}
