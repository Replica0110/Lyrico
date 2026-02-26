package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.flow.Flow
data class SettingsSnapshot(
    val lyricFormat: LyricFormat,
    val romaEnabled: Boolean,
    val separator: String,
    val searchSourceOrder: List<Source>,
    val searchPageSize: Int,
    val themeMode: ThemeMode,
    val ignoreShortAudio: Boolean,
    val translationEnabled: Boolean,
    val onlyTranslationIfAvailable: Boolean
)

interface SettingsRepository {
    // Flow properties
    val lyricFormat: Flow<LyricFormat>
    val sortInfo: Flow<SortInfo>
    val separator: Flow<String>
    val romaEnabled: Flow<Boolean>

    val translationEnabled: Flow<Boolean>
    val checkUpdateEnabled: Flow<Boolean>
    val ignoreShortAudio: Flow<Boolean>
    val searchSourceOrder: Flow<List<Source>>
    val searchPageSize: Flow<Int>
    val themeMode: Flow<ThemeMode>
    val onlyTranslationIfAvailable: Flow<Boolean>
    val settingsFlow: Flow<SettingsSnapshot>
    // Suspend functions for operations that might block or are one-off
    suspend fun getLastScanTime(): Long
    
    // Save functions
    suspend fun saveLyricDisplayMode(mode: LyricFormat)
    suspend fun saveSortInfo(sortInfo: SortInfo)
    suspend fun saveSeparator(separator: String)
    suspend fun saveRomaEnabled(enabled: Boolean)
    suspend fun saveCheckUpdateEnabled(enabled: Boolean)
    suspend fun saveTranslationEnabled(enabled: Boolean)
    suspend fun saveIgnoreShortAudio(enabled: Boolean)
    suspend fun saveLastScanTime(time: Long)
    suspend fun saveSearchSourceOrder(sources: List<Source>)
    suspend fun saveSearchPageSize(size: Int)
    suspend fun saveThemeMode(mode: ThemeMode)
    suspend fun saveOnlyTranslationIfAvailable(enabled: Boolean)
    suspend fun getLyricRenderConfig(): LyricRenderConfig
}
