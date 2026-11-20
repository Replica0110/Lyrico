package com.lonx.lyrico.data.model.search

import androidx.annotation.StringRes
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget

/** Text-searchable built-in metadata fields shown as dedicated local-search tabs. */
enum class LocalSearchField(
    val target: MetadataFieldTarget,
    private val value: (SongEntity) -> String?
) {
    TITLE(MetadataFieldTarget.TITLE, SongEntity::title),
    ARTIST(MetadataFieldTarget.ARTIST, SongEntity::artist),
    ALBUM(MetadataFieldTarget.ALBUM, SongEntity::album),
    ALBUM_ARTIST(MetadataFieldTarget.ALBUM_ARTIST, SongEntity::albumArtist),
    DATE(MetadataFieldTarget.DATE, SongEntity::date),
    LANGUAGE(MetadataFieldTarget.LANGUAGE, SongEntity::language),
    GENRE(MetadataFieldTarget.GENRE, SongEntity::genre),
    LYRICIST(MetadataFieldTarget.LYRICIST, SongEntity::lyricist),
    COMPOSER(MetadataFieldTarget.COMPOSER, SongEntity::composer),
    COPYRIGHT(MetadataFieldTarget.COPYRIGHT, SongEntity::copyright),
    COMMENT(MetadataFieldTarget.COMMENT, SongEntity::comment);

    @get:StringRes
    val labelRes: Int
        get() = target.labelRes

    fun valueOf(song: SongEntity): String? = value(song)?.takeIf { it.isNotBlank() }

    fun matches(song: SongEntity, query: String): Boolean {
        if (query.isBlank()) return false
        return valueOf(song)?.contains(query, ignoreCase = true) == true
    }
}
