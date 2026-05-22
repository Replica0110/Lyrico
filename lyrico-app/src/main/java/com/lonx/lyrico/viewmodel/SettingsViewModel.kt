package com.lonx.lyrico.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.AppLogLevel
import com.lonx.lyrico.data.model.AppLogType
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.CacheCategory
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.MetadataFieldWriteRule
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.lonx.lyrico.data.model.toArtistSeparator
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.ui.theme.KeyColor
import com.lonx.lyrico.ui.theme.KeyColors
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
    val searchSourceOrder: List<String> = emptyList(),
    val enabledSearchSources: Set<String> = emptySet(),
    val searchPageSize: Int = 20,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val monetEnable: Boolean = false,
    val keyColor: KeyColor = KeyColors[1],
    val onlyTranslationIfAvailable: Boolean = false,
    val removeEmptyLines: Boolean = true,
    val categorizedCacheSize: Map<CacheCategory, Long> = emptyMap(),
    val totalCacheSize: Long = 0L,
    val conversionMode: ConversionMode = ConversionMode.NONE,
    val metadataFieldWriteRules: List<MetadataFieldWriteRule> = emptyList()
) {
    /**
     * 返回按优先级排序且启用的搜索源列表
     */
    val filteredSearchSources: List<String>
        get() = searchSourceOrder.filter { it in enabledSearchSources }
}
sealed class SettingsEvent {
    data class ShowToast(val message: UiMessage) : SettingsEvent()
}
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val database: LyricoDatabase,
    private val appLogRepository: AppLogRepository
) : ViewModel() {
    private val folder = database.folderDao()
    private val _categorizedCacheSize = MutableStateFlow<Map<CacheCategory, Long>>(emptyMap())

    private data class SettingsBaseState(
        val lyric: com.lonx.lyrico.data.model.LyricRenderConfig,
        val search: com.lonx.lyrico.data.model.SearchConfig,
        val theme: com.lonx.lyrico.data.model.ThemeConfig,
        val ignoreShortAudio: Boolean,
        val metadataFieldRules: List<MetadataFieldWriteRule>
    )

    private val settingsBaseState = combine(
        settingsRepository.lyricRenderConfigFlow,
        settingsRepository.searchConfigFlow,
        settingsRepository.themeConfigFlow,
        settingsRepository.ignoreShortAudio,
        settingsRepository.metadataFieldWriteRules
    ) { lyric, search, theme, ignoreShort, metadataFieldRules ->
        SettingsBaseState(lyric, search, theme, ignoreShort, metadataFieldRules)
    }

    private val baseUiState = combine(
        settingsBaseState,
        _categorizedCacheSize
    ) { base, cacheMap ->
        SettingsUiState(
            lyricFormat = base.lyric.format,
            romaEnabled = base.lyric.showRomanization,
            translationEnabled = base.lyric.showTranslation,
            separator = base.search.separator.toArtistSeparator(),
            searchSourceOrder = base.search.searchSourceOrder,
            enabledSearchSources = base.search.enabledSearchSources,
            searchPageSize = base.search.searchPageSize,
            themeMode = base.theme.themeMode,
            ignoreShortAudio = base.ignoreShortAudio,
            monetEnable = base.theme.monetEnable,
            keyColor = base.theme.keyColor,
            categorizedCacheSize = cacheMap,
            onlyTranslationIfAvailable = base.lyric.onlyTranslationIfAvailable,
            totalCacheSize = cacheMap.values.sum(),
            removeEmptyLines = base.lyric.removeEmptyLines,
            conversionMode = base.lyric.conversionMode,
            metadataFieldWriteRules = base.metadataFieldRules
        )
    }

    // 使用 combine 合并各分组设置流和缓存流
    val uiState: StateFlow<SettingsUiState> = baseUiState.stateIn(
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
    fun setMonetEnable(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveMonetEnable(enabled)
        }
    }
    fun setKeyColor(selectedMode: KeyColor) {
        viewModelScope.launch {
            settingsRepository.saveKeyColor(selectedMode)
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
    fun setSearchSourceOrder(sources: List<String>) {
        viewModelScope.launch {
            settingsRepository.saveSearchSourceOrder(sources)
        }
    }

    fun setEnabledSearchSources(sources: Set<String>) {
        viewModelScope.launch {
            settingsRepository.saveEnabledSearchSources(sources)
        }
    }
    fun setSearchPageSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.saveSearchPageSize(size)
        }
    }

    fun setMetadataFieldWriteRules(rules: List<MetadataFieldWriteRule>) {
        viewModelScope.launch {
            settingsRepository.saveMetadataFieldWriteRules(rules)
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
                appLogRepository.log(
                    level = AppLogLevel.INFO,
                    type = AppLogType.APP,
                    tag = TAG,
                    message = "Settings exported",
                    detail = "uri=$uri"
                )
                _events.emit(SettingsEvent.ShowToast(UiMessage.StringResource(R.string.export_success)))
            } catch (e: Exception) {
                e.printStackTrace()
                appLogRepository.logException(
                    type = AppLogType.APP,
                    tag = TAG,
                    message = "Failed to export settings",
                    throwable = e
                )
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
                        appLogRepository.log(
                            level = AppLogLevel.INFO,
                            type = AppLogType.APP,
                            tag = TAG,
                            message = "Settings imported",
                            detail = "uri=$uri"
                        )
                        _events.emit(SettingsEvent.ShowToast(
                            UiMessage.StringResource(R.string.import_success)
                        ))
                    } else {
                        appLogRepository.log(
                            level = AppLogLevel.WARNING,
                            type = AppLogType.APP,
                            tag = TAG,
                            message = "Settings import rejected: invalid format",
                            detail = "uri=$uri"
                        )
                        _events.emit(SettingsEvent.ShowToast(
                            UiMessage.StringResource(R.string.import_failed_format)
                        ))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                appLogRepository.logException(
                    type = AppLogType.APP,
                    tag = TAG,
                    message = "Failed to import settings",
                    throwable = e
                )
                _events.emit(SettingsEvent.ShowToast(
                    UiMessage.StringResource(R.string.import_failed, e.message ?: "Unknown error")
                ))
            }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}

