package com.lonx.lyrico.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.model.SettingsBackup
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.model.toSourceCsv
import com.lonx.lyrics.model.toSourceList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.json.Json


private val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsDefaults {
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

    // 搜索源顺序默认值
    val SEARCH_SOURCE_ORDER = listOf(Source.QM, Source.KG, Source.NE)
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
        val REMOVE_EMPTY_LINES = booleanPreferencesKey("remove_empty_lines")
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
        val SEARCH_PAGE_SIZE = intPreferencesKey("search_page_size")
        val THEME_MODE = stringPreferencesKey("theme_mode")

        val ONLY_TRANSLATION_IF_AVAILABLE = booleanPreferencesKey("only_translation_if_available")
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

    override val onlyTranslationIfAvailable: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.ONLY_TRANSLATION_IF_AVAILABLE]
                ?: SettingsDefaults.ONLY_TRANSLATION_IF_AVAILABLE
        }

    override val removeEmptyLines: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.REMOVE_EMPTY_LINES] ?: SettingsDefaults.REMOVE_EMPTY_LINES
        }
    override val showScrollTopButton: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.SHOW_SCROLL_TOP_BUTTON] ?: SettingsDefaults.SHOW_SCROLL_TOP_BUTTON
        }

    override suspend fun getLastScanTime(): Long {
        return context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIME] ?: 0L
        }.first()
    }

    private data class LyricPart(
        val lyricFormat: LyricFormat,
        val romaEnabled: Boolean,
        val translationEnabled: Boolean,
        val onlyTranslationIfAvailable: Boolean,
        val removeEmptyLines: Boolean
    )

    private val lyricPartFlow =
        combine(
            lyricFormat,
            romaEnabled,
            translationEnabled,
            onlyTranslationIfAvailable,
            removeEmptyLines
        ) { format, roma, translation, onlyTranslation, removeEmptyLines ->
            LyricPart(
                lyricFormat = format,
                romaEnabled = roma,
                translationEnabled = translation,
                onlyTranslationIfAvailable = onlyTranslation,
                removeEmptyLines = removeEmptyLines
            )
        }

    private data class SearchPart(
        val separator: String,
        val searchSourceOrder: List<Source>,
        val searchPageSize: Int
    )

    private val searchPartFlow =
        combine(separator, searchSourceOrder, searchPageSize) { sep, order, size ->
            SearchPart(sep, order, size)
        }

    private data class UiPart(
        val themeMode: ThemeMode,
        val ignoreShortAudio: Boolean,
        val showScrollTopButton: Boolean
    )

    private val uiPartFlow =
        combine(themeMode, ignoreShortAudio, showScrollTopButton) { theme, ignore, showScrollTop ->
            UiPart(
                themeMode = theme,
                ignoreShortAudio = ignore,
                showScrollTopButton = showScrollTop
            )
        }
    override val settingsFlow: Flow<SettingsSnapshot> =
        combine(
            lyricPartFlow,
            searchPartFlow,
            uiPartFlow
        ) { lyric, search, ui ->
            SettingsSnapshot(
                lyricFormat = lyric.lyricFormat,
                romaEnabled = lyric.romaEnabled,
                translationEnabled = lyric.translationEnabled,
                onlyTranslationIfAvailable = lyric.onlyTranslationIfAvailable,
                separator = search.separator,
                searchSourceOrder = search.searchSourceOrder,
                searchPageSize = search.searchPageSize,
                themeMode = ui.themeMode,
                ignoreShortAudio = ui.ignoreShortAudio,
                removeEmptyLines = lyric.removeEmptyLines,
                showScrollTopButton = ui.showScrollTopButton
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

    override suspend fun saveSearchPageSize(size: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SEARCH_PAGE_SIZE] = size
        }
    }

    override suspend fun saveThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    override suspend fun saveRemoveEmptyLines(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.REMOVE_EMPTY_LINES] = enabled
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
        return LyricRenderConfig(
            format = format,
            showRomanization = roma,
            removeEmptyLines = removeEmptyLines,
            showTranslation = showTranslation,
            onlyTranslationIfAvailable = onlyTranslationIfAvailable
        )
    }

    override suspend fun exportSettings(): String {
        val prefs = context.settingsDataStore.data.first()

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

            searchSourceOrder = SettingsDefaults.SEARCH_SOURCE_ORDER.map { it.name },

            searchPageSize = prefs[PreferencesKeys.SEARCH_PAGE_SIZE]
                ?: SettingsDefaults.SEARCH_PAGE_SIZE,

            themeMode = prefs[PreferencesKeys.THEME_MODE]
                ?: SettingsDefaults.THEME_MODE.name,

            onlyTranslationIfAvailable = prefs[PreferencesKeys.ONLY_TRANSLATION_IF_AVAILABLE]
                ?: SettingsDefaults.ONLY_TRANSLATION_IF_AVAILABLE,

            showScrollTopButton = prefs[PreferencesKeys.SHOW_SCROLL_TOP_BUTTON]
                ?: SettingsDefaults.SHOW_SCROLL_TOP_BUTTON
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
                    prefs[PreferencesKeys.SEARCH_SOURCE_ORDER] =
                        list.toSourceList().toSourceCsv()
                }
                backup.searchPageSize?.let { prefs[PreferencesKeys.SEARCH_PAGE_SIZE] = it }
                backup.themeMode?.let { prefs[PreferencesKeys.THEME_MODE] = it }
                backup.onlyTranslationIfAvailable?.let {
                    prefs[PreferencesKeys.ONLY_TRANSLATION_IF_AVAILABLE] = it
                }
                backup.showScrollTopButton?.let {
                    prefs[PreferencesKeys.SHOW_SCROLL_TOP_BUTTON] = it
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

}

