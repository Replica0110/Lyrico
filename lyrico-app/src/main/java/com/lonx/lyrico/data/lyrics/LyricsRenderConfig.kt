package com.lonx.lyrico.data.lyrics

import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.data.settings.SettingsDefaults
import kotlinx.serialization.Serializable

@Serializable
data class LyricsRenderConfig(
    val format: LyricsFormat,
    val showRomanization: Boolean,
    val showTranslation: Boolean = SettingsDefaults.TRANSLATION_ENABLED,
    val onlyTranslationIfAvailable: Boolean = SettingsDefaults.ONLY_TRANSLATION_IF_AVAILABLE,
    val lineOrder: List<LyricsLineTrack> = defaultLyricsLineOrder,
    val removeEmptyLines: Boolean = SettingsDefaults.REMOVE_EMPTY_LINES,
    val conversionMode: ConversionMode = SettingsDefaults.CONVERSION_MODE
) {
    val normalizedLineOrder: List<LyricsLineTrack>
        get() = lineOrder.normalizedLyricsLineOrder()
}
