package com.lonx.lyrico.ui.screens.batch

import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.editfield.EditFieldRegistry

/**
 * 批量编辑字段的读写规则。
 *
 * 页面渲染、候选值回填、预览与任务配置都从这里取同一份映射，
 * 新增或改名一个字段时不必再到四处 when 里同步。
 *
 * 封面与评级由专用控件维护，不属于文本草稿，[isText] 为 false。
 */

internal val BatchEditField.code: String
    get() = requireNotNull(EditFieldRegistry.fields.firstOrNull { it.target == target }?.code) {
        "No edit field definition for $target"
    }

internal val BatchEditField.isText: Boolean
    get() = this != BatchEditField.COVER && this != BatchEditField.RATING

/** 草稿里该字段的当前值；未修改时为 `<keep>`。 */
internal fun BatchEditField.readDraftValue(state: BatchEditUiState): String = when (this) {
    BatchEditField.TITLE -> state.title
    BatchEditField.ARTIST -> state.artist
    BatchEditField.ALBUM_ARTIST -> state.albumArtist
    BatchEditField.ALBUM -> state.album
    BatchEditField.DATE -> state.date
    BatchEditField.LANGUAGE -> state.language
    BatchEditField.GENRE -> state.genre
    BatchEditField.TRACK_NUMBER -> state.trackNumber
    BatchEditField.DISC_NUMBER -> state.discNumber
    BatchEditField.COMPOSER -> state.composer
    BatchEditField.LYRICIST -> state.lyricist
    BatchEditField.COPYRIGHT -> state.copyright
    BatchEditField.COMMENT -> state.comment
    BatchEditField.LYRICS -> state.lyrics
    BatchEditField.REPLAY_GAIN_TRACK_GAIN -> state.replayGainTrackGain
    BatchEditField.REPLAY_GAIN_TRACK_PEAK -> state.replayGainTrackPeak
    BatchEditField.REPLAY_GAIN_ALBUM_GAIN -> state.replayGainAlbumGain
    BatchEditField.REPLAY_GAIN_ALBUM_PEAK -> state.replayGainAlbumPeak
    BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS -> state.replayGainReferenceLoudness
    BatchEditField.COVER, BatchEditField.RATING -> "<keep>"
}

/** 把草稿里该字段改成 [value]；非文本字段原样返回。 */
internal fun BatchEditUiState.withDraftValue(
    field: BatchEditField,
    value: String,
): BatchEditUiState = when (field) {
    BatchEditField.TITLE -> copy(title = value)
    BatchEditField.ARTIST -> copy(artist = value)
    BatchEditField.ALBUM_ARTIST -> copy(albumArtist = value)
    BatchEditField.ALBUM -> copy(album = value)
    BatchEditField.DATE -> copy(date = value)
    BatchEditField.LANGUAGE -> copy(language = value)
    BatchEditField.GENRE -> copy(genre = value)
    BatchEditField.TRACK_NUMBER -> copy(trackNumber = value)
    BatchEditField.DISC_NUMBER -> copy(discNumber = value)
    BatchEditField.COMPOSER -> copy(composer = value)
    BatchEditField.LYRICIST -> copy(lyricist = value)
    BatchEditField.COPYRIGHT -> copy(copyright = value)
    BatchEditField.COMMENT -> copy(comment = value)
    BatchEditField.LYRICS -> copy(lyrics = value)
    BatchEditField.REPLAY_GAIN_TRACK_GAIN -> copy(replayGainTrackGain = value)
    BatchEditField.REPLAY_GAIN_TRACK_PEAK -> copy(replayGainTrackPeak = value)
    BatchEditField.REPLAY_GAIN_ALBUM_GAIN -> copy(replayGainAlbumGain = value)
    BatchEditField.REPLAY_GAIN_ALBUM_PEAK -> copy(replayGainAlbumPeak = value)
    BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS -> copy(replayGainReferenceLoudness = value)
    BatchEditField.COVER, BatchEditField.RATING -> this
}

/** 歌曲当前值，用于预览的旧值一栏。 */
internal fun BatchEditField.readSongValue(song: SongEntity): String = when (this) {
    BatchEditField.TITLE -> song.title
    BatchEditField.ARTIST -> song.artist
    BatchEditField.ALBUM_ARTIST -> song.albumArtist
    BatchEditField.ALBUM -> song.album
    BatchEditField.DATE -> song.date
    BatchEditField.LANGUAGE -> song.language
    BatchEditField.GENRE -> song.genre
    BatchEditField.TRACK_NUMBER -> song.trackerNumber
    BatchEditField.DISC_NUMBER -> song.discNumber?.toString()
    BatchEditField.COMPOSER -> song.composer
    BatchEditField.LYRICIST -> song.lyricist
    BatchEditField.COPYRIGHT -> song.copyright
    BatchEditField.COMMENT -> song.comment
    BatchEditField.LYRICS -> song.lyrics
    BatchEditField.REPLAY_GAIN_TRACK_GAIN -> song.replayGainTrackGain
    BatchEditField.REPLAY_GAIN_TRACK_PEAK -> song.replayGainTrackPeak
    BatchEditField.REPLAY_GAIN_ALBUM_GAIN -> song.replayGainAlbumGain
    BatchEditField.REPLAY_GAIN_ALBUM_PEAK -> song.replayGainAlbumPeak
    BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS -> song.replayGainReferenceLoudness
    BatchEditField.COVER, BatchEditField.RATING -> ""
}.orEmpty()

/** 数据库列名，用于「从已选歌曲取值」的去重查询；封面与评级没有列。 */
internal fun BatchEditField.databaseColumnName(): String? = when (this) {
    BatchEditField.TITLE -> "title"
    BatchEditField.ARTIST -> "artist"
    BatchEditField.ALBUM_ARTIST -> "albumArtist"
    BatchEditField.ALBUM -> "album"
    BatchEditField.DATE -> "date"
    BatchEditField.LANGUAGE -> "language"
    BatchEditField.GENRE -> "genre"
    BatchEditField.TRACK_NUMBER -> "trackerNumber"
    BatchEditField.DISC_NUMBER -> "discNumber"
    BatchEditField.COMPOSER -> "composer"
    BatchEditField.LYRICIST -> "lyricist"
    BatchEditField.COPYRIGHT -> "copyright"
    BatchEditField.COMMENT -> "comment"
    BatchEditField.LYRICS -> "lyrics"
    BatchEditField.REPLAY_GAIN_TRACK_GAIN -> "replayGainTrackGain"
    BatchEditField.REPLAY_GAIN_TRACK_PEAK -> "replayGainTrackPeak"
    BatchEditField.REPLAY_GAIN_ALBUM_GAIN -> "replayGainAlbumGain"
    BatchEditField.REPLAY_GAIN_ALBUM_PEAK -> "replayGainAlbumPeak"
    BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS -> "replayGainReferenceLoudness"
    BatchEditField.COVER, BatchEditField.RATING -> null
}
