package com.lonx.lyrico.data.player

import com.lonx.lyrico.R

object FriendlyPlayerRegistry {
    val players: List<FriendlyPlayer> = listOf(
        FriendlyPlayer(
            id = "cone_player",
            displayNameRes = R.string.friendly_player_cone_player,
            packageNames = listOf(
                "ink.trantor.coneplayer.gp",
                "ink.trantor.coneplayer"
            ),
            websiteUrl = "https://coneplayer.trantor.ink/#/"
        ),
        FriendlyPlayer(
            id = "ella_music",
            displayNameRes = R.string.friendly_player_halcyon,
            packageNames = listOf("com.ella.music"),
            websiteUrl = "https://github.com/Kifranei/Ella"
        ),
        FriendlyPlayer(
            id = "salt_player",
            displayNameRes = R.string.friendly_player_salt_player,
            packageNames = listOf("com.salt.music"),
            websiteUrl = "https://moriafly.com/program/salt-player.html"
        )
    )
}
