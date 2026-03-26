package com.lonx.lyrico.data.model

import com.lonx.lyrico.data.repository.SettingsDefaults

data class LyricRenderConfig(
    val format: LyricFormat,
    val showRomanization: Boolean,
    val showTranslation: Boolean = SettingsDefaults.TRANSLATION_ENABLED,
    val onlyTranslationIfAvailable: Boolean = SettingsDefaults.ONLY_TRANSLATION_IF_AVAILABLE,
    val removeEmptyLines: Boolean = SettingsDefaults.REMOVE_EMPTY_LINES,
    val conversionMode: ConversionMode = SettingsDefaults.CONVERSION_MODE
)
