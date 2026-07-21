package com.lonx.lyrico.data.song.tag

data class TagWriteRule(
    val standardKey: String,
    val aliasesToClear: List<String> = emptyList()
)

object AudioTagWriteRules {
    private val rules = mapOf(
        AudioTagFieldKey.Title to TagWriteRule("TITLE", listOf("TIT2", "TIT1")),
        AudioTagFieldKey.Artist to TagWriteRule("ARTIST", listOf("TPE1")),
        AudioTagFieldKey.Album to TagWriteRule("ALBUM", listOf("TALB")),
        AudioTagFieldKey.Genre to TagWriteRule("GENRE", listOf("TCON", "STYLE", "SUBGENRE", "MOOD")),
        AudioTagFieldKey.Date to TagWriteRule("DATE", listOf("YEAR", "TYER", "TDAT")),
        AudioTagFieldKey.Language to TagWriteRule("LANGUAGE", listOf("TLAN")),
        AudioTagFieldKey.TrackNumber to TagWriteRule("TRACKNUMBER", listOf("TRACK", "TRCK")),
        AudioTagFieldKey.AlbumArtist to TagWriteRule(
            "ALBUMARTIST",
            listOf("TPE2", "ALBUM ARTIST", "aART", "ALBUMARTISTSORT")
        ),
        AudioTagFieldKey.DiscNumber to TagWriteRule("DISCNUMBER", listOf("DISC", "TPOS", "DISKNUMBER")),
        AudioTagFieldKey.Composer to TagWriteRule("COMPOSER", listOf("TCOM", "©wrt")),
        AudioTagFieldKey.Comment to TagWriteRule("COMMENT", listOf("COMM", "DESCRIPTION")),
        AudioTagFieldKey.Lyricist to TagWriteRule("LYRICIST", listOf("TEXT", "WRITER", "LYRICS BY")),
        AudioTagFieldKey.Lyrics to TagWriteRule(
            "LYRICS",
            listOf("UNSYNCED LYRICS", "USLT", "LYRIC", "LYRICSENG")
        ),
        AudioTagFieldKey.TtmlLyrics to TagWriteRule("TTML LYRICS"),
        AudioTagFieldKey.Copyright to TagWriteRule("COPYRIGHT", listOf("TCOP", "CPRO", "©cpy")),
        AudioTagFieldKey.ReplayGainTrackGain to TagWriteRule("REPLAYGAIN_TRACK_GAIN"),
        AudioTagFieldKey.ReplayGainTrackPeak to TagWriteRule("REPLAYGAIN_TRACK_PEAK"),
        AudioTagFieldKey.ReplayGainAlbumGain to TagWriteRule("REPLAYGAIN_ALBUM_GAIN"),
        AudioTagFieldKey.ReplayGainAlbumPeak to TagWriteRule("REPLAYGAIN_ALBUM_PEAK"),
        AudioTagFieldKey.ReplayGainReferenceLoudness to TagWriteRule("REPLAYGAIN_REFERENCE_LOUDNESS")
    )

    fun ruleOf(key: AudioTagFieldKey): TagWriteRule? = rules[key]
}
