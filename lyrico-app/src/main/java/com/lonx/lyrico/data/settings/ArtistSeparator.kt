package com.lonx.lyrico.data.settings

enum class ArtistSeparator(
    private val text: String
) {
    COMMA(","),
    SLASH("/"),
    SEMICOLON(";"),
    ENUMERATION_COMMA("、");

    fun toText(): String = text

    companion object {
        fun fromText(text: String): ArtistSeparator =
            entries.firstOrNull { it.text == text } ?: COMMA
    }
}

fun String.toArtistSeparator(): ArtistSeparator =
    ArtistSeparator.fromText(this)
