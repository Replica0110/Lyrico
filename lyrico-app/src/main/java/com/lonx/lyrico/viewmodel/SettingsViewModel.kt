package com.lonx.lyrico.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.CacheCategory
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.lonx.lyrico.data.model.toArtistSeparator
import com.lonx.lyrico.utils.CacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val lyricFormat: LyricFormat = LyricFormat.VERBATIM_LRC,
    val separator: ArtistSeparator = ArtistSeparator.SLASH,
    val romaEnabled: Boolean = false,
    val translationEnabled: Boolean = false,
    val ignoreShortAudio: Boolean = false,
    val searchSourceOrder: List<Source> = emptyList(),
    val searchPageSize: Int = 20,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val categorizedCacheSize: Map<CacheCategory, Long> = emptyMap(),
    val totalCacheSize: Long = 0L,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _categorizedCacheSize = MutableStateFlow<Map<CacheCategory, Long>>(emptyMap())

    // 使用 combine 合并设置流和缓存流
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        _categorizedCacheSize
    ) { settings, cacheMap ->
        SettingsUiState(
            lyricFormat = settings.lyricFormat,
            romaEnabled = settings.romaEnabled,
            translationEnabled = settings.translationEnabled,
            separator = settings.separator.toArtistSeparator(),
            searchSourceOrder = settings.searchSourceOrder,
            searchPageSize = settings.searchPageSize,
            themeMode = settings.themeMode,
            ignoreShortAudio = settings.ignoreShortAudio,
            categorizedCacheSize = cacheMap,
            totalCacheSize = cacheMap.values.sum()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setLyricFormat(mode: LyricFormat) {
        viewModelScope.launch {
            settingsRepository.saveLyricDisplayMode(mode)
        }
    }
    fun refreshCache(context: Context) {
        viewModelScope.launch {
            val sizes = CacheManager.getCategorizedCacheSize(context)
            _categorizedCacheSize.value = sizes
        }
    }
    fun clearCache(context: Context) {
        viewModelScope.launch {
            CacheManager.clearAllCache(context)
            refreshCache(context)
        }
    }

    fun setRomaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveRomaEnabled(enabled)
        }
    }
    fun setTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveTranslationEnabled(enabled)
        }
    }

    fun setSeparator(separator: ArtistSeparator) {
        viewModelScope.launch {
            settingsRepository.saveSeparator(separator.toText())
        }
    }

    fun setIgnoreShortAudio(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveIgnoreShortAudio(enabled)
        }
    }
    fun setSearchSourceOrder(sources: List<Source>) {
        viewModelScope.launch {
            settingsRepository.saveSearchSourceOrder(sources)
        }
    }
    fun setSearchPageSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.saveSearchPageSize(size)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.saveThemeMode(mode)
        }
    }

}

