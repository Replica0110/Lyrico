package com.lonx.lyrico.data.model.artist

object ArtistSplitDefaults {
    val BUILTIN_SEPARATORS = listOf(
        BuiltinArtistSeparator("slash", "/", true),
        BuiltinArtistSeparator("fullwidth_slash", "／", true),
        BuiltinArtistSeparator("semicolon", ";", true),
        BuiltinArtistSeparator("fullwidth_semicolon", "；", true),
        BuiltinArtistSeparator("comma", ",", true),
        BuiltinArtistSeparator("fullwidth_comma", "，", true),
        BuiltinArtistSeparator("ideographic_comma", "、", true),
        BuiltinArtistSeparator("ampersand", "&", false),
        BuiltinArtistSeparator("feat_dot", " feat. ", false, "feat."),
        BuiltinArtistSeparator("ft_dot", " ft. ", false, "ft."),
        BuiltinArtistSeparator("featuring", " featuring ", false, "featuring")
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

fun ArtistSplitConfig.effectiveSeparators(): List<String> {
    val builtin = ArtistSplitDefaults.BUILTIN_SEPARATORS
        .filter { item -> builtinSeparatorOverrides[item.id] ?: item.defaultEnabled }
        .map { it.value }

    val custom = customSeparators
        .filter { it.enabled }
        .map { it.value }

    return (builtin + custom)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

fun ArtistSplitConfig.effectiveNoSplitArtists(): Set<String> {
    val builtin = ArtistSplitDefaults.BUILTIN_NO_SPLIT_ARTISTS
        .filter { item -> builtinNoSplitArtistOverrides[item.id] ?: item.defaultEnabled }
        .map { it.name }

    return (builtin + customNoSplitArtists)
        .map { it.normalizedArtistKey() }
        .toSet()
}

