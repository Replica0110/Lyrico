package com.lonx.lyrico.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateDTO(
    val versionName: String,
    val releaseNotes: String,
    val url: String
)