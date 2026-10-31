package com.lonx.lyrico.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.cache.CacheCategory
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.FloatingBarEffect
import com.lonx.lyrico.data.model.ReplayGainPeakMode
import com.lonx.lyrico.data.model.ReplayGainSettings
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricLineTrack
import com.lonx.lyrico.data.model.lyrics.LyricsProcessingOptions
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldWriteRule
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.model.SearchSourceTabStyle
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.lonx.lyrico.data.model.toArtistSeparator
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.ui.theme.KeyColor
import com.lonx.lyrico.ui.theme.KeyColors
import com.lonx.lyrico.utils.CacheManager
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.utils.DownloadsFileWriter
import com.lonx.lyrico.data.model.log.recordOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.lonx.lyrico.platform.player.AudioPlayerApps
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val isInitialized: Boolean = false,
    val defaultPlayerComponent: String? = null,
    val defaultPlayerLabel: String? = null,
    val lyricFormat: LyricFormat = LyricFormat.VERBATIM_LRC,
    val separator: ArtistSeparator = ArtistSeparator.SLASH,
    val romaEnabled: Boolean = false,
    val lyricLineOrder: List<LyricLineTrack> = com.lonx.lyrico.data.model.lyrics.DefaultLyricLineOrder,
    val translationEnabled: Boolean = false,
    val lyricIndexEnabled: Boolean = false,
    val ignoreShortAudio: Boolean = false,
    val searchSourceOrder: List<String> = emptyList(),
    val enabledSearchSources: Set<String> = emptySet(),
    val searchPageSize: Int = 20,
    val searchSourceTabStyle: SearchSourceTabStyle = SearchSourceTabStyle.ICON_AND_TEXT,
    val showAllSearchResultFields: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val monetEnable: Boolean = false,
    val floatingBottomBarEnabled: Boolean = true,
    val barBlurEnabled: Boolean = false,
    val floatingBarEffect: FloatingBarEffect = FloatingBarEffect.NONE,
    val keyColor: KeyColor = KeyColors[1],
    val onlyTranslationIfAvailable: Boolean = false,
    val removeEmptyLines: Boolean = true,
    val categorizedCacheSize: Map<CacheCategory, Long> = emptyMap(),
    val totalCacheSize: Long = 0L,
    val conversionMode: ConversionMode = ConversionMode.NONE,
    val lyricsTagLineKeywords: List<String> = emptyList(),
    val metadataFieldWriteRules: List<PluginMetadataFieldWriteRule> = emptyList(),
    val replayGainTargetLoudness: Double = -18.0,
    val replayGainPeakMode: ReplayGainPeakMode = ReplayGainPeakMode.SAMPLE_PEAK
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
    private val appLogRepository: AppLogRepository,
    private val applicationContext: Context,
) : ViewModel() {
    private val folder = database.folderDao()
    private val _categorizedCacheSize = MutableStateFlow<Map<CacheCategory, Long>>(emptyMap())
    private val defaultPlayerRefresh = MutableStateFlow(0L)
    private data class DefaultPlayerState(val component: String?, val label: String?)
    private val defaultPlayerState = combine(
        settingsRepository.defaultPlayerComponent, defaultPlayerRefresh
    ) { component, _ -> component }.map { component ->
        val label = component?.let { withContext(Dispatchers.IO) { AudioPlayerApps.label(applicationContext, it) } }
        DefaultPlayerState(component, label)
    }

    fun refreshDefaultPlayerInfo() {
        defaultPlayerRefresh.value++
    }

    fun setDefaultPlayerComponent(component: String?) {
        viewModelScope.launch {
            try {
                settingsRepository.saveDefaultPlayerComponent(component)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.emit(SettingsEvent.ShowToast(
                    UiMessage.StringResource(R.string.unknown_error, error.message.orEmpty())
                ))
            }
        }
    }

    private data class SettingsBaseState(
        val lyric: LyricRenderConfig,
        val search: com.lonx.lyrico.data.model.SearchConfig,
        val theme: com.lonx.lyrico.data.model.ThemeConfig,
        val ignoreShortAudio: Boolean,
        val lyricsTagLineKeywords: List<String>,
        val metadataFieldRules: List<PluginMetadataFieldWriteRule>,
        val replayGainTargetLoudness: Double,
        val replayGainPeakMode: ReplayGainPeakMode,
        val floatingBottomBarEnabled: Boolean,
        val barBlurEnabled: Boolean,
        val floatingBarEffect: FloatingBarEffect
    )

    private data class VisualSettingsState(
        val floatingBottomBarEnabled: Boolean,
        val barBlurEnabled: Boolean,
        val floatingBarEffect: FloatingBarEffect,
    )

    private data class SettingsTailState(
        val ignoreShortAudio: Boolean,
        val lyricsTagLineKeywords: List<String>,
        val metadataFieldRules: List<PluginMetadataFieldWriteRule>,
        val replayGain: ReplayGainSettings,
        val visual: VisualSettingsState
    )

    private val visualSettingsState = combine(
        settingsRepository.floatingBottomBarEnabled,
        settingsRepository.barBlurEnabled,
        settingsRepository.floatingBarEffect,
    ) { floatingBar, barBlur, floatingBarEffect ->
        VisualSettingsState(floatingBar, barBlur, floatingBarEffect)
    }

    private val settingsTailState = combine(
        settingsRepository.ignoreShortAudio,
        settingsRepository.lyricsTagLineKeywords,
        settingsRepository.metadataFieldWriteRules,
        settingsRepository.replayGainSettings,
        visualSettingsState,
    ) { ignoreShort, lyricsTagLineKeywords, metadataFieldRules, replayGain, visual ->
        SettingsTailState(ignoreShort, lyricsTagLineKeywords, metadataFieldRules, replayGain, visual)
    }

    private val settingsBaseState = combine(
        settingsRepository.lyricRenderConfigFlow,
        settingsRepository.searchConfigFlow,
        settingsRepository.themeConfigFlow,
        settingsTailState
    ) { lyric, search, theme, tail ->
        SettingsBaseState(
            lyric,
            search,
            theme,
            tail.ignoreShortAudio,
            tail.lyricsTagLineKeywords,
            tail.metadataFieldRules,
            tail.replayGain.targetLoudness,
            tail.replayGain.peakMode,
            tail.visual.floatingBottomBarEnabled,
            tail.visual.barBlurEnabled,
            tail.visual.floatingBarEffect,
        )
    }

    private val baseUiState = combine(
        settingsBaseState,
        _categorizedCacheSize,
        settingsRepository.lyricIndexEnabled,
        defaultPlayerState,
    ) { base, cacheMap, lyricIndexEnabled, defaultPlayer ->
        SettingsUiState(
            isInitialized = true,
            defaultPlayerComponent = defaultPlayer.component,
            defaultPlayerLabel = defaultPlayer.label,
            lyricFormat = base.lyric.format,
            romaEnabled = base.lyric.showRomanization,
            lyricLineOrder = base.lyric.normalizedLineOrder,
            translationEnabled = base.lyric.showTranslation,
            separator = base.search.separator.toArtistSeparator(),
            searchSourceOrder = base.search.searchSourceOrder,
            enabledSearchSources = base.search.enabledSearchSources,
            searchPageSize = base.search.searchPageSize,
            searchSourceTabStyle = base.search.searchSourceTabStyle,
            showAllSearchResultFields = base.search.showAllSearchResultFields,
            themeMode = base.theme.themeMode,
            lyricIndexEnabled = lyricIndexEnabled,
            ignoreShortAudio = base.ignoreShortAudio,
            monetEnable = base.theme.monetEnable,
            floatingBottomBarEnabled = base.floatingBottomBarEnabled,
            barBlurEnabled = base.barBlurEnabled,
            floatingBarEffect = base.floatingBarEffect,
            keyColor = base.theme.keyColor,
            categorizedCacheSize = cacheMap,
            onlyTranslationIfAvailable = base.lyric.onlyTranslationIfAvailable,
            totalCacheSize = cacheMap.values.sum(),
            removeEmptyLines = base.lyric.removeEmptyLines,
            conversionMode = base.lyric.conversionMode,
            lyricsTagLineKeywords = base.lyricsTagLineKeywords,
            metadataFieldWriteRules = base.metadataFieldRules,
            replayGainTargetLoudness = base.replayGainTargetLoudness,
            replayGainPeakMode = base.replayGainPeakMode
        )
    }

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

    fun setLyricLineOrder(order: List<LyricLineTrack>) {
        viewModelScope.launch { settingsRepository.saveLyricLineOrder(order) }
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

    fun setFloatingBottomBarEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveFloatingBottomBarEnabled(enabled)
        }
    }

    fun setBarBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveBarBlurEnabled(enabled)
        }
    }

    fun setFloatingBarEffect(effect: FloatingBarEffect) {
        viewModelScope.launch {
            settingsRepository.saveFloatingBarEffect(effect)
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
    fun setLyricsTagLineKeywords(keywords: List<String>) {
        viewModelScope.launch {
            settingsRepository.saveLyricsTagLineKeywords(keywords)
        }
    }

    fun addNonLyricsContentRule(rule: String): Boolean {
        val normalized = normalizeNonLyricsContentRule(rule) ?: return false
        val current = uiState.value.lyricsTagLineKeywords
        if (current.any { it.equals(normalized, ignoreCase = true) }) return false
        setLyricsTagLineKeywords(current + normalized)
        return true
    }

    fun updateNonLyricsContentRule(oldRule: String, newRule: String): Boolean {
        val normalized = normalizeNonLyricsContentRule(newRule) ?: return false
        val current = uiState.value.lyricsTagLineKeywords
        if (current.any { !it.equals(oldRule, ignoreCase = false) && it.equals(normalized, ignoreCase = true) }) {
            return false
        }
        setLyricsTagLineKeywords(current.map { if (it == oldRule) normalized else it })
        return true
    }

    fun removeNonLyricsContentRule(rule: String) {
        setLyricsTagLineKeywords(uiState.value.lyricsTagLineKeywords.filterNot { it == rule })
    }

    fun resetNonLyricsContentRules() {
        setLyricsTagLineKeywords(LyricsProcessingOptions.DefaultTagLineKeywords)
    }

    private fun normalizeNonLyricsContentRule(input: String): String? {
        val value = input.trim()
        return value.takeIf { it.isNotEmpty() && !it.contains('\n') && !it.contains('\r') }
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
    fun setLyricIndexEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveLyricIndexEnabled(enabled)
        }
    }

    fun setIgnoreShortAudio(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveIgnoreShortAudio(enabled)
        }
    }

    fun setReplayGainTargetLoudness(loudness: Double) {
        viewModelScope.launch {
            settingsRepository.saveReplayGainTargetLoudness(loudness)
        }
    }

    fun setReplayGainPeakMode(mode: ReplayGainPeakMode) {
        viewModelScope.launch {
            settingsRepository.saveReplayGainPeakMode(mode)
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

    fun setSearchSourceTabStyle(style: SearchSourceTabStyle) {
        viewModelScope.launch {
            settingsRepository.saveSearchSourceTabStyle(style)
        }
    }

    fun setShowAllSearchResultFields(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveShowAllSearchResultFields(enabled)
        }
    }

    fun setMetadataFieldWriteRules(rules: List<PluginMetadataFieldWriteRule>) {
        viewModelScope.launch {
            settingsRepository.saveMetadataFieldWriteRules(rules)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.saveThemeMode(mode)
        }
    }

    fun exportSettings(context: Context, uri: Uri? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            var destination = uri
            try {
                val jsonString = settingsRepository.exportSettings()
                val bytes = jsonString.toByteArray(Charsets.UTF_8)
                destination = if (uri == null) {
                    DownloadsFileWriter.write(
                        context = context,
                        fileName = "lyrico_settings_backup_${System.currentTimeMillis()}.json",
                        mimeType = "application/json",
                        bytes = bytes,
                    )
                } else {
                    requireNotNull(context.contentResolver.openOutputStream(uri)) { "Settings export returned no output stream: $uri" }.use {
                        it.write(bytes)
                    }
                    uri
                }
                appLogRepository.recordOperation(
                    type = AppLogType.APP,
                    tag = TAG,
                    stage = "Settings exported",
                    detail = "uri=$destination\nbytes=${bytes.size}",
                    level = AppLogLevel.INFO,
                )
                _events.emit(SettingsEvent.ShowToast(UiMessage.StringResource(
                    if (uri == null) R.string.settings_export_downloads_success else R.string.export_success
                )))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                appLogRepository.recordOperation(
                    type = AppLogType.APP,
                    tag = TAG,
                    stage = "Failed to export settings",
                    detail = "uri=$destination\ndownloads=${uri == null}\n${e.stackTraceToString()}",
                    level = AppLogLevel.ERROR,
                )
                _events.emit(SettingsEvent.ShowToast(UiMessage.StringResource(R.string.export_failed, e.message ?: "Unknown error")))
            }
        }
    }

    fun importSettings(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = requireNotNull(context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }) { "Settings import returned no input stream: $uri" }
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
            } catch (e: Exception) {
                e.printStackTrace()
                appLogRepository.logException(
                    type = AppLogType.APP,
                    tag = TAG,
                    message = "Failed to import settings: uri=$uri",
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

