package com.lonx.lyrico.data.model.artist

object ArtistSplitDefaults {
    val BUILTIN_SEPARATORS = listOf(
        BuiltinArtistSeparator("slash", "/"),
        BuiltinArtistSeparator("fullwidth_slash", "／"),
        BuiltinArtistSeparator("semicolon", ";"),
        BuiltinArtistSeparator("fullwidth_semicolon", "；"),
        BuiltinArtistSeparator("comma", ","),
        BuiltinArtistSeparator("fullwidth_comma", "，"),
        BuiltinArtistSeparator("ideographic_comma", "、"),
        BuiltinArtistSeparator("ampersand", "&"),
        BuiltinArtistSeparator("feat_dot", " feat. ", "feat."),
        BuiltinArtistSeparator("ft_dot", " ft. ", "ft."),
        BuiltinArtistSeparator("featuring", " featuring ", "featuring")
    )

    val BUILTIN_NO_SPLIT_ARTISTS = listOf(
        BuiltinNoSplitArtist("simon_and_garfunkel", "Simon & Garfunkel"),
        BuiltinNoSplitArtist("earth_wind_and_fire", "Earth, Wind & Fire"),
        BuiltinNoSplitArtist("bump_of_chicken", "BUMP OF CHICKEN")
    )
}

fun String.normalizedArtistKey(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()
}

fun ArtistSplitConfig.visibleBuiltinSeparators(): List<BuiltinArtistSeparator> =
    ArtistSplitDefaults.BUILTIN_SEPARATORS.filterNot { it.id in hiddenBuiltinSeparatorIds }

fun ArtistSplitConfig.effectiveSeparators(): List<String> {
    val builtin = visibleBuiltinSeparators().map { it.value }

    val custom = customSeparators
        .map { it.value }

    return (builtin + custom)
        .filter { it.isNotBlank() }
        .distinctBy { it.trim() }
}

fun ArtistSplitConfig.effectiveNoSplitArtists(): Set<String> {
    return effectiveNoSplitArtistNames()
        .map { it.normalizedArtistKey() }
        .toSet()
}

fun ArtistSplitConfig.effectiveNoSplitArtistNames(): List<String> {
    val builtin = ArtistSplitDefaults.BUILTIN_NO_SPLIT_ARTISTS
        .filter { item -> builtinNoSplitArtistOverrides[item.id] ?: item.defaultEnabled }
        .map { it.name }

    val custom = customNoSplitArtists
        .map { it.name }

    return (builtin + custom)
        .filter { it.isNotBlank() }
        .distinctBy { it.normalizedArtistKey() }
}
