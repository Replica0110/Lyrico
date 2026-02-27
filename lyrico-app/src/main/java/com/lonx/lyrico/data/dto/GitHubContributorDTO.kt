package com.lonx.lyrico.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class GitHubContributorDTO(
    val id: Int,
    val login: String,
    val avatar_url: String,
    val html_url: String,
    val contributions: Int,
    val type: String = "User"  // "User" or "Bot"
)