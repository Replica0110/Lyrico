package com.lonx.lyrico.data.model.export

import kotlinx.serialization.Serializable

@Serializable
enum class ExportDestination {
    SELECTED_DIRECTORY,
    AUDIO_DIRECTORY
}
