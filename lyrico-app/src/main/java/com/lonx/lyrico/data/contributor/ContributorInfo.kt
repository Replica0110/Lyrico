package com.lonx.lyrico.data.contributor

data class ContributorInfo(
    val id: Int,
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
    val contributions: Int
)
