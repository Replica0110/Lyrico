package com.lonx.lyrico.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lonx.lyrico.data.model.LyricDisplayMode
import com.lonx.lyrico.data.model.ThemeMode
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
        val LYRIC_DISPLAY_MODE = stringPreferencesKey("lyric_display_mode")
        val LAST_SCAN_TIME = longPreferencesKey("last_scan_time")
        val SORT_BY = stringPreferencesKey("sort_by")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SEPARATOR = stringPreferencesKey("separator")
        val ROMA_ENABLED = booleanPreferencesKey("roma_enabled")
        val SEARCH_SOURCE_ORDER = stringPreferencesKey("search_source_order")
        val SEARCH_PAGE_SIZE = intPreferencesKey("search_page_size")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    // 默认搜索源顺序
    private val defaultSourceOrder = listOf(Source.QM, Source.KG, Source.NE)
    private val defaultSearchPageSize = 10

    override val lyricDisplayMode: Flow<LyricDisplayMode>
        get() = context.settingsDataStore.data.map { preferences ->
            LyricDisplayMode.valueOf(
                preferences[PreferencesKeys.LYRIC_DISPLAY_MODE]
                    ?: LyricDisplayMode.WORD_BY_WORD.name
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

    override suspend fun getLastScanTime(): Long {
        return context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIME] ?: 0L
        }.first()
    }

    override suspend fun saveLyricDisplayMode(mode: LyricDisplayMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.LYRIC_DISPLAY_MODE] = mode.name
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

    override val settingsFlow: Flow<SettingsSnapshot> = combine(
        lyricDisplayMode,
        romaEnabled,
        separator,
        searchSourceOrder,
        searchPageSize,
        themeMode
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        SettingsSnapshot(
            lyricDisplayMode = array[0] as LyricDisplayMode,
            romaEnabled = array[1] as Boolean,
            separator = array[2] as String,
            searchSourceOrder = array[3] as List<Source>,
            searchPageSize = array[4] as Int,
            themeMode = array[5] as ThemeMode
        )
    }

}

