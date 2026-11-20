package com.lonx.lyrico.data.dto.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.lonx.lyrico.data.editfield.EditFieldConfigJson
import com.lonx.lyrico.data.editfield.EditFieldConfigRepository
import com.lonx.lyrico.data.model.appearance.FloatingBarEffect
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.batch.BatchMatchConfig
import com.lonx.lyrico.data.model.filename.CharacterMappingConfig
import com.lonx.lyrico.data.model.log.LogRetentionOption
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldWriteRule
import com.lonx.lyrico.data.model.replaygain.ReplayGainPeakMode
import com.lonx.lyrico.data.model.search.SearchSourceTabStyle
import com.lonx.lyrico.data.repository.SettingsDefaults
import com.lonx.lyrico.data.repository.SettingsPreferenceKeys
import com.lonx.lyrico.data.repository.csvToIds
import com.lonx.lyrico.data.repository.decodeLyricLineOrder
import com.lonx.lyrico.data.repository.decodeLyricsTagLineKeywords
import com.lonx.lyrico.data.repository.decodeSourceSettingsStore
import com.lonx.lyrico.data.repository.idsToCsv
import com.lonx.lyrico.data.repository.normalizeLyricsTagLineKeywords
import com.lonx.lyrico.data.repository.resolveBarBlurEnabled
import com.lonx.lyrico.data.repository.resolveFloatingBarEffect
import kotlinx.serialization.json.Json

internal class SettingsBackupMapper(private val json: Json) {
    fun fromPreferences(
        prefs: Preferences,
        characterMapping: CharacterMappingConfig,
        batchMatchConfig: BatchMatchConfig,
        artistSplitConfig: ArtistSplitConfig,
        metadataFieldWriteRules: List<PluginMetadataFieldWriteRule>,
    ): SettingsBackup {
        return SettingsBackup(
            removeEmptyLines = prefs[SettingsPreferenceKeys.REMOVE_EMPTY_LINES]
                ?: SettingsDefaults.REMOVE_EMPTY_LINES,
            lyricsTagLineKeywords = json.decodeLyricsTagLineKeywords(
                prefs[SettingsPreferenceKeys.LYRICS_TAG_LINE_KEYWORDS]
            ),

            lyricFormat = prefs[SettingsPreferenceKeys.LYRIC_FORMAT]
                ?: SettingsDefaults.LYRIC_FORMAT.name,

            sortBy = prefs[SettingsPreferenceKeys.SORT_BY]
                ?: SettingsDefaults.SORT_BY.name,

            sortOrder = prefs[SettingsPreferenceKeys.SORT_ORDER]
                ?: SettingsDefaults.SORT_ORDER.name,

            albumSortBy = prefs[SettingsPreferenceKeys.ALBUM_SORT_BY]
                ?: SettingsDefaults.ALBUM_SORT_BY.name,

            albumSortOrder = prefs[SettingsPreferenceKeys.ALBUM_SORT_ORDER]
                ?: SettingsDefaults.ALBUM_SORT_ORDER.name,

            artistSortBy = prefs[SettingsPreferenceKeys.ARTIST_SORT_BY]
                ?: SettingsDefaults.ARTIST_SORT_BY.name,

            artistSortOrder = prefs[SettingsPreferenceKeys.ARTIST_SORT_ORDER]
                ?: SettingsDefaults.ARTIST_SORT_ORDER.name,

            albumGridColumns = prefs[SettingsPreferenceKeys.ALBUM_GRID_COLUMNS]
                ?: SettingsDefaults.ALBUM_GRID_COLUMNS,

            separator = prefs[SettingsPreferenceKeys.SEPARATOR]
                ?: SettingsDefaults.SEPARATOR,

            lyricLineOrder = decodeLyricLineOrder(prefs[SettingsPreferenceKeys.LYRIC_LINE_ORDER]).map { it.name },

            romaEnabled = prefs[SettingsPreferenceKeys.ROMA_ENABLED]
                ?: SettingsDefaults.ROMA_ENABLED,

            checkUpdateEnabled = prefs[SettingsPreferenceKeys.CHECK_UPDATE_ENABLED]
                ?: SettingsDefaults.CHECK_UPDATE_ENABLED,

            translationEnabled = prefs[SettingsPreferenceKeys.TRANSLATION_ENABLED]
                ?: SettingsDefaults.TRANSLATION_ENABLED,

            lyricIndexEnabled = prefs[SettingsPreferenceKeys.LYRIC_INDEX_ENABLED] ?: false,
            defaultPlayerComponent = prefs[SettingsPreferenceKeys.DEFAULT_PLAYER_COMPONENT].orEmpty(),
            ignoreShortAudio = prefs[SettingsPreferenceKeys.IGNORE_SHORT_AUDIO]
                ?: SettingsDefaults.IGNORE_SHORT_AUDIO,

            replayGainTargetLoudness = prefs[SettingsPreferenceKeys.REPLAY_GAIN_TARGET_LOUDNESS]
                ?: SettingsDefaults.REPLAY_GAIN_TARGET_LOUDNESS,
            replayGainPeakMode = prefs[SettingsPreferenceKeys.REPLAY_GAIN_PEAK_MODE]
                ?: SettingsDefaults.REPLAY_GAIN_PEAK_MODE.name,

            searchSourceOrder = (prefs[SettingsPreferenceKeys.SEARCH_SOURCE_ORDER] ?: SettingsDefaults.SEARCH_SOURCE_ORDER.idsToCsv()).csvToIds(),

            enabledSearchSources = (prefs[SettingsPreferenceKeys.ENABLED_SEARCH_SOURCES] ?: SettingsDefaults.SEARCH_SOURCE_ORDER.idsToCsv()).csvToIds(),

            searchPageSize = prefs[SettingsPreferenceKeys.SEARCH_PAGE_SIZE]
                ?: SettingsDefaults.SEARCH_PAGE_SIZE,
            searchSourceTabStyle = prefs[SettingsPreferenceKeys.SEARCH_SOURCE_TAB_STYLE]
                ?: SettingsDefaults.SEARCH_SOURCE_TAB_STYLE.name,
            showAllSearchResultFields = prefs[SettingsPreferenceKeys.SHOW_ALL_SEARCH_RESULT_FIELDS]
                ?: SettingsDefaults.SHOW_ALL_SEARCH_RESULT_FIELDS,

            uiEngine = UiEngine.fromValue(prefs[SettingsPreferenceKeys.UI_ENGINE]).value,
            themeMode = prefs[SettingsPreferenceKeys.THEME_MODE]
                ?: SettingsDefaults.THEME_MODE.name,
            monetEnable = prefs[SettingsPreferenceKeys.MONET_ENABLE]
                ?: SettingsDefaults.MONET_ENABLE,
            floatingBottomBarEnabled = prefs[SettingsPreferenceKeys.FLOATING_BOTTOM_BAR_ENABLED]
                ?: SettingsDefaults.FLOATING_BOTTOM_BAR_ENABLED,
            barBlurEnabled = prefs.resolveBarBlurEnabled(),
            floatingBarEffect = prefs.resolveFloatingBarEffect().name,
            keyThemeColor = prefs[SettingsPreferenceKeys.KEY_THEME_COLOR] ?: SettingsDefaults.KEY_THEME_COLOR,

            onlyTranslationIfAvailable = prefs[SettingsPreferenceKeys.ONLY_TRANSLATION_IF_AVAILABLE]
                ?: SettingsDefaults.ONLY_TRANSLATION_IF_AVAILABLE,

            limitLyricsInputLines = prefs[SettingsPreferenceKeys.LIMIT_LYRICS_INPUT_LINES]
                ?: SettingsDefaults.LIMIT_LYRICS_INPUT_LINES,
            characterMappingConfig = characterMapping,
            metadataFieldWriteRules = metadataFieldWriteRules,
            sourceSettings = json.decodeSourceSettingsStore(prefs[SettingsPreferenceKeys.SOURCE_SETTINGS]),
            renameFormat = prefs[SettingsPreferenceKeys.RENAME_FORMAT]
                ?: SettingsDefaults.RENAME_FORMAT,
            batchMatchConfig = batchMatchConfig,
            conversionMode = prefs[SettingsPreferenceKeys.CONVERSION_MODE]
                ?: SettingsDefaults.CONVERSION_MODE.name,
            logRetentionOption = prefs[SettingsPreferenceKeys.LOG_RETENTION_OPTION]
                ?: SettingsDefaults.LOG_RETENTION_OPTION.name,
            artistSplitConfig = artistSplitConfig,
            editFieldConfig = EditFieldConfigJson.from(EditFieldConfigRepository.readConfig(prefs)),
        )
    }

    fun restore(backup: SettingsBackup, prefs: MutablePreferences) {
        backup.removeEmptyLines?.let { prefs[SettingsPreferenceKeys.REMOVE_EMPTY_LINES] = it }
        backup.lyricsTagLineKeywords?.let {
            prefs[SettingsPreferenceKeys.LYRICS_TAG_LINE_KEYWORDS] =
                json.encodeToString(normalizeLyricsTagLineKeywords(it))
        }
        backup.lyricFormat?.let { prefs[SettingsPreferenceKeys.LYRIC_FORMAT] = it }
        backup.sortBy?.let { prefs[SettingsPreferenceKeys.SORT_BY] = it }
        backup.sortOrder?.let { prefs[SettingsPreferenceKeys.SORT_ORDER] = it }
        backup.albumSortBy?.let { prefs[SettingsPreferenceKeys.ALBUM_SORT_BY] = it }
        backup.albumSortOrder?.let { prefs[SettingsPreferenceKeys.ALBUM_SORT_ORDER] = it }
        backup.artistSortBy?.let { prefs[SettingsPreferenceKeys.ARTIST_SORT_BY] = it }
        backup.artistSortOrder?.let { prefs[SettingsPreferenceKeys.ARTIST_SORT_ORDER] = it }
        backup.albumGridColumns?.let {
            prefs[SettingsPreferenceKeys.ALBUM_GRID_COLUMNS] = it.coerceIn(2, 4)
        }
        backup.separator?.let { prefs[SettingsPreferenceKeys.SEPARATOR] = it }
        backup.romaEnabled?.let { prefs[SettingsPreferenceKeys.ROMA_ENABLED] = it }
        backup.lyricLineOrder?.let { names ->
            prefs[SettingsPreferenceKeys.LYRIC_LINE_ORDER] =
                decodeLyricLineOrder(names.joinToString(",")).joinToString(",") { it.name }
        }

        backup.checkUpdateEnabled?.let { prefs[SettingsPreferenceKeys.CHECK_UPDATE_ENABLED] = it }
        backup.translationEnabled?.let { prefs[SettingsPreferenceKeys.TRANSLATION_ENABLED] = it }
        backup.lyricIndexEnabled?.let { prefs[SettingsPreferenceKeys.LYRIC_INDEX_ENABLED] = it }
        backup.ignoreShortAudio?.let { prefs[SettingsPreferenceKeys.IGNORE_SHORT_AUDIO] = it }
        backup.defaultPlayerComponent?.let {
            if (it.isBlank()) prefs.remove(SettingsPreferenceKeys.DEFAULT_PLAYER_COMPONENT)
            else prefs[SettingsPreferenceKeys.DEFAULT_PLAYER_COMPONENT] = it
        }
        backup.replayGainTargetLoudness?.let {
            prefs[SettingsPreferenceKeys.REPLAY_GAIN_TARGET_LOUDNESS] = it
        }
        backup.replayGainPeakMode?.let { modeName ->
            runCatching { ReplayGainPeakMode.valueOf(modeName) }
                .getOrNull()
                ?.let { prefs[SettingsPreferenceKeys.REPLAY_GAIN_PEAK_MODE] = it.name }
        }
        backup.searchSourceOrder?.let { list ->
            prefs[SettingsPreferenceKeys.SEARCH_SOURCE_ORDER] = list.idsToCsv()
        }

        // 启用源为 null 或空时回落到默认列表
        if (backup.enabledSearchSources.isNullOrEmpty()) {
            prefs[SettingsPreferenceKeys.ENABLED_SEARCH_SOURCES] =
                SettingsDefaults.DEFAULT_ENABLED_SEARCH_SOURCES.idsToCsv()
        } else {
            // 使用导入的启用源列表，直接映射而不补齐缺失源
            prefs[SettingsPreferenceKeys.ENABLED_SEARCH_SOURCES] =
                backup.enabledSearchSources.idsToCsv()
        }

        backup.searchPageSize?.let { prefs[SettingsPreferenceKeys.SEARCH_PAGE_SIZE] = it }
        backup.searchSourceTabStyle?.let { styleName ->
            runCatching { SearchSourceTabStyle.valueOf(styleName) }
                .getOrNull()
                ?.let { prefs[SettingsPreferenceKeys.SEARCH_SOURCE_TAB_STYLE] = it.name }
        }
        backup.showAllSearchResultFields?.let {
            prefs[SettingsPreferenceKeys.SHOW_ALL_SEARCH_RESULT_FIELDS] = it
        }
        backup.uiEngine?.let { prefs[SettingsPreferenceKeys.UI_ENGINE] = UiEngine.fromValue(it).value }
        backup.themeMode?.let { prefs[SettingsPreferenceKeys.THEME_MODE] = it }
        backup.monetEnable?.let { prefs[SettingsPreferenceKeys.MONET_ENABLE] = it }
        backup.floatingBottomBarEnabled?.let {
            prefs[SettingsPreferenceKeys.FLOATING_BOTTOM_BAR_ENABLED] = it
        }
        backup.barBlurEnabled?.let { prefs[SettingsPreferenceKeys.BAR_BLUR_ENABLED] = it }
        backup.floatingBarEffect?.let {
            prefs[SettingsPreferenceKeys.FLOATING_BAR_EFFECT] = FloatingBarEffect.fromName(it).name
        }
        backup.keyThemeColor?.let { prefs[SettingsPreferenceKeys.KEY_THEME_COLOR] = it }
        backup.onlyTranslationIfAvailable?.let {
            prefs[SettingsPreferenceKeys.ONLY_TRANSLATION_IF_AVAILABLE] = it
        }
        backup.limitLyricsInputLines?.let {
            prefs[SettingsPreferenceKeys.LIMIT_LYRICS_INPUT_LINES] = it
        }
        backup.characterMappingConfig?.let { config ->
            prefs[SettingsPreferenceKeys.CHARACTER_MAPPING_CONFIG] = json.encodeToString(config)
        }
        backup.metadataFieldWriteRules?.let { rules ->
            prefs[SettingsPreferenceKeys.METADATA_FIELD_WRITE_RULES] =
                json.encodeToString(rules)
        }
        backup.sourceSettings?.let { store ->
            prefs[SettingsPreferenceKeys.SOURCE_SETTINGS] = json.encodeToString(store)
        }
        backup.renameFormat?.let { prefs[SettingsPreferenceKeys.RENAME_FORMAT]  = it }
        backup.batchMatchConfig?.let { config ->
            prefs[SettingsPreferenceKeys.BATCH_MATCH_CONFIG] = json.encodeToString(config)
        }
        backup.conversionMode?.let { prefs[SettingsPreferenceKeys.CONVERSION_MODE] = it }
        backup.logRetentionOption?.let { optionName ->
            runCatching { LogRetentionOption.valueOf(optionName) }
                .getOrNull()
                ?.let { prefs[SettingsPreferenceKeys.LOG_RETENTION_OPTION] = it.name }
        }
        backup.artistSplitConfig?.let { config ->
            prefs[SettingsPreferenceKeys.ARTIST_SPLIT_CONFIG] = json.encodeToString(config)
            prefs[SettingsPreferenceKeys.LIBRARY_INDEX_VERSION] = 0
        }
        if (backup.editFieldConfig != null || backup.editFieldVisibilityOverrides != null) {
            val config = backup.editFieldConfig?.toConfig(backup.editFieldVisibilityOverrides.orEmpty())
                ?: EditFieldConfigRepository.readConfig(prefs).copy(
                    overrides = EditFieldConfigJson(
                        version = 2,
                        overrides = backup.editFieldVisibilityOverrides.orEmpty(),
                    ).toConfig().overrides,
                )
            EditFieldConfigRepository.writeConfig(prefs, config)
        }
    }
}
