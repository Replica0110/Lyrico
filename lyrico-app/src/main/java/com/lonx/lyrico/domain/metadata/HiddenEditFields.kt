package com.lonx.lyrico.domain.metadata

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import java.util.Locale

/**
 * 单曲编辑提交时，仅采用可见字段的草稿值，隐藏字段保留读取时的原值。
 * 没有原始快照时沿用草稿；此规则不用于批量编辑的 KEEP_VALUE 任务契约。
 */
internal fun AudioTagData.preserveHiddenEditFields(
    original: AudioTagData?,
    visibleFieldCodes: Set<String>,
): AudioTagData {
    val base = original ?: return this

    fun visible(code: String): Boolean = code in visibleFieldCodes

    return copy(
        customFields = customFields.filter { field ->
            visible(EditFieldRegistry.customTagCode(field.key.uppercase(Locale.ROOT)))
        } + base.customFields.filterNot { field ->
            visible(EditFieldRegistry.customTagCode(field.key.uppercase(Locale.ROOT)))
        },
        title = if (visible("title")) title else base.title,
        artist = if (visible("artist")) artist else base.artist,
        albumArtist = if (visible("album_artist")) albumArtist else base.albumArtist,
        album = if (visible("album")) album else base.album,
        date = if (visible("date")) date else base.date,
        language = if (visible("language")) language else base.language,
        genre = if (visible("genre")) genre else base.genre,
        trackNumber = if (visible("track_number")) trackNumber else base.trackNumber,
        discNumber = if (visible("disc_number")) discNumber else base.discNumber,
        composer = if (visible("composer")) composer else base.composer,
        lyricist = if (visible("lyricist")) lyricist else base.lyricist,
        copyright = if (visible("copyright")) copyright else base.copyright,
        comment = if (visible("comment")) comment else base.comment,
        replayGainTrackGain = if (visible("track_gain")) {
            replayGainTrackGain
        } else {
            base.replayGainTrackGain
        },
        replayGainTrackPeak = if (visible("track_peak")) {
            replayGainTrackPeak
        } else {
            base.replayGainTrackPeak
        },
        replayGainAlbumGain = if (visible("album_gain")) {
            replayGainAlbumGain
        } else {
            base.replayGainAlbumGain
        },
        replayGainAlbumPeak = if (visible("album_peak")) {
            replayGainAlbumPeak
        } else {
            base.replayGainAlbumPeak
        },
        replayGainReferenceLoudness = if (visible("reference_loudness")) {
            replayGainReferenceLoudness
        } else {
            base.replayGainReferenceLoudness
        },
        lyrics = if (visible("lyrics")) lyrics else base.lyrics,
        pictures = if (visible("picture")) pictures else base.pictures,
        picUrl = if (visible("picture")) picUrl else base.picUrl,
        rating = if (visible("rating")) rating else base.rating,
    )
}
