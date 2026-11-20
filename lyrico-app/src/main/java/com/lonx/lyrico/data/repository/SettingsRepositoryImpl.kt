package com.lonx.lyrico.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lonx.lyrico.data.model.batch.BatchMatchConfig
import com.lonx.lyrico.data.model.batch.BatchMatchConfigDefaults
import com.lonx.lyrico.data.model.filename.CharacterMappingConfig
import com.lonx.lyrico.data.model.filename.CharacterMappingDefaults
import com.lonx.lyrico.data.model.text.ConversionMode
import com.lonx.lyrico.data.model.appearance.FloatingBarEffect
import com.lonx.lyrico.data.model.replaygain.ReplayGainPeakMode
import com.lonx.lyrico.data.model.replaygain.ReplayGainSettings
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricLineTrack
import com.lonx.lyrico.data.model.lyrics.DefaultLyricLineOrder
import com.lonx.lyrico.data.model.lyrics.normalizedLyricLineOrder
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.LyricsProcessingOptions
import com.lonx.lyrico.data.model.log.LogRetentionOption
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldWriteRule
import com.lonx.lyrico.data.model.search.SearchConfig
import com.lonx.lyrico.data.model.search.SearchSourceTabStyle
import com.lonx.lyrico.data.dto.settings.SettingsBackup
import com.lonx.lyrico.data.dto.settings.SettingsBackupMapper
import com.lonx.lyrico.data.model.appearance.ThemeConfig
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.data.model.appearance.ThemeMode
import com.lonx.lyrico.data.model.library.AlbumSortBy
import com.lonx.lyrico.data.model.library.AlbumSortInfo
import com.lonx.lyrico.data.model.library.ArtistSortBy
import com.lonx.lyrico.data.model.library.ArtistSortInfo
import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.appearance.ThemeColor
import com.lonx.lyrico.data.model.library.SongSortBy
import com.lonx.lyrico.data.model.library.SongSortInfo
import com.lonx.lyrico.data.model.library.SortOrder
import com.lonx.lyrico.data.model.search.source.SourceRuntimeConfig
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.MetadataWriteMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.first

internal val Context.settingsDataStore by preferencesDataStore(name = "settings")

internal fun decodeArtistPosterFolder(value: String?): String? =
    runCatching { Json.decodeFromString<List<String>>(value ?: "[]") }
        .getOrDefault(emptyList())
        .firstOrNull()

internal fun encodeArtistPosterFolder(uri: String?): String =
    Json.encodeToString(listOfNotNull(uri))

object SettingsDefaults {
    const val MONET_ENABLE: Boolean = false
    const val FLOATING_BOTTOM_BAR_ENABLED: Boolean = true
    val FLOATING_BAR_EFFECT = FloatingBarEffect.NONE
    const val BAR_BLUR_ENABLED = false
    val KEY_THEME_COLOR = null
    val CONVERSION_MODE = ConversionMode.NONE
    const val RENAME_FORMAT = "@1 - @2"

    val LYRIC_FORMAT = LyricFormat.VERBATIM_LRC
    val SORT_BY = SongSortBy.TITLE
    val SORT_ORDER = SortOrder.ASC
    val ALBUM_SORT_BY = AlbumSortBy.NAME
    val ALBUM_SORT_ORDER = SortOrder.ASC
    val ARTIST_SORT_BY = ArtistSortBy.NAME
    val ARTIST_SORT_ORDER = SortOrder.ASC
    const val ALBUM_GRID_COLUMNS = 2
    const val SEPARATOR = "/"
    val LYRIC_LINE_ORDER = DefaultLyricLineOrder
    const val ROMA_ENABLED = true
    const val TRANSLATION_ENABLED = true
    const val CHECK_UPDATE_ENABLED = true
    const val IGNORE_SHORT_AUDIO = true
    const val ONLY_TRANSLATION_IF_AVAILABLE = false
    const val REMOVE_EMPTY_LINES = true
    val LYRICS_TAG_LINE_KEYWORDS = LyricsProcessingOptions.DefaultTagLineKeywords
    const val LIMIT_LYRICS_INPUT_LINES = false
    val LOG_RETENTION_OPTION = LogRetentionOption.THIRTY_DAYS
    const val REPLAY_GAIN_TARGET_LOUDNESS = -18.0
    val REPLAY_GAIN_PEAK_MODE = ReplayGainPeakMode.SAMPLE_PEAK

    val SEARCH_SOURCE_ORDER = emptyList<String>()
    val DEFAULT_ENABLED_SEARCH_SOURCES = emptySet<String>()
    const val SEARCH_PAGE_SIZE = 10
    val SEARCH_SOURCE_TAB_STYLE = SearchSourceTabStyle.ICON_AND_TEXT
    const val SHOW_ALL_SEARCH_RESULT_FIELDS = false

    val THEME_MODE = ThemeMode.AUTO
}

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {
    private val proActivatedKey = booleanPreferencesKey("pro_activated")
    override val proActivated: Flow<Boolean> = context.settingsDataStore.data.map {
        it[proActivatedKey] ?: false
    }

    override suspend fun activatePro() {
        context.settingsDataStore.edit { it[proActivatedKey] = true }
    }

    override suspend fun deactivatePro() {
        context.settingsDataStore.edit { it[proActivatedKey] = false }
    }

    private val artistPosterFoldersKey = stringPreferencesKey("artist_poster_folders")
    private val artistPosterRevisionKey = longPreferencesKey("artist_poster_revision")
    override val artistPosterRevision: Flow<Long> = context.settingsDataStore.data.map {
        it[artistPosterRevisionKey] ?: 0L
    }

    override suspend fun refreshArtistPosters() {
        context.settingsDataStore.edit {
            it[artistPosterRevisionKey] = (it[artistPosterRevisionKey] ?: 0L) + 1L
        }
    }
    override val artistPosterFolder: Flow<String?> = context.settingsDataStore.data.map {
        decodeArtistPosterFolder(it[artistPosterFoldersKey])
    }

    override suspend fun setArtistPosterFolder(uri: String) {
        context.settingsDataStore.edit {
            it[artistPosterFoldersKey] = encodeArtistPosterFolder(uri)
        }
    }

    override suspend fun clearArtistPosterFolder() {
        context.settingsDataStore.edit {
            it[artistPosterFoldersKey] = encodeArtistPosterFolder(null)
        }
    }

    private val jsonFormatter = Json {
        ignoreUnknownKeys = true // 允许 JSON 中包含当前版本未知的字段
        prettyPrint = true       // 导出的 JSON 格式化，易于阅读
        encodeDefaults = true    // 即使是默认值也编码
    }

    private val backupMapper = SettingsBackupMapper(jsonFormatter)

    override val lyricFormat: Flow<LyricFormat>
        get() = context.settingsDataStore.data.map { preferences ->
            try {
                LyricFormat.valueOf(
                    preferences[SettingsPreferenceKeys.LYRIC_FORMAT] ?: SettingsDefaults.LYRIC_FORMAT.name
                )
            } catch (e: Exception) {
                SettingsDefaults.LYRIC_FORMAT
            }
        }

    override val sortInfo: Flow<SongSortInfo>
        get() = context.settingsDataStore.data.map { preferences ->
            val sortBy = try {
                SongSortBy.valueOf(
                    preferences[SettingsPreferenceKeys.SORT_BY] ?: SettingsDefaults.SORT_BY.name
                )
            } catch (e: Exception) {
                SettingsDefaults.SORT_BY
            }

            val sortOrder = try {
                SortOrder.valueOf(
                    preferences[SettingsPreferenceKeys.SORT_ORDER] ?: SettingsDefaults.SORT_ORDER.name
                )
            } catch (e: Exception) {
                SettingsDefaults.SORT_ORDER
            }

            SongSortInfo(sortBy, sortOrder)
        }

    override val albumSortInfo: Flow<AlbumSortInfo>
        get() = context.settingsDataStore.data.map { preferences ->
            val sortBy = runCatching {
                AlbumSortBy.valueOf(
                    preferences[SettingsPreferenceKeys.ALBUM_SORT_BY]
                        ?: SettingsDefaults.ALBUM_SORT_BY.name
                )
            }.getOrDefault(SettingsDefaults.ALBUM_SORT_BY)

            val sortOrder = runCatching {
                SortOrder.valueOf(
                    preferences[SettingsPreferenceKeys.ALBUM_SORT_ORDER]
                        ?: SettingsDefaults.ALBUM_SORT_ORDER.name
                )
            }.getOrDefault(SettingsDefaults.ALBUM_SORT_ORDER)

            AlbumSortInfo(sortBy, sortOrder)
        }

    override val artistSortInfo: Flow<ArtistSortInfo>
        get() = context.settingsDataStore.data.map { preferences ->
            val sortBy = runCatching {
                ArtistSortBy.valueOf(
                    preferences[SettingsPreferenceKeys.ARTIST_SORT_BY]
                        ?: SettingsDefaults.ARTIST_SORT_BY.name
                )
            }.getOrDefault(SettingsDefaults.ARTIST_SORT_BY)

            val sortOrder = runCatching {
                SortOrder.valueOf(
                    preferences[SettingsPreferenceKeys.ARTIST_SORT_ORDER]
                        ?: SettingsDefaults.ARTIST_SORT_ORDER.name
                )
            }.getOrDefault(SettingsDefaults.ARTIST_SORT_ORDER)

            ArtistSortInfo(sortBy, sortOrder)
        }

    override val albumGridColumns: Flow<Int>
        get() = context.settingsDataStore.data.map { preferences ->
            val columns = preferences[SettingsPreferenceKeys.ALBUM_GRID_COLUMNS]
                ?: SettingsDefaults.ALBUM_GRID_COLUMNS
            columns.coerceIn(2, 4)
        }

    override val separator: Flow<String>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.SEPARATOR] ?: SettingsDefaults.SEPARATOR
        }

    override val romaEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.ROMA_ENABLED] ?: SettingsDefaults.ROMA_ENABLED
        }

    override val lyricLineOrder: Flow<List<LyricLineTrack>>
        get() = context.settingsDataStore.data.map { preferences ->
            decodeLyricLineOrder(preferences[SettingsPreferenceKeys.LYRIC_LINE_ORDER])
        }

    override val translationEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.TRANSLATION_ENABLED] ?: SettingsDefaults.TRANSLATION_ENABLED
        }

    override val checkUpdateEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.CHECK_UPDATE_ENABLED]
                ?: SettingsDefaults.CHECK_UPDATE_ENABLED
        }

    override val lyricIndexEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.LYRIC_INDEX_ENABLED] ?: false
        }

    override val defaultPlayerComponent: Flow<String?>
        get() = context.settingsDataStore.data.map { it[SettingsPreferenceKeys.DEFAULT_PLAYER_COMPONENT] }

    override suspend fun saveDefaultPlayerComponent(component: String?) {
        context.settingsDataStore.edit { preferences ->
            if (component == null) preferences.remove(SettingsPreferenceKeys.DEFAULT_PLAYER_COMPONENT)
            else preferences[SettingsPreferenceKeys.DEFAULT_PLAYER_COMPONENT] = component
        }
    }

    override val ignoreShortAudio: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.IGNORE_SHORT_AUDIO] ?: SettingsDefaults.IGNORE_SHORT_AUDIO
        }

    override val replayGainSettings: Flow<ReplayGainSettings>
        get() = context.settingsDataStore.data.map { preferences ->
            ReplayGainSettings(
                targetLoudness = preferences[SettingsPreferenceKeys.REPLAY_GAIN_TARGET_LOUDNESS]
                    ?: SettingsDefaults.REPLAY_GAIN_TARGET_LOUDNESS,
                peakMode = runCatching {
                    ReplayGainPeakMode.valueOf(
                        preferences[SettingsPreferenceKeys.REPLAY_GAIN_PEAK_MODE]
                            ?: SettingsDefaults.REPLAY_GAIN_PEAK_MODE.name
                    )
                }.getOrDefault(SettingsDefaults.REPLAY_GAIN_PEAK_MODE)
            )
        }

    override val searchSourceOrder: Flow<List<String>>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.SEARCH_SOURCE_ORDER].csvToIds()
        }

    override val enabledSearchSources: Flow<Set<String>>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.ENABLED_SEARCH_SOURCES].csvToIds().toSet()
        }

    override val searchPageSize: Flow<Int>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.SEARCH_PAGE_SIZE] ?: SettingsDefaults.SEARCH_PAGE_SIZE
        }

    override val searchSourceTabStyle: Flow<SearchSourceTabStyle>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.SEARCH_SOURCE_TAB_STYLE]
                ?.let { styleName ->
                    runCatching { SearchSourceTabStyle.valueOf(styleName) }.getOrNull()
                }
                ?: SettingsDefaults.SEARCH_SOURCE_TAB_STYLE
        }

    override val showAllSearchResultFields: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.SHOW_ALL_SEARCH_RESULT_FIELDS]
                ?: SettingsDefaults.SHOW_ALL_SEARCH_RESULT_FIELDS
        }

    override val uiEngine: Flow<UiEngine>
        get() = context.settingsDataStore.data.map { preferences ->
            UiEngine.fromValue(preferences[SettingsPreferenceKeys.UI_ENGINE])
        }

    override suspend fun saveUiEngine(engine: UiEngine) {
        context.settingsDataStore.edit { it[SettingsPreferenceKeys.UI_ENGINE] = engine.value }
    }

    override val themeMode: Flow<ThemeMode>
        get() = context.settingsDataStore.data.map { preferences ->
            val modeName = preferences[SettingsPreferenceKeys.THEME_MODE]
            if (modeName.isNullOrBlank()) {
                SettingsDefaults.THEME_MODE
            } else {
                try {
                    ThemeMode.valueOf(modeName)
                } catch (e: IllegalArgumentException) {
                    SettingsDefaults.THEME_MODE
                }
            }
        }
    override val keyColor: Flow<ThemeColor>
        get() = context.settingsDataStore.data.map { preferences ->
            ThemeColor.fromArgb(preferences[SettingsPreferenceKeys.KEY_THEME_COLOR])
        }
    override val monetEnable: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.MONET_ENABLE] ?: false
        }
    override val floatingBottomBarEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.FLOATING_BOTTOM_BAR_ENABLED]
                ?: SettingsDefaults.FLOATING_BOTTOM_BAR_ENABLED
        }
    override val barBlurEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences.resolveBarBlurEnabled()
        }
    override val floatingBarEffect: Flow<FloatingBarEffect>
        get() = context.settingsDataStore.data.map { it.resolveFloatingBarEffect() }

    override val conversionMode: Flow<ConversionMode>
        get() = context.settingsDataStore.data.map { preferences ->
            val modeName = preferences[SettingsPreferenceKeys.CONVERSION_MODE]
            if (modeName.isNullOrBlank()) {
                ConversionMode.NONE
            } else {
                try {
                    ConversionMode.valueOf(modeName)
                } catch (e: IllegalArgumentException) {
                    ConversionMode.NONE
                }
            }
        }

    override val onlyTranslationIfAvailable: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.ONLY_TRANSLATION_IF_AVAILABLE]
                ?: SettingsDefaults.ONLY_TRANSLATION_IF_AVAILABLE
        }

    override val removeEmptyLines: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.REMOVE_EMPTY_LINES] ?: SettingsDefaults.REMOVE_EMPTY_LINES
        }
    override val lyricsTagLineKeywords: Flow<List<String>>
        get() = context.settingsDataStore.data.map { preferences ->
            jsonFormatter.decodeLyricsTagLineKeywords(preferences[SettingsPreferenceKeys.LYRICS_TAG_LINE_KEYWORDS])
        }
    override val limitLyricsInputLines: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.LIMIT_LYRICS_INPUT_LINES]
                ?: SettingsDefaults.LIMIT_LYRICS_INPUT_LINES
        }
    override val logRetentionOption: Flow<LogRetentionOption>
        get() = context.settingsDataStore.data.map { preferences ->
            val optionName = preferences[SettingsPreferenceKeys.LOG_RETENTION_OPTION]
            if (optionName.isNullOrBlank()) {
                SettingsDefaults.LOG_RETENTION_OPTION
            } else {
                runCatching { LogRetentionOption.valueOf(optionName) }
                    .getOrDefault(SettingsDefaults.LOG_RETENTION_OPTION)
            }
        }

    override val renameFormat: Flow<String>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.RENAME_FORMAT] ?: SettingsDefaults.RENAME_FORMAT
        }
    override suspend fun getLastScanTime(): Long {
        return context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.LAST_SCAN_TIME] ?: 0L
        }.first()
    }

    private val baseConfigFlow =
        combine(
            lyricFormat,
            romaEnabled,
            translationEnabled,
            lyricLineOrder
        ) { format, roma, translation, lineOrder ->
            LyricRenderConfig(
                format = format,
                showRomanization = roma,
                showTranslation = translation,
                lineOrder = lineOrder
            )
        }

    private val extraConfigFlow =
        combine(
            onlyTranslationIfAvailable,
            removeEmptyLines,
            conversionMode
        ) { onlyTranslation, removeEmptyLines, conversionMode ->
            Triple(onlyTranslation, removeEmptyLines, conversionMode)
        }

    override val lyricRenderConfigFlow =
        combine(baseConfigFlow, extraConfigFlow) { base, extra ->
            base.copy(
                onlyTranslationIfAvailable = extra.first,
                removeEmptyLines = extra.second,
                conversionMode = extra.third
            )
        }

    private val searchDisplayConfigFlow = combine(
        searchPageSize,
        searchSourceTabStyle,
        showAllSearchResultFields
    ) { pageSize, tabStyle, showAllFields ->
        Triple(pageSize, tabStyle, showAllFields)
    }

    override val searchConfigFlow: Flow<SearchConfig> =
        combine(
            separator,
            searchSourceOrder,
            enabledSearchSources,
            searchDisplayConfigFlow
        ) { sep, order, enabled, displayConfig ->
            SearchConfig(
                separator = sep,
                searchSourceOrder = order,
                enabledSearchSources = enabled,
                searchPageSize = displayConfig.first,
                searchSourceTabStyle = displayConfig.second,
                showAllSearchResultFields = displayConfig.third
            )
        }

    override val themeConfigFlow: Flow<ThemeConfig> =
        combine(themeMode, monetEnable, keyColor) { theme, monet, keyColor ->
            ThemeConfig(
                themeMode = theme,
                monetEnable = monet,
                keyColor = keyColor
            )
        }

    override suspend fun saveOnlyTranslationIfAvailable(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.ONLY_TRANSLATION_IF_AVAILABLE] = enabled
        }
    }

    override suspend fun saveLyricDisplayMode(mode: LyricFormat) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.LYRIC_FORMAT] = mode.name
        }
    }

    override suspend fun saveSortInfo(sortInfo: SongSortInfo) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.SORT_BY] = sortInfo.sortBy.name
            preferences[SettingsPreferenceKeys.SORT_ORDER] = sortInfo.order.name
        }
    }

    override val sourceSettingsByIdFlow: Flow<Map<String, SourceRuntimeConfig>>
        get() = context.settingsDataStore.data.map { preferences ->
            jsonFormatter.decodeSourceSettingsStore(preferences[SettingsPreferenceKeys.SOURCE_SETTINGS])
                .values
                .mapKeys { (sourceId, _) -> sourceId.toStableSourceId() }
                .mapValues { (_, values) -> SourceRuntimeConfig(values) }
        }

    override suspend fun saveAlbumSortInfo(sortInfo: AlbumSortInfo) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.ALBUM_SORT_BY] = sortInfo.sortBy.name
            preferences[SettingsPreferenceKeys.ALBUM_SORT_ORDER] = sortInfo.order.name
        }
    }

    override suspend fun saveArtistSortInfo(sortInfo: ArtistSortInfo) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.ARTIST_SORT_BY] = sortInfo.sortBy.name
            preferences[SettingsPreferenceKeys.ARTIST_SORT_ORDER] = sortInfo.order.name
        }
    }

    override suspend fun saveAlbumGridColumns(columns: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.ALBUM_GRID_COLUMNS] = columns.coerceIn(2, 4)
        }
    }

    override suspend fun saveSeparator(separator: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.SEPARATOR] = separator
        }
    }

    override suspend fun saveRomaEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.ROMA_ENABLED] = enabled
        }
    }

    override suspend fun saveLyricLineOrder(order: List<LyricLineTrack>) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.LYRIC_LINE_ORDER] =
                order.normalizedLyricLineOrder().joinToString(",") { it.name }
        }
    }

    override suspend fun saveCheckUpdateEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.CHECK_UPDATE_ENABLED] = enabled
        }
    }

    override suspend fun saveTranslationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.TRANSLATION_ENABLED] = enabled
        }
    }

    override suspend fun saveLyricIndexEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.LYRIC_INDEX_ENABLED] = enabled
        }
    }

    override suspend fun saveIgnoreShortAudio(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.IGNORE_SHORT_AUDIO] = enabled
        }
    }

    override suspend fun saveReplayGainTargetLoudness(loudness: Double) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.REPLAY_GAIN_TARGET_LOUDNESS] = loudness
        }
    }

    override suspend fun saveReplayGainPeakMode(mode: ReplayGainPeakMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.REPLAY_GAIN_PEAK_MODE] = mode.name
        }
    }

    override suspend fun getReplayGainSettings(): ReplayGainSettings =
        replayGainSettings.first()

    override suspend fun saveLastScanTime(time: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.LAST_SCAN_TIME] = time
        }
    }

    override suspend fun saveSearchSourceOrder(sources: List<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.SEARCH_SOURCE_ORDER] =
                sources.idsToCsv()
        }
    }

    override suspend fun saveEnabledSearchSources(sources: Set<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.ENABLED_SEARCH_SOURCES] = sources.idsToCsv()
        }
    }

    override suspend fun saveSearchPageSize(size: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.SEARCH_PAGE_SIZE] = size
        }
    }

    override suspend fun saveSearchSourceTabStyle(style: SearchSourceTabStyle) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.SEARCH_SOURCE_TAB_STYLE] = style.name
        }
    }

    override suspend fun saveShowAllSearchResultFields(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.SHOW_ALL_SEARCH_RESULT_FIELDS] = enabled
        }
    }

    override suspend fun saveConversionMode(mode: ConversionMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.CONVERSION_MODE] = mode.name
        }
    }

    override suspend fun saveThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.THEME_MODE] = mode.name
        }
    }
    override suspend fun saveMonetEnable(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.MONET_ENABLE] = enabled
        }
    }

    override suspend fun saveFloatingBottomBarEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.FLOATING_BOTTOM_BAR_ENABLED] = enabled
        }
    }

    override suspend fun saveBarBlurEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.BAR_BLUR_ENABLED] = enabled
        }
    }

    override suspend fun saveFloatingBarEffect(effect: FloatingBarEffect) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.FLOATING_BAR_EFFECT] = effect.name
        }
    }

    override suspend fun saveKeyColor(selectedKeyColor: ThemeColor) {
        context.settingsDataStore.edit { preferences ->
            if (selectedKeyColor.argb == null) {
                // 如果选了“系统默认”，直接移除保存的值
                preferences.remove(SettingsPreferenceKeys.KEY_THEME_COLOR)
            } else {
                preferences[SettingsPreferenceKeys.KEY_THEME_COLOR] = selectedKeyColor.argb
            }
        }
    }
    override suspend fun saveRemoveEmptyLines(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.REMOVE_EMPTY_LINES] = enabled
        }
    }

    override suspend fun saveLyricsTagLineKeywords(keywords: List<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.LYRICS_TAG_LINE_KEYWORDS] =
                jsonFormatter.encodeToString(normalizeLyricsTagLineKeywords(keywords))
        }
    }

    override suspend fun saveLimitLyricsInputLines(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.LIMIT_LYRICS_INPUT_LINES] = enabled
        }
    }

    override suspend fun saveLogRetentionOption(option: LogRetentionOption) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.LOG_RETENTION_OPTION] = option.name
        }
    }

    override suspend fun getLyricRenderConfig(): LyricRenderConfig {
        val prefs = context.settingsDataStore.data.first()

        val format = LyricFormat.valueOf(
            prefs[SettingsPreferenceKeys.LYRIC_FORMAT]
                ?: SettingsDefaults.LYRIC_FORMAT.name
        )

        val roma = prefs[SettingsPreferenceKeys.ROMA_ENABLED] ?: SettingsDefaults.ROMA_ENABLED

        val showTranslation = prefs[SettingsPreferenceKeys.TRANSLATION_ENABLED] ?: SettingsDefaults.TRANSLATION_ENABLED

        val removeEmptyLines = prefs[SettingsPreferenceKeys.REMOVE_EMPTY_LINES] ?: SettingsDefaults.REMOVE_EMPTY_LINES
        val onlyTranslationIfAvailable = prefs[SettingsPreferenceKeys.ONLY_TRANSLATION_IF_AVAILABLE] ?: SettingsDefaults.ONLY_TRANSLATION_IF_AVAILABLE
        val conversionMode = ConversionMode.valueOf(
            prefs[SettingsPreferenceKeys.CONVERSION_MODE]
                ?: SettingsDefaults.CONVERSION_MODE.name
        )
        return LyricRenderConfig(
            format = format,
            showRomanization = roma,
            removeEmptyLines = removeEmptyLines,
            showTranslation = showTranslation,
            lineOrder = decodeLyricLineOrder(prefs[SettingsPreferenceKeys.LYRIC_LINE_ORDER]),
            onlyTranslationIfAvailable = onlyTranslationIfAvailable,
            conversionMode = conversionMode
        )
    }

    override suspend fun exportSettings(): String {
        val prefs = context.settingsDataStore.data.first()
        val charMapping = getCharacterMappingConfig()
        val batchMatchConfig = getBatchMatchConfig()
        val artistSplitConfig = getArtistSplitConfig()
        val backup = backupMapper.fromPreferences(
            prefs, charMapping, batchMatchConfig, artistSplitConfig, getMetadataFieldWriteRules()
        )

        return jsonFormatter.encodeToString(backup)
    }

    override suspend fun importSettings(jsonString: String): Boolean {
        return try {
            val backup = jsonFormatter.decodeFromString<SettingsBackup>(jsonString)

            context.settingsDataStore.edit { prefs ->
                backupMapper.restore(backup, prefs)
            }
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            false
        }
    }

    override val characterMappingConfig: Flow<CharacterMappingConfig>
        get() = context.settingsDataStore.data.map { preferences ->
            val configJson = preferences[SettingsPreferenceKeys.CHARACTER_MAPPING_CONFIG]
            if (configJson.isNullOrBlank()) {
                CharacterMappingConfig(
                    rules = CharacterMappingDefaults.ALL_BUILTIN_RULES
                )
            } else {
                try {
                    jsonFormatter.decodeFromString<CharacterMappingConfig>(configJson)
                } catch (e: Exception) {
                    CharacterMappingConfig(
                        rules = CharacterMappingDefaults.ALL_BUILTIN_RULES
                    )
                }
            }
        }

    override val artistSplitConfigFlow: Flow<ArtistSplitConfig>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.ARTIST_SPLIT_CONFIG]
                ?.let { json ->
                    runCatching {
                        jsonFormatter.decodeFromString<ArtistSplitConfig>(json)
                    }.getOrNull()
                }
                ?: ArtistSplitConfig()
        }

    override val batchMatchConfig: Flow<BatchMatchConfig>
        get() = context.settingsDataStore.data.map { preferences ->
            val configJson = preferences[SettingsPreferenceKeys.BATCH_MATCH_CONFIG]
            if (configJson.isNullOrBlank()) {
                BatchMatchConfigDefaults.DEFAULT_CONFIG
            } else {
                decodeBatchMatchConfig(configJson)
            }
        }

    override val metadataFieldWriteRules: Flow<List<PluginMetadataFieldWriteRule>>
        get() = context.settingsDataStore.data.map { preferences ->
            val rulesJson = preferences[SettingsPreferenceKeys.METADATA_FIELD_WRITE_RULES]
            if (!rulesJson.isNullOrBlank()) {
                decodeMetadataFieldWriteRules(rulesJson)
            } else {
                emptyList()
            }
        }

    override suspend fun saveCharacterMappingConfig(config: CharacterMappingConfig) {
        context.settingsDataStore.edit { preferences ->
            val configJson = jsonFormatter.encodeToString(config)
            preferences[SettingsPreferenceKeys.CHARACTER_MAPPING_CONFIG] = configJson
        }
    }

    override suspend fun saveRenameFormat(format: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.RENAME_FORMAT] = format
        }
    }
    override suspend fun saveBatchMatchConfig(config: BatchMatchConfig) {
        context.settingsDataStore.edit { preferences ->
            val configJson = jsonFormatter.encodeToString(config)
            preferences[SettingsPreferenceKeys.BATCH_MATCH_CONFIG] = configJson
        }
    }

    override suspend fun saveMetadataFieldWriteRules(rules: List<PluginMetadataFieldWriteRule>) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.METADATA_FIELD_WRITE_RULES] =
                jsonFormatter.encodeToString(rules)
        }
    }

    override suspend fun saveSourceSettings(sourceId: String, values: Map<String, String>) {
        context.settingsDataStore.edit { preferences ->
            val currentStore = jsonFormatter.decodeSourceSettingsStore(preferences[SettingsPreferenceKeys.SOURCE_SETTINGS])
            val newStore = currentStore.copy(
                values = currentStore.values + (sourceId.toStableSourceId() to values)
            )
            preferences[SettingsPreferenceKeys.SOURCE_SETTINGS] = jsonFormatter.encodeToString(newStore)
        }
    }

    override suspend fun updateCharacterMappingInRule(ruleId: String, charMappings: Map<String, String?>) {
        val currentConfig = characterMappingConfig.first()
        val updatedRules = currentConfig.rules.map { rule ->
            if (rule.id == ruleId) {
                rule.copy(charMappings = charMappings)
            } else rule
        }
        saveCharacterMappingConfig(currentConfig.copy(rules = updatedRules))
    }

    override suspend fun getCharacterMappingConfig(): CharacterMappingConfig {
        return characterMappingConfig.first()
    }
    override suspend fun getBatchMatchConfig(): BatchMatchConfig {
        return batchMatchConfig.first()
    }

    override suspend fun getMetadataFieldWriteRules(): List<PluginMetadataFieldWriteRule> {
        return metadataFieldWriteRules.first()
    }

    override suspend fun getSourceSettings(sourceId: String): SourceRuntimeConfig {
        return sourceSettingsByIdFlow.first()[sourceId.toStableSourceId()] ?: SourceRuntimeConfig()
    }

    override suspend fun removePluginSettings(pluginId: String) {
        val stablePluginId = pluginId.toStableSourceId()
        context.settingsDataStore.edit { preferences ->
            val currentStore = jsonFormatter.decodeSourceSettingsStore(preferences[SettingsPreferenceKeys.SOURCE_SETTINGS])
            preferences[SettingsPreferenceKeys.SOURCE_SETTINGS] = jsonFormatter.encodeToString(
                currentStore.copy(values = currentStore.values - stablePluginId)
            )
            val rules = decodeMetadataFieldWriteRules(
                preferences[SettingsPreferenceKeys.METADATA_FIELD_WRITE_RULES].orEmpty()
            )
            preferences[SettingsPreferenceKeys.METADATA_FIELD_WRITE_RULES] =
                jsonFormatter.encodeToString(rules.filterNot { it.pluginId == stablePluginId })
        }
    }

    override suspend fun saveArtistSplitConfig(config: ArtistSplitConfig) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.ARTIST_SPLIT_CONFIG] = jsonFormatter.encodeToString(config)
            preferences[SettingsPreferenceKeys.LIBRARY_INDEX_VERSION] = 0
            preferences[artistPosterRevisionKey] = (preferences[artistPosterRevisionKey] ?: 0L) + 1L
        }
    }

    override suspend fun getArtistSplitConfig(): ArtistSplitConfig {
        return artistSplitConfigFlow.first()
    }

    override suspend fun getLibraryIndexVersion(): Int {
        return context.settingsDataStore.data.map { preferences ->
            preferences[SettingsPreferenceKeys.LIBRARY_INDEX_VERSION] ?: 0
        }.first()
    }

    override suspend fun saveLibraryIndexVersion(version: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsPreferenceKeys.LIBRARY_INDEX_VERSION] = version
        }
    }

    private fun decodeBatchMatchConfig(configJson: String): BatchMatchConfig {
        return try {
            val root = jsonFormatter.parseToJsonElement(configJson).jsonObject

            val newTargetModes = root["targetModes"]
                ?.jsonObject
                ?.mapNotNull { (targetName, modeElement) ->
                    val target = decodeMetadataFieldTarget(targetName)
                        ?: return@mapNotNull null

                    val mode = decodeMetadataWriteMode(
                        modeElement.jsonPrimitive.contentOrNull
                    ) ?: return@mapNotNull null

                    target to mode
                }
                ?.toMap()

            val legacyTargetModes = root["fields"]
                ?.jsonObject
                ?.mapNotNull { (fieldName, modeElement) ->
                    val target = legacyBatchFieldToTarget(fieldName)
                        ?: return@mapNotNull null

                    val mode = decodeMetadataWriteMode(
                        modeElement.jsonPrimitive.contentOrNull
                    ) ?: return@mapNotNull null

                    target to mode
                }
                ?.toMap()

            val defaultConfig = BatchMatchConfigDefaults.DEFAULT_CONFIG
            val decodedTargetModes = newTargetModes ?: legacyTargetModes

            BatchMatchConfig(
                targetModes = if (decodedTargetModes == null) {
                    defaultConfig.targetModes
                } else {
                    BatchMatchConfigDefaults.BATCH_MATCH_TARGETS.associateWith { target ->
                        decodedTargetModes[target] ?: MetadataWriteMode.DISABLED
                    }
                },
                concurrency = root["concurrency"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.toIntOrNull()
                    ?: defaultConfig.concurrency,
                preferFileName = root["preferFileName"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.toBooleanStrictOrNull()
                    ?: defaultConfig.preferFileName
            )
        } catch (_: Exception) {
            BatchMatchConfigDefaults.DEFAULT_CONFIG
        }
    }

    private fun decodeMetadataFieldTarget(name: String): MetadataFieldTarget? {
        return MetadataFieldTarget.entries.firstOrNull { it.name == name }
    }

    private fun decodeMetadataWriteMode(name: String?): MetadataWriteMode? {
        return name
            ?.let { value -> runCatching { MetadataWriteMode.valueOf(value) }.getOrNull() }
    }

    private fun legacyBatchFieldToTarget(fieldName: String): MetadataFieldTarget? {
        return when (fieldName) {
            "TITLE" -> MetadataFieldTarget.TITLE
            "ARTIST" -> MetadataFieldTarget.ARTIST
            "ALBUM" -> MetadataFieldTarget.ALBUM
            "GENRE" -> MetadataFieldTarget.GENRE
            "DATE" -> MetadataFieldTarget.DATE
            "TRACK_NUMBER" -> MetadataFieldTarget.TRACK_NUMBER
            "LYRICS" -> MetadataFieldTarget.LYRICS
            "COMMENT" -> MetadataFieldTarget.COMMENT
            "COVER" -> MetadataFieldTarget.COVER
            else -> null
        }
    }

    private fun decodeMetadataFieldWriteRules(raw: String): List<PluginMetadataFieldWriteRule> {
        if (raw.isBlank()) return emptyList()
        val normalizedRaw = raw.replace("\"sourceId\"", "\"pluginId\"")
        return runCatching {
            jsonFormatter.parseToJsonElement(normalizedRaw).jsonArray.mapNotNull { element ->
                runCatching {
                    jsonFormatter.decodeFromJsonElement<PluginMetadataFieldWriteRule?>(element)
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun String.toStableSourceId(): String {
        return trim()
    }

}
