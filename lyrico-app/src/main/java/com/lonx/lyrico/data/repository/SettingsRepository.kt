package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.LyricDisplayMode
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.flow.Flow
data class SettingsSnapshot(
    val lyricDisplayMode: LyricDisplayMode,
    val romaEnabled: Boolean,
    val separator: String,
    val searchSourceOrder: List<Source>,
    val searchPageSize: Int,
    val themeMode: ThemeMode
)

interface SettingsRepository {
    // Flow properties
    val lyricDisplayMode: Flow<LyricDisplayMode>
    val sortInfo: Flow<SortInfo>
    val separator: Flow<String>
    val romaEnabled: Flow<Boolean>
    val searchSourceOrder: Flow<List<Source>>
    val searchPageSize: Flow<Int>
    val themeMode: Flow<ThemeMode>

    val settingsFlow: Flow<SettingsSnapshot>
    // Suspend functions for operations that might block or are one-off
    suspend fun getLastScanTime(): Long
    
    // Save functions
    suspend fun saveLyricDisplayMode(mode: LyricDisplayMode)
    suspend fun saveSortInfo(sortInfo: SortInfo)
    suspend fun saveSeparator(separator: String)
    suspend fun saveRomaEnabled(enabled: Boolean)
    suspend fun saveLastScanTime(time: Long)
    suspend fun saveSearchSourceOrder(sources: List<Source>)
    suspend fun saveSearchPageSize(size: Int)
    suspend fun saveThemeMode(mode: ThemeMode)
}
