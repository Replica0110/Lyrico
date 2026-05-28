package com.lonx.lyrico.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CustomTagSettings(
    val visibleKeys: List<String> = emptyList(),
)

class CustomTagSettingsRepository(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {

    val settingsFlow: Flow<CustomTagSettings> =
        context.settingsDataStore.data.map { preferences ->
            val raw = preferences[CUSTOM_TAG_SETTINGS]
            raw
                ?.let { decodeSettings(it) }
                ?.sanitize()
                ?: CustomTagSettings()
        }

    suspend fun addVisibleKey(input: String) {
        val key = normalizeCustomTagKey(input)
            ?: throw IllegalArgumentException("Invalid custom tag key: $input")

        context.settingsDataStore.edit { preferences ->
            val current = preferences[CUSTOM_TAG_SETTINGS]
                ?.let { decodeSettings(it) }
                ?.sanitize()
                ?: CustomTagSettings()

            val nextKeys = if (key in current.visibleKeys) {
                current.visibleKeys
            } else {
                current.visibleKeys + key
            }

            preferences[CUSTOM_TAG_SETTINGS] =
                encodeSettings(current.copy(visibleKeys = nextKeys))
        }
    }

    suspend fun removeVisibleKey(key: String) {
        context.settingsDataStore.edit { preferences ->
            val current = preferences[CUSTOM_TAG_SETTINGS]
                ?.let { decodeSettings(it) }
                ?.sanitize()
                ?: CustomTagSettings()

            preferences[CUSTOM_TAG_SETTINGS] =
                encodeSettings(
                    current.copy(
                        visibleKeys = current.visibleKeys - key
                    )
                )
        }
    }

    suspend fun setVisibleKeys(keys: List<String>) {
        context.settingsDataStore.edit { preferences ->
            preferences[CUSTOM_TAG_SETTINGS] =
                encodeSettings(
                    CustomTagSettings(
                        visibleKeys = keys.sanitizeCustomTagKeys()
                    )
                )
        }
    }

    suspend fun reset() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(CUSTOM_TAG_SETTINGS)
        }
    }

    private fun decodeSettings(raw: String): CustomTagSettings {
        return runCatching {
            json.decodeFromString<CustomTagSettings>(raw)
        }.getOrDefault(CustomTagSettings())
    }

    private fun encodeSettings(settings: CustomTagSettings): String {
        return json.encodeToString(settings.sanitize())
    }

    private fun CustomTagSettings.sanitize(): CustomTagSettings {
        return copy(
            visibleKeys = visibleKeys.sanitizeCustomTagKeys()
        )
    }

    private fun List<String>.sanitizeCustomTagKeys(): List<String> {
        return mapNotNull { normalizeCustomTagKey(it) }
            .distinct()
    }

    private fun normalizeCustomTagKey(input: String): String? {
        val key = input.trim()

        return when {
            key.isBlank() -> null
            key.length > 64 -> null
            key.any { it == '\n' || it == '\r' } -> null
            else -> key
        }
    }

    companion object {
        private val CUSTOM_TAG_SETTINGS =
            stringPreferencesKey("custom_tag_settings")
    }
}
