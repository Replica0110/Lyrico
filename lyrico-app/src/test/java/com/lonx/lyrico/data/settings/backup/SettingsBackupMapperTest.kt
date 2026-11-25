package com.lonx.lyrico.data.settings.backup

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lonx.lyrico.data.editfield.EditFieldConfigJson
import com.lonx.lyrico.data.editfield.EditFieldConfigRepository
import com.lonx.lyrico.data.settings.FloatingBarEffect
import com.lonx.lyrico.data.settings.ArtistSplitConfig
import com.lonx.lyrico.data.batch.BatchMatchConfigDefaults
import com.lonx.lyrico.data.rename.CharacterMappingConfig
import com.lonx.lyrico.data.rename.CharacterMappingDefaults
import com.lonx.lyrico.data.settings.SettingsDefaults
import com.lonx.lyrico.data.settings.SettingsPreferenceKeys
import com.lonx.lyrico.data.settings.csvToIds
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBackupMapperTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mapper = SettingsBackupMapper(json)

    @Test
    fun missingFieldsPreservePreferencesExceptEnabledSourcesFallback() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.SEARCH_PAGE_SIZE to 37,
            SettingsPreferenceKeys.KEY_THEME_COLOR to 0xFF3482FF.toInt(),
            SettingsPreferenceKeys.ENABLED_SEARCH_SOURCES to "old-source",
        )

        mapper.restore(json.decodeFromString<SettingsBackup>("{}"), prefs)

        assertEquals(37, prefs[SettingsPreferenceKeys.SEARCH_PAGE_SIZE])
        assertEquals(0xFF3482FF.toInt(), prefs[SettingsPreferenceKeys.KEY_THEME_COLOR])
        assertEquals(
            SettingsDefaults.DEFAULT_ENABLED_SEARCH_SOURCES.toList(),
            prefs[SettingsPreferenceKeys.ENABLED_SEARCH_SOURCES].csvToIds(),
        )
    }

    @Test
    fun partialImportKeepsValidationRemovalAndIndexInvalidation() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.REPLAY_GAIN_PEAK_MODE to "TRUE_PEAK",
            SettingsPreferenceKeys.SEARCH_SOURCE_TAB_STYLE to "ICON_AND_TEXT",
            SettingsPreferenceKeys.DEFAULT_PLAYER_COMPONENT to "old-player",
            SettingsPreferenceKeys.LIBRARY_INDEX_VERSION to 42,
        )
        val backup = SettingsBackup(
            albumGridColumns = 99,
            defaultPlayerComponent = " ",
            replayGainPeakMode = "unknown",
            searchSourceTabStyle = "unknown",
            enabledSearchSources = listOf(" plugin-a ", "plugin-a", "", "plugin-b"),
            artistSplitConfig = ArtistSplitConfig(),
            lyricsTagLineKeywords = listOf(" a,b ", "b；c", ""),
        )

        mapper.restore(backup, prefs)

        assertEquals(4, prefs[SettingsPreferenceKeys.ALBUM_GRID_COLUMNS])
        assertNull(prefs[SettingsPreferenceKeys.DEFAULT_PLAYER_COMPONENT])
        assertEquals("TRUE_PEAK", prefs[SettingsPreferenceKeys.REPLAY_GAIN_PEAK_MODE])
        assertEquals("ICON_AND_TEXT", prefs[SettingsPreferenceKeys.SEARCH_SOURCE_TAB_STYLE])
        assertEquals("plugin-a,plugin-b", prefs[SettingsPreferenceKeys.ENABLED_SEARCH_SOURCES])
        assertEquals(0, prefs[SettingsPreferenceKeys.LIBRARY_INDEX_VERSION])
        assertEquals(
            listOf("a", "b", "c"),
            json.decodeFromString<List<String>>(prefs[SettingsPreferenceKeys.LYRICS_TAG_LINE_KEYWORDS]!!),
        )
    }

    @Test
    fun legacyFieldVisibilityRestoresAndRemovesLegacyStorage() {
        val legacyVisibility = stringPreferencesKey("edit_field_visibility_overrides")
        val legacyTags = stringPreferencesKey("custom_tag_settings")
        val prefs = mutablePreferencesOf(
            legacyVisibility to """{"values":{"artist":true}}""",
            legacyTags to """{"visibleKeys":["TEST"]}""",
        )

        mapper.restore(
            json.decodeFromString<SettingsBackup>(
                """{"edit_field_visibility_overrides":{"title":false}}"""
            ),
            prefs,
        )

        val restored = EditFieldConfigRepository.readConfig(prefs)
        assertEquals(false, restored.overrides["title"])
        assertEquals(listOf("TEST"), restored.customTags)
        assertNull(prefs[legacyVisibility])
        assertNull(prefs[legacyTags])
    }

    @Test
    fun currentFieldConfigTakesPrecedenceOverLegacyVisibility() {
        val prefs = mutablePreferencesOf()
        mapper.restore(
            SettingsBackup(
                editFieldConfig = EditFieldConfigJson(overrides = mapOf("title" to true)),
                editFieldVisibilityOverrides = mapOf("title" to false),
            ),
            prefs,
        )

        assertEquals(true, EditFieldConfigRepository.readConfig(prefs).overrides["title"])
    }

    @Test
    fun exportPreservesRawThemeColorAndDoesNotEnableBlurFromMaterial() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.KEY_THEME_COLOR to 0xFF123456.toInt(),
            SettingsPreferenceKeys.FLOATING_BAR_EFFECT to FloatingBarEffect.LIQUID_GLASS.name,
        )

        val backup = mapper.fromPreferences(
            prefs,
            CharacterMappingConfig(rules = CharacterMappingDefaults.ALL_BUILTIN_RULES),
            BatchMatchConfigDefaults.DEFAULT_CONFIG,
            ArtistSplitConfig(),
            emptyList(),
        )

        assertEquals(0xFF123456.toInt(), backup.keyThemeColor)
        assertEquals(FloatingBarEffect.LIQUID_GLASS.name, backup.floatingBarEffect)
        assertEquals(false, backup.barBlurEnabled)
        assertEquals(SettingsDefaults.SEARCH_SOURCE_ORDER, backup.enabledSearchSources)
        assertFalse(prefs.contains(SettingsPreferenceKeys.BAR_BLUR_ENABLED))
    }
}
