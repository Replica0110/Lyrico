package com.lonx.audiotag.model

object AudioTagKeys {
    private val reservedKeys = setOf(
        "TITLE", "TIT2", "TIT1",
        "ARTIST", "TPE1",
        "ALBUM", "TALB",
        "GENRE", "TCON", "STYLE", "SUBGENRE", "MOOD",
        "DATE", "YEAR", "TYER", "TDAT",
        "TRACKNUMBER", "TRACK", "TRCK",
        "ALBUMARTIST", "ALBUM ARTIST", "TPE2", "AART", "ALBUMARTISTSORT",
        "DISCNUMBER", "DISC", "TPOS", "DISKNUMBER",
        "COMPOSER", "TCOM", "©WRT",
        "COMMENT", "COMM", "DESCRIPTION",
        "LYRICIST", "TEXT", "WRITER", "LYRICS BY",
        "LYRICS", "UNSYNCED LYRICS", "USLT", "LYRIC", "LYRICSENG",
        "COPYRIGHT", "TCOP", "CPRO", "©CPY",
        "RATING", "POPM", "RATE",
        "REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_TRACK_PEAK",
        "REPLAYGAIN_ALBUM_GAIN", "REPLAYGAIN_ALBUM_PEAK",
        "REPLAYGAIN_REFERENCE_LOUDNESS",
        "PICTURE"
    )

    fun isReserved(key: String): Boolean {
        return reservedKeys.contains(key.trim().uppercase())
    }
}
