package com.lonx.lyrico.data.player

import androidx.annotation.StringRes

data class FriendlyPlayer(
    val id: String,
    @param:StringRes val displayNameRes: Int,
    val packageNames: List<String>,
    val websiteUrl: String,
    val marketUri: String? = null
)

data class FriendlyPlayerUiState(
    val player: FriendlyPlayer,
    val installedPackageName: String?,
    val installedDisplayName: String?
) {
    val installed: Boolean = installedPackageName != null
}
