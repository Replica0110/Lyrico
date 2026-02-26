package com.lonx.lyrico.data.model

data class LyricRenderConfig(
    val format: LyricFormat,
    val showRomanization: Boolean,
    val showTranslation: Boolean = true,
    val onlyTranslationIfAvailable: Boolean = false
)
