package com.lonx.lyrico.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseDTO(
    val tag_name: String,
    val body: String? = null,
    val html_url: String
)