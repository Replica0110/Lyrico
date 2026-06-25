package com.lonx.lyrico.data.model.lyrics

import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.repository.SettingsDefaults
import kotlinx.serialization.Serializable

@Serializable
data class LyricRenderConfig(
    val format: LyricFormat,
    val showRomanization: Boolean,
    val showTranslation: Boolean = SettingsDefaults.TRANSLATION_ENABLED,
    val onlyTranslationIfAvailable: Boolean = SettingsDefaults.ONLY_TRANSLATION_IF_AVAILABLE,
    val lineOrder: List<LyricLineTrack> = SettingsDefaults.LYRIC_LINE_ORDER,
    val removeEmptyLines: Boolean = SettingsDefaults.REMOVE_EMPTY_LINES,
    val conversionMode: ConversionMode = SettingsDefaults.CONVERSION_MODE
) {
    val normalizedLineOrder: List<LyricLineTrack>
        get() = lineOrder.normalizedLyricLineOrder()
}
