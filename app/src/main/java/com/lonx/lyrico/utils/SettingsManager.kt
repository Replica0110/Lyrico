package com.lonx.lyrico.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lonx.lyrico.data.model.LyricDisplayMode

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    private object PreferencesKeys {
        val LYRIC_DISPLAY_MODE = stringPreferencesKey("lyric_display_mode")
    }


    suspend fun saveLyricDisplayMode(mode: LyricDisplayMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LYRIC_DISPLAY_MODE] = mode.name
        }
    }
}
