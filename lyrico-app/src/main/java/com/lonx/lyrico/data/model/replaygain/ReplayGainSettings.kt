package com.lonx.lyrico.data.model.replaygain

enum class ReplayGainPeakMode {
    SAMPLE_PEAK,
    TRUE_PEAK
}

data class ReplayGainSettings(
    val targetLoudness: Double,
    val peakMode: ReplayGainPeakMode
)
