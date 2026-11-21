package com.lonx.lyrico.data.contributor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GitHubContributorDto(
    val id: Int,
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    val contributions: Int,
    val type: String = "User"
)
