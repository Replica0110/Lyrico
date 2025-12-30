package com.lonx.lyrico.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lonx.lyrico.data.model.LyricDisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    private object PreferencesKeys {
        val LYRIC_DISPLAY_MODE = stringPreferencesKey("lyric_display_mode")
        val LAST_SCAN_TIME = longPreferencesKey("last_scan_time")
        val SORT_BY = stringPreferencesKey("sort_by")
        val SORT_ORDER = stringPreferencesKey("sort_order")
    }


    suspend fun saveLyricDisplayMode(mode: LyricDisplayMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LYRIC_DISPLAY_MODE] = mode.name
        }
    }

    suspend fun saveSortInfo(sortInfo: com.lonx.lyrico.viewmodel.SortInfo) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_BY] = sortInfo.sortBy.name
            preferences[PreferencesKeys.SORT_ORDER] = sortInfo.order.name
        }
    }

    fun getSortInfo(): Flow<com.lonx.lyrico.viewmodel.SortInfo> {
        return context.dataStore.data.map { preferences ->
            val sortBy = com.lonx.lyrico.viewmodel.SortBy.valueOf(
                preferences[PreferencesKeys.SORT_BY] ?: com.lonx.lyrico.viewmodel.SortBy.TITLE.name
            )
            val sortOrder = com.lonx.lyrico.viewmodel.SortOrder.valueOf(
                preferences[PreferencesKeys.SORT_ORDER] ?: com.lonx.lyrico.viewmodel.SortOrder.ASC.name
            )
            com.lonx.lyrico.viewmodel.SortInfo(sortBy, sortOrder)
        }
    }

    suspend fun saveLastScanTime(time: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIME] = time
        }
    }

    suspend fun getLastScanTime(): Long {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIME] ?: 0L
        }.first()
    }
}
