package com.lonx.lyrico.data.settings

enum class ReplayGainPeakMode {
    SAMPLE_PEAK,
    TRUE_PEAK
}

data class ReplayGainSettings(
    val targetLoudness: Double,
    val peakMode: ReplayGainPeakMode
)
