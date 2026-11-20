package com.lonx.lyrico.data.model.contributor

data class ContributorInfo(
    val id: Int,
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
    val contributions: Int
)
