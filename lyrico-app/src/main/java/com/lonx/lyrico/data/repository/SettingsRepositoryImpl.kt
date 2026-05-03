package com.lonx.lyrico.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchConfigDefaults
import com.lonx.lyrico.data.model.BatchMatchField
import com.lonx.lyrico.data.model.BatchMatchMode
import com.lonx.lyrico.data.model.CharacterMappingConfig
import com.lonx.lyrico.data.model.CharacterMappingDefaults
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.ExtraMetadataKey
import com.lonx.lyrico.data.model.ExtraMetadataTarget
import com.lonx.lyrico.data.model.ExtraMetadataWriteDefaults
import com.lonx.lyrico.data.model.ExtraMetadataWriteRule
import com.lonx.lyrico.data.model.ExtraWriteMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.model.SearchConfig
import com.lonx.lyrico.data.model.SettingsBackup
import com.lonx.lyrico.data.model.SourceUsage
import com.lonx.lyrico.data.model.SourceUsageConfig
import com.lonx.lyrico.data.model.ThemeConfig
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.ui.theme.KeyColor
import com.lonx.lyrico.ui.theme.KeyColors
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.model.toSourceCsv
import com.lonx.lyrics.model.toSourceList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.first


private val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsDefaults {
    const val MONET_ENABLE: Boolean = false
    val KEY_THEME_COLOR = null
    val CONVERSION_MODE = ConversionMode.NONE
    const val RENAME_FORMAT = "@1 - @2"
    const val SHOW_SCROLL_TOP_BUTTON = true
    val LYRIC_FORMAT = LyricFormat.VERBATIM_LRC
    val SORT_BY = SortBy.TITLE
    val SORT_ORDER = SortOrder.ASC
    const val SEPARATOR = "/"
    const val ROMA_ENABLED = true
    const val TRANSLATION_ENABLED = true
    const val CHECK_UPDATE_ENABLED = true
    const val IGNORE_SHORT_AUDIO = true
    const val ONLY_TRANSLATION_IF_AVAILABLE = false
    const val REMOVE_EMPTY_LINES = true
    const val LIMIT_LYRICS_INPUT_LINES = false

    // 搜索源顺序默认值
    val SEARCH_SOURCE_ORDER = Source.entries.toList()
    // 启用的搜索源默认值（默认启用所有源）
    val DEFAULT_ENABLED_SEARCH_SOURCES = Source.entries.toSet()
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
        val SEPARATOR = stringPreferencesKey("separator")
        val ROMA_ENABLED = booleanPreferencesKey("roma_enabled")
        val CHECK_UPDATE_ENABLED = booleanPreferencesKey("check_update_enabled")
        val TRANSLATION_ENABLED = booleanPreferencesKey("translation_enabled")
        val IGNORE_SHORT_AUDIO = booleanPreferencesKey("ignore_short_audio")
        val SEARCH_SOURCE_ORDER = stringPreferencesKey("search_source_order")
        val ENABLED_SEARCH_SOURCES = stringPreferencesKey("enabled_search_sources")
        val LYRICS_SOURCE_CONFIG = stringPreferencesKey("lyrics_source_config")
        val COVER_SOURCE_CONFIG = stringPreferencesKey("cover_source_config")
        val METADATA_SOURCE_CONFIG = stringPreferencesKey("metadata_source_config")
        val SEARCH_PAGE_SIZE = intPreferencesKey("search_page_size")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MONET_ENABLE = booleanPreferencesKey("monet_enable")
        val KEY_THEME_COLOR = intPreferencesKey("theme_color_argb")
        val ONLY_TRANSLATION_IF_AVAILABLE = booleanPreferencesKey("only_translation_if_available")
        val CHARACTER_MAPPING_CONFIG = stringPreferencesKey("character_mapping_config")
        val BATCH_MATCH_CONFIG = stringPreferencesKey("batch_match_config")
        val EXTRA_METADATA_WRITE_RULES = stringPreferencesKey("extra_metadata_write_rules")
        val CONVERSION_MODE = stringPreferencesKey("conversion_mode")
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

    override val searchSourceOrder: Flow<List<Source>>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.SEARCH_SOURCE_ORDER].toSourceList()
        }

    override val enabledSearchSources: Flow<Set<Source>>
        get() = context.settingsDataStore.data.map { preferences ->
            val enabledCsv = preferences[PreferencesKeys.ENABLED_SEARCH_SOURCES]
            if (enabledCsv.isNullOrBlank()) {
                Source.entries.toSet()
            } else {
                enabledCsv.split(",").mapNotNull { Source.fromNameOrNull(it) }.toSet()
            }
        }

    override val lyricsSourceConfig: Flow<SourceUsageConfig>
        get() = sourceUsageConfigFlow(PreferencesKeys.LYRICS_SOURCE_CONFIG)

    override val coverSourceConfig: Flow<SourceUsageConfig>
        get() = sourceUsageConfigFlow(PreferencesKeys.COVER_SOURCE_CONFIG)

    override val metadataSourceConfig: Flow<SourceUsageConfig>
        get() = sourceUsageConfigFlow(PreferencesKeys.METADATA_SOURCE_CONFIG)

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

    private data class SourceConfigBundle(
        val legacyOrder: List<Source>,
        val legacyEnabled: Set<Source>,
        val lyrics: SourceUsageConfig,
        val cover: SourceUsageConfig,
        val metadata: SourceUsageConfig
    )

    private val sourceConfigBundleFlow =
        combine(
            searchSourceOrder,
            enabledSearchSources,
            lyricsSourceConfig,
            coverSourceConfig,
            metadataSourceConfig
        ) { legacyOrder, legacyEnabled, lyrics, cover, metadata ->
            SourceConfigBundle(legacyOrder, legacyEnabled, lyrics, cover, metadata)
        }

    override val searchConfigFlow: Flow<SearchConfig> =
        combine(separator, sourceConfigBundleFlow, searchPageSize) { sep, sourceConfig, size ->
            SearchConfig(
                separator = sep,
                searchSourceOrder = sourceConfig.legacyOrder,
                enabledSearchSources = sourceConfig.legacyEnabled,
                searchPageSize = size,
                lyricsSourceConfig = sourceConfig.lyrics,
                coverSourceConfig = sourceConfig.cover,
                metadataSourceConfig = sourceConfig.metadata
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

    override suspend fun saveSearchSourceOrder(sources: List<Source>) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SEARCH_SOURCE_ORDER] =
                sources.toSourceCsv()
        }
    }

    override suspend fun saveEnabledSearchSources(sources: Set<Source>) {
        context.settingsDataStore.edit { preferences ->
            val csv = sources.joinToString(",") { it.name }
            preferences[PreferencesKeys.ENABLED_SEARCH_SOURCES] = csv
        }
    }

    override suspend fun saveSourceUsageConfig(usage: SourceUsage, config: SourceUsageConfig) {
        context.settingsDataStore.edit { preferences ->
            preferences[usage.preferenceKey()] = jsonFormatter.encodeToString(config.normalized())
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
        val backup = SettingsBackup(
            removeEmptyLines = prefs[PreferencesKeys.REMOVE_EMPTY_LINES]
                ?: SettingsDefaults.REMOVE_EMPTY_LINES,

            lyricFormat = prefs[PreferencesKeys.LYRIC_FORMAT]
                ?: SettingsDefaults.LYRIC_FORMAT.name,

            sortBy = prefs[PreferencesKeys.SORT_BY]
                ?: SettingsDefaults.SORT_BY.name,

            sortOrder = prefs[PreferencesKeys.SORT_ORDER]
                ?: SettingsDefaults.SORT_ORDER.name,

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

            searchSourceOrder = (prefs[PreferencesKeys.SEARCH_SOURCE_ORDER] ?: SettingsDefaults.SEARCH_SOURCE_ORDER.toSourceCsv()).toSourceList().map { it.name },

            enabledSearchSources = (prefs[PreferencesKeys.ENABLED_SEARCH_SOURCES] ?: SettingsDefaults.SEARCH_SOURCE_ORDER.toSourceCsv()).split(",").mapNotNull { Source.fromNameOrNull(it) }.map { it.name },

            lyricsSourceConfig = lyricsSourceConfig.first(),
            coverSourceConfig = coverSourceConfig.first(),
            metadataSourceConfig = metadataSourceConfig.first(),

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
            extraMetadataWriteRules = getExtraMetadataWriteRules(),
            renameFormat = prefs[PreferencesKeys.RENAME_FORMAT]
                ?: SettingsDefaults.RENAME_FORMAT,
            batchMatchConfig = batchMatchConfig,
            conversionMode = prefs[PreferencesKeys.CONVERSION_MODE]
                ?: SettingsDefaults.CONVERSION_MODE.name
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
                backup.separator?.let { prefs[PreferencesKeys.SEPARATOR] = it }
                backup.romaEnabled?.let { prefs[PreferencesKeys.ROMA_ENABLED] = it }
                backup.checkUpdateEnabled?.let { prefs[PreferencesKeys.CHECK_UPDATE_ENABLED] = it }
                backup.translationEnabled?.let { prefs[PreferencesKeys.TRANSLATION_ENABLED] = it }
                backup.ignoreShortAudio?.let { prefs[PreferencesKeys.IGNORE_SHORT_AUDIO] = it }
                backup.searchSourceOrder?.let { list ->
                    prefs[PreferencesKeys.SEARCH_SOURCE_ORDER] = list.toSourceList().toSourceCsv()
                }
                
                // 处理启用的搜索源：如果为null或为空，默认启用所有源
                if (backup.enabledSearchSources.isNullOrEmpty()) {
                    // 默认启用所有源
                    val defaultEnabledCsv = SettingsDefaults.DEFAULT_ENABLED_SEARCH_SOURCES.joinToString(",") { it.name }
                    prefs[PreferencesKeys.ENABLED_SEARCH_SOURCES] = defaultEnabledCsv
                } else {
                    // 使用导入的启用源列表，直接映射而不补齐缺失源
                    val validSources = backup.enabledSearchSources.mapNotNull { Source.fromNameOrNull(it) }
                    val csv = validSources.joinToString(",") { it.name }
                    prefs[PreferencesKeys.ENABLED_SEARCH_SOURCES] = csv
                }
                backup.lyricsSourceConfig?.let { config ->
                    prefs[PreferencesKeys.LYRICS_SOURCE_CONFIG] = jsonFormatter.encodeToString(config.normalized())
                }
                backup.coverSourceConfig?.let { config ->
                    prefs[PreferencesKeys.COVER_SOURCE_CONFIG] = jsonFormatter.encodeToString(config.normalized())
                }
                backup.metadataSourceConfig?.let { config ->
                    prefs[PreferencesKeys.METADATA_SOURCE_CONFIG] = jsonFormatter.encodeToString(config.normalized())
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
                backup.extraMetadataWriteRules?.let { rules ->
                    prefs[PreferencesKeys.EXTRA_METADATA_WRITE_RULES] = jsonFormatter.encodeToString(rules)
                }
                backup.renameFormat?.let { prefs[PreferencesKeys.RENAME_FORMAT]  = it }
                backup.batchMatchConfig?.let { config ->
                    prefs[PreferencesKeys.BATCH_MATCH_CONFIG] = jsonFormatter.encodeToString(config)
                }
                backup.conversionMode?.let { prefs[PreferencesKeys.CONVERSION_MODE] = it }
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
    override val batchMatchConfig: Flow<BatchMatchConfig>
        get() = context.settingsDataStore.data.map { preferences ->
            val configJson = preferences[PreferencesKeys.BATCH_MATCH_CONFIG]
            if (configJson.isNullOrBlank()) {
                BatchMatchConfigDefaults.DEFAULT_CONFIG
            } else {
                decodeBatchMatchConfig(configJson)
            }
        }

    override val extraMetadataWriteRules: Flow<List<ExtraMetadataWriteRule>>
        get() = context.settingsDataStore.data.map { preferences ->
            val rulesJson = preferences[PreferencesKeys.EXTRA_METADATA_WRITE_RULES]
            val legacyBatchJson = preferences[PreferencesKeys.BATCH_MATCH_CONFIG]
            if (rulesJson.isNullOrBlank()) {
                defaultExtraRulesWithLegacyReplayGain(legacyBatchJson)
            } else {
                try {
                    mergeExtraRulesWithDefaults(
                        jsonFormatter.decodeFromString<List<ExtraMetadataWriteRule>>(rulesJson)
                    )
                } catch (e: Exception) {
                    defaultExtraRulesWithLegacyReplayGain(legacyBatchJson)
                }
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

    override suspend fun saveExtraMetadataWriteRules(rules: List<ExtraMetadataWriteRule>) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.EXTRA_METADATA_WRITE_RULES] =
                jsonFormatter.encodeToString(mergeExtraRulesWithDefaults(rules))
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

    override suspend fun getExtraMetadataWriteRules(): List<ExtraMetadataWriteRule> {
        return extraMetadataWriteRules.first()
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

    private fun defaultExtraRulesWithLegacyReplayGain(configJson: String?): List<ExtraMetadataWriteRule> {
        val legacyMode = findLegacyBatchFieldMode(configJson, "REPLAY_GAIN")?.toExtraWriteMode()
            ?: ExtraWriteMode.DISABLED
        return ExtraMetadataWriteDefaults.DEFAULT_RULES.map { rule ->
            if (rule.source == Source.QM && rule.target != ExtraMetadataTarget.COMMENT) {
                rule.copy(mode = legacyMode)
            } else {
                rule
            }
        }
    }

    private fun findLegacyBatchFieldMode(configJson: String?, fieldName: String): BatchMatchMode? {
        if (configJson.isNullOrBlank()) return null
        return try {
            val root = jsonFormatter.parseToJsonElement(configJson).jsonObject
            val modeName = root["fields"]?.jsonObject?.get(fieldName)?.jsonPrimitive?.contentOrNull
            modeName?.let { BatchMatchMode.valueOf(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun BatchMatchMode.toExtraWriteMode(): ExtraWriteMode {
        return when (this) {
            BatchMatchMode.SUPPLEMENT -> ExtraWriteMode.SUPPLEMENT
            BatchMatchMode.OVERWRITE -> ExtraWriteMode.OVERWRITE
        }
    }

    private fun mergeExtraRulesWithDefaults(
        rules: List<ExtraMetadataWriteRule>
    ): List<ExtraMetadataWriteRule> {
        return ExtraMetadataWriteDefaults.DEFAULT_RULES.map { defaultRule ->
            rules.firstOrNull {
                it.key == defaultRule.key && it.source == defaultRule.source
            } ?: defaultRule
        }
    }

    private fun sourceUsageConfigFlow(
        key: Preferences.Key<String>
    ): Flow<SourceUsageConfig> {
        return context.settingsDataStore.data.map { preferences ->
            val configJson = preferences[key]
            if (configJson.isNullOrBlank()) {
                legacySourceUsageConfig(preferences)
            } else {
                try {
                    jsonFormatter.decodeFromString<SourceUsageConfig>(configJson).normalized()
                } catch (e: Exception) {
                    legacySourceUsageConfig(preferences)
                }
            }
        }
    }

    private fun legacySourceUsageConfig(preferences: Preferences): SourceUsageConfig {
        val order = preferences[PreferencesKeys.SEARCH_SOURCE_ORDER].toSourceList()
        val enabledCsv = preferences[PreferencesKeys.ENABLED_SEARCH_SOURCES]
        val enabled = if (enabledCsv.isNullOrBlank()) {
            SettingsDefaults.DEFAULT_ENABLED_SEARCH_SOURCES
        } else {
            enabledCsv.split(",").mapNotNull { Source.fromNameOrNull(it) }.toSet()
        }
        return SourceUsageConfig(order, enabled).normalized()
    }

    private fun SourceUsage.preferenceKey(): Preferences.Key<String> {
        return when (this) {
            SourceUsage.LYRICS -> PreferencesKeys.LYRICS_SOURCE_CONFIG
            SourceUsage.COVER -> PreferencesKeys.COVER_SOURCE_CONFIG
            SourceUsage.METADATA -> PreferencesKeys.METADATA_SOURCE_CONFIG
        }
    }

    private fun SourceUsageConfig.normalized(): SourceUsageConfig {
        val order = (sourceOrder + Source.entries).distinct()
        val enabled = if (enabledSources.isEmpty()) {
            SettingsDefaults.DEFAULT_ENABLED_SEARCH_SOURCES
        } else {
            enabledSources
        }.filter { it in order }.toSet()
        return copy(sourceOrder = order, enabledSources = enabled)
    }
}

