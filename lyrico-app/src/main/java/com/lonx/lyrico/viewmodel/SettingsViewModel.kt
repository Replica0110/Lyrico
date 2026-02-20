package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.dao.FolderDao
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.dao.SongDao
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.lonx.lyrico.data.model.toArtistSeparator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val lyricFormat: LyricFormat = LyricFormat.VERBATIM_LRC,
    val separator: ArtistSeparator = ArtistSeparator.SLASH,
    val romaEnabled: Boolean = false,
    val translationEnabled: Boolean = false,
    val ignoreShortAudio: Boolean = false,
    val searchSourceOrder: List<Source> = emptyList(),
    val searchPageSize: Int = 20,
    val themeMode: ThemeMode = ThemeMode.AUTO
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val database: LyricoDatabase
) : ViewModel() {

    private val folderDao: FolderDao = database.folderDao()
    private val songDao: SongDao = database.songDao()

    val uiState: StateFlow<SettingsUiState> =
        settingsRepository.settingsFlow
            .map { settings ->
                SettingsUiState(
                    lyricFormat = settings.lyricFormat,
                    romaEnabled = settings.romaEnabled,
                    translationEnabled = settings.translationEnabled,
                    separator = settings.separator.toArtistSeparator(),
                    searchSourceOrder = settings.searchSourceOrder,
                    searchPageSize = settings.searchPageSize,
                    themeMode = settings.themeMode,
                    ignoreShortAudio = settings.ignoreShortAudio
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SettingsUiState()
            )

    fun setLyricFormat(mode: LyricFormat) {
        viewModelScope.launch {
            settingsRepository.saveLyricDisplayMode(mode)
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

