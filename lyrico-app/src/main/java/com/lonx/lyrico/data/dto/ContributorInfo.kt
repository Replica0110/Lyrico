package com.lonx.lyrico.data.dto

data class ContributorInfo(
    val id: Int,
    val login: String,
    val avatar_url: String,
    val html_url: String,
    val contributions: Int,
    val type: String = "User"  // "User" or "Bot"
)