package com.lonx.lyrico.data.editfield

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lonx.lyrico.data.settings.settingsDataStore
import kotlinx.coroutines.flow.map

/** 所有修改在一次 DataStore 事务内读、改、写；旧键只用于迁移。 */
class EditFieldConfigRepository(private val context: Context) {
    val configFlow = context.settingsDataStore.data.map(::readConfig)

    suspend fun setEnabled(code: String, enabled: Boolean) = updateConfig { config ->
        require(config.allFields.any { it.code == code }) { "Unknown edit field: $code" }
        config.withEnabled(code, enabled)
    }

    suspend fun setComponentEnabled(key: String, enabled: Boolean) =
        updateConfig { it.withComponentEnabled(key, enabled) }

    suspend fun setBlockOrder(keys: List<String>) = updateConfig { it.withBlockOrder(keys) }

    suspend fun setComponentOrder(key: String, codes: List<String>) =
        updateConfig { it.withComponentOrder(key, codes) }

    suspend fun addCustomTag(key: String) {
        val normalized = CustomTagKey.normalize(key) ?: return
        updateConfig { it.withAddedCustomTag(normalized) }
    }

    suspend fun removeCustomTag(key: String) {
        val normalized = CustomTagKey.normalize(key) ?: return
        updateConfig { it.withRemovedCustomTag(normalized) }
    }

    suspend fun resetAll() = updateConfig { EditFieldConfig() }

    private suspend fun updateConfig(transform: (EditFieldConfig) -> EditFieldConfig) {
        context.settingsDataStore.edit { prefs -> writeConfig(prefs, transform(readConfig(prefs))) }
    }

    companion object {
        private val CONFIG = stringPreferencesKey("edit_field_config")
        private val LEGACY_VISIBILITY = stringPreferencesKey("edit_field_visibility_overrides")
        private val LEGACY_CUSTOM_TAGS = stringPreferencesKey("custom_tag_settings")

        internal fun readConfig(prefs: Preferences): EditFieldConfig = EditFieldConfigJson.decode(
            prefs[CONFIG], prefs[LEGACY_VISIBILITY], prefs[LEGACY_CUSTOM_TAGS],
        )

        internal fun writeConfig(prefs: MutablePreferences, config: EditFieldConfig) {
            prefs[CONFIG] = EditFieldConfigJson.from(config).encode()
            prefs.remove(LEGACY_VISIBILITY)
            prefs.remove(LEGACY_CUSTOM_TAGS)
        }
    }
}
