package com.lonx.lyrico.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.CacheCategory
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.lonx.lyrico.data.model.toArtistSeparator
import com.lonx.lyrico.utils.CacheManager
import com.lonx.lyrico.utils.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val lyricFormat: LyricFormat = LyricFormat.VERBATIM_LRC,
    val separator: ArtistSeparator = ArtistSeparator.SLASH,
    val romaEnabled: Boolean = false,
    val translationEnabled: Boolean = false,
    val ignoreShortAudio: Boolean = false,
    val searchSourceOrder: List<Source> = emptyList(),
    val searchPageSize: Int = 20,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val onlyTranslationIfAvailable: Boolean = false,
    val removeEmptyLines: Boolean = true,
    val categorizedCacheSize: Map<CacheCategory, Long> = emptyMap(),
    val totalCacheSize: Long = 0L,
    val conversionMode: ConversionMode = ConversionMode.NONE
)
sealed class SettingsEvent {
    data class ShowToast(val message: UiMessage) : SettingsEvent()
}
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val database: LyricoDatabase
) : ViewModel() {
    private val folder = database.folderDao()
    private val _categorizedCacheSize = MutableStateFlow<Map<CacheCategory, Long>>(emptyMap())

    // 使用 combine 合并设置流和缓存流
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        _categorizedCacheSize,
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
            onlyTranslationIfAvailable = settings.onlyTranslationIfAvailable,
            totalCacheSize = cacheMap.values.sum(),
            removeEmptyLines = settings.removeEmptyLines,
            conversionMode = settings.conversionMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )
    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()
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
    suspend fun clearSongs(): Boolean = withContext(Dispatchers.IO) {
        folder.clearAllFolders()
        val counts = folder.getFoldersCount()
        counts == 0
    }
    fun setTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveTranslationEnabled(enabled)
        }
    }
    fun setOnlyTranslationIfAvailable(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveOnlyTranslationIfAvailable(enabled)
        }
    }

    fun setRemoveEmptyLines(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveRemoveEmptyLines(enabled)
        }
    }
    fun setSeparator(separator: ArtistSeparator) {
        viewModelScope.launch {
            settingsRepository.saveSeparator(separator.toText())
        }
    }

    fun setConversionMode(mode: ConversionMode) {
        viewModelScope.launch {
            settingsRepository.saveConversionMode(mode)
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
    fun exportSettings(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = settingsRepository.exportSettings()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                _events.emit(SettingsEvent.ShowToast(UiMessage.StringResource(R.string.export_success)))
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(SettingsEvent.ShowToast(UiMessage.StringResource(R.string.export_failed, e.message ?: "Unknown error")))
            }
        }
    }

    fun importSettings(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }

                if (jsonString != null) {
                    val success = settingsRepository.importSettings(jsonString)
                    if (success) {
                        _events.emit(SettingsEvent.ShowToast(
                            UiMessage.StringResource(R.string.import_success)
                        ))
                    } else {
                        _events.emit(SettingsEvent.ShowToast(
                            UiMessage.StringResource(R.string.import_failed_format)
                        ))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(SettingsEvent.ShowToast(
                    UiMessage.StringResource(R.string.import_failed, e.message ?: "Unknown error")
                ))
            }
        }
    }
}

