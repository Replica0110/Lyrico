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
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.ui.theme.KeyColor
import com.lonx.lyrico.ui.theme.KeyColors
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private object PreferencesKeys {
        val REMOVE_EMPTY_LINES = booleanPreferencesKey("remove_empty_lines")
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
        val MONET_ENABLE = booleanPreferencesKey("monet_enable")
        val KEY_THEME_COLOR = intPreferencesKey("theme_color_argb")

        val ONLY_TRANSLATION_IF_AVAILABLE = booleanPreferencesKey("only_translation_if_available")
    }

    // 默认搜索源顺序
    private val defaultSourceOrder = listOf(Source.QM, Source.KG, Source.NE)
    private val defaultSearchPageSize = 10
    private val DEFAULT_COLOR_INT = 0xFF3482FF.toInt()
    override val lyricFormat: Flow<LyricFormat>
        get() = context.settingsDataStore.data.map { preferences ->
            LyricFormat.valueOf(
                preferences[PreferencesKeys.LYRIC_FORMAT]
                    ?: LyricFormat.VERBATIM_LRC.name
            )
        }

    override val sortInfo: Flow<SortInfo>
        get() = context.settingsDataStore.data.map { preferences ->
            val sortBy = SortBy.valueOf(
                preferences[PreferencesKeys.SORT_BY] ?: SortBy.TITLE.name
            )
            val sortOrder = SortOrder.valueOf(
                preferences[PreferencesKeys.SORT_ORDER]
                    ?: SortOrder.ASC.name
            )
            SortInfo(sortBy, sortOrder)
        }

    override val separator: Flow<String>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.SEPARATOR] ?: "/"
        }

    override val romaEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.ROMA_ENABLED] ?: true
        }
    override val translationEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.TRANSLATION_ENABLED] ?: true
        }
    override val checkUpdateEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.CHECK_UPDATE_ENABLED] ?: true
        }
    override val ignoreShortAudio: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.IGNORE_SHORT_AUDIO] ?: true
        }

    override val searchSourceOrder: Flow<List<Source>>
        get() = context.settingsDataStore.data.map { preferences ->
            val orderString = preferences[PreferencesKeys.SEARCH_SOURCE_ORDER]
            if (orderString.isNullOrBlank()) {
                defaultSourceOrder
            } else {
                try {
                    orderString.split(",").mapNotNull { name ->
                        try {
                            Source.valueOf(name.trim())
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }.ifEmpty { defaultSourceOrder }
                } catch (e: Exception) {
                    defaultSourceOrder
                }
            }
        }
    override val searchPageSize: Flow<Int>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.SEARCH_PAGE_SIZE] ?: defaultSearchPageSize
        }

    override val themeMode: Flow<ThemeMode>
        get() = context.settingsDataStore.data.map { preferences ->
            val modeName = preferences[PreferencesKeys.THEME_MODE]
            if (modeName.isNullOrBlank()) {
                ThemeMode.AUTO
            } else {
                try {
                    ThemeMode.valueOf(modeName)
                } catch (e: IllegalArgumentException) {
                    ThemeMode.AUTO
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

    override val onlyTranslationIfAvailable: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.ONLY_TRANSLATION_IF_AVAILABLE] ?: false
        }

    override val removeEmptyLines: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.REMOVE_EMPTY_LINES] ?: true
        }
    override suspend fun getLastScanTime(): Long {
        return context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIME] ?: 0L
        }.first()
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
            preferences[PreferencesKeys.SEARCH_SOURCE_ORDER] = sources.joinToString(",") { it.name }
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
        val monetEnable: Boolean,
        val keyColor: KeyColor
    )

    private val uiPartFlow =
        combine(themeMode, ignoreShortAudio, monetEnable, keyColor) { theme, ignore, monetEnable, keyColor ->
            UiPart(
                themeMode = theme,
                ignoreShortAudio = ignore,
                monetEnable = monetEnable,
                keyColor = keyColor
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
                monetEnable = ui.monetEnable,
                keyColor = ui.keyColor,
                removeEmptyLines = lyric.removeEmptyLines
            )
        }

    override suspend fun saveOnlyTranslationIfAvailable(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.ONLY_TRANSLATION_IF_AVAILABLE] = enabled
        }
    }

    override suspend fun saveRemoveEmptyLines(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.REMOVE_EMPTY_LINES] = enabled
        }
    }
    override suspend fun getLyricRenderConfig(): LyricRenderConfig {
        val prefs = context.settingsDataStore.data.first()

        val format = LyricFormat.valueOf(
            prefs[PreferencesKeys.LYRIC_FORMAT]
                ?: LyricFormat.VERBATIM_LRC.name
        )

        val roma = prefs[PreferencesKeys.ROMA_ENABLED] ?: true

        val showTranslation = prefs[PreferencesKeys.TRANSLATION_ENABLED] ?: true

        val onlyTranslationIfAvailable = prefs[PreferencesKeys.ONLY_TRANSLATION_IF_AVAILABLE] ?: false
        return LyricRenderConfig(
            format = format,
            showRomanization = roma,
            showTranslation = showTranslation,
            onlyTranslationIfAvailable = onlyTranslationIfAvailable
        )
    }


}

