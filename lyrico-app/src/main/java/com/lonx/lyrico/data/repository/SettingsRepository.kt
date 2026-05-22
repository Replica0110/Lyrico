package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.CharacterMappingConfig
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.model.LogRetentionOption
import com.lonx.lyrico.data.model.MetadataFieldWriteRule
import com.lonx.lyrico.data.model.SearchConfig
import com.lonx.lyrico.data.model.ThemeConfig
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.model.AlbumSortInfo
import com.lonx.lyrico.data.model.ArtistSortInfo
import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.ui.theme.KeyColor
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import kotlinx.coroutines.flow.Flow


interface SettingsRepository {
    val batchMatchConfig: Flow<BatchMatchConfig>
    val metadataFieldWriteRules: Flow<List<MetadataFieldWriteRule>>
    val sourceSettingsByIdFlow: Flow<Map<String, SourceRuntimeConfig>>

    val renameFormat: Flow<String>

    // Flow properties
    val lyricFormat: Flow<LyricFormat>
    val sortInfo: Flow<SortInfo>
    val albumSortInfo: Flow<AlbumSortInfo>
    val artistSortInfo: Flow<ArtistSortInfo>
    val albumGridColumns: Flow<Int>
    val separator: Flow<String>
    val romaEnabled: Flow<Boolean>

    val conversionMode: Flow<ConversionMode>

    val translationEnabled: Flow<Boolean>
    val checkUpdateEnabled: Flow<Boolean>
    val ignoreShortAudio: Flow<Boolean>
    val searchSourceOrder: Flow<List<String>>
    val enabledSearchSources: Flow<Set<String>>
    val searchPageSize: Flow<Int>
    val themeMode: Flow<ThemeMode>
    val keyColor: Flow<KeyColor>
    val monetEnable: Flow<Boolean>
    val onlyTranslationIfAvailable: Flow<Boolean>
    val removeEmptyLines: Flow<Boolean>
    val limitLyricsInputLines: Flow<Boolean>
    val logRetentionOption: Flow<LogRetentionOption>

    val lyricRenderConfigFlow: Flow<LyricRenderConfig>
    val searchConfigFlow: Flow<SearchConfig>
    val themeConfigFlow: Flow<ThemeConfig>
    val showScrollTopButton : Flow<Boolean>

    val characterMappingConfig: Flow<CharacterMappingConfig>
    val artistSplitConfigFlow: Flow<ArtistSplitConfig>
    // Suspend functions for operations that might block or are one-off
    suspend fun getLastScanTime(): Long
    
    // Save functions
    suspend fun saveLyricDisplayMode(mode: LyricFormat)
    suspend fun saveSortInfo(sortInfo: SortInfo)
    suspend fun saveAlbumSortInfo(sortInfo: AlbumSortInfo)
    suspend fun saveArtistSortInfo(sortInfo: ArtistSortInfo)
    suspend fun saveAlbumGridColumns(columns: Int)
    suspend fun saveSeparator(separator: String)
    suspend fun saveRomaEnabled(enabled: Boolean)
    suspend fun saveConversionMode(mode: ConversionMode)
    suspend fun saveCheckUpdateEnabled(enabled: Boolean)
    suspend fun saveTranslationEnabled(enabled: Boolean)
    suspend fun saveIgnoreShortAudio(enabled: Boolean)
    suspend fun saveLastScanTime(time: Long)
    suspend fun saveSearchSourceOrder(sources: List<String>)
    suspend fun saveEnabledSearchSources(sources: Set<String>)
    suspend fun saveSearchPageSize(size: Int)
    suspend fun saveThemeMode(mode: ThemeMode)
    suspend fun saveKeyColor(selectedKeyColor: KeyColor)
    suspend fun saveMonetEnable(enabled: Boolean)
    suspend fun saveOnlyTranslationIfAvailable(enabled: Boolean)
    suspend fun saveRemoveEmptyLines(enabled: Boolean)
    suspend fun saveLimitLyricsInputLines(enabled: Boolean)
    suspend fun saveLogRetentionOption(option: LogRetentionOption)
    suspend fun saveShowScrollTopButton(enabled: Boolean)
    suspend fun getLyricRenderConfig(): LyricRenderConfig
    suspend fun exportSettings(): String
    suspend fun importSettings(jsonString: String): Boolean
    suspend fun saveBatchMatchConfig(config: BatchMatchConfig)
    suspend fun saveMetadataFieldWriteRules(rules: List<MetadataFieldWriteRule>)
    suspend fun saveSourceSettings(sourceId: String, values: Map<String, String>)
    suspend fun getSourceSettings(sourceId: String): SourceRuntimeConfig
    suspend fun removePluginSettings(pluginId: String)
    suspend fun saveRenameFormat(format: String)
    suspend fun saveCharacterMappingConfig(config: CharacterMappingConfig)
    // 更新指定规则中的字符映射
    suspend fun updateCharacterMappingInRule(ruleId: String, charMappings: Map<String, String?>)
    suspend fun getCharacterMappingConfig(): CharacterMappingConfig
    suspend fun getBatchMatchConfig(): BatchMatchConfig
    suspend fun getMetadataFieldWriteRules(): List<MetadataFieldWriteRule>
    suspend fun saveArtistSplitConfig(config: ArtistSplitConfig)
    suspend fun getArtistSplitConfig(): ArtistSplitConfig
    suspend fun getLibraryIndexVersion(): Int
    suspend fun saveLibraryIndexVersion(version: Int)
}
