package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.model.LyricDisplayMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.lonx.lyrico.data.model.toArtistSeparator
import kotlinx.coroutines.flow.combine

data class SettingsUiState(
    val lyricDisplayMode: LyricDisplayMode = LyricDisplayMode.WORD_BY_WORD,
    val separator: ArtistSeparator = ArtistSeparator.SLASH,
    val romaEnabled: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.lyricDisplayMode,
                settingsRepository.romaEnabled,
                settingsRepository.separator
            ) { lyricDisplayMode, romaEnabled, separator ->
                SettingsUiState(
                    lyricDisplayMode = lyricDisplayMode,
                    romaEnabled = romaEnabled,
                    separator = separator.toArtistSeparator()
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }


    fun setLyricDisplayMode(mode: LyricDisplayMode) {
        viewModelScope.launch {
            settingsRepository.saveLyricDisplayMode(mode)
            _uiState.update {
                it.copy(lyricDisplayMode = mode)
            }
        }
    }
    fun setRomaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveRomaEnabled(enabled)
            _uiState.update {
                it.copy(romaEnabled = enabled)
            }
        }
    }
    fun setSeparator(separator: ArtistSeparator) {
        viewModelScope.launch {
            settingsRepository.saveSeparator(separator.toText())
            _uiState.update {
                it.copy(separator = separator)
            }
        }
    }
}
