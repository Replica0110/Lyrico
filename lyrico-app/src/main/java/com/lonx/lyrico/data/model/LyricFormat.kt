package com.lonx.lyrico.data.model

enum class LyricFormat(val displayName: String) {
    PLAIN_LRC("逐行歌词"),
    VERBATIM_LRC("逐字歌词"),
    ENHANCED_LRC("增强型逐字歌词")
}