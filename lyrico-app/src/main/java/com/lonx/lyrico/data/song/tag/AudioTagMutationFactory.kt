package com.lonx.lyrico.data.song.tag

import android.net.Uri
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.CustomTagField

object AudioTagMutationFactory {
    fun fromAudioTagData(
        data: AudioTagData,
        mode: AudioTagMutationMode
    ): AudioTagMutation {
        val patchMode = mode == AudioTagMutationMode.Patch
        val fields = buildMap {
            putString(AudioTagFieldKey.Title, data.title, patchMode)
            putString(AudioTagFieldKey.Artist, data.artist, patchMode)
            putString(AudioTagFieldKey.Album, data.album, patchMode)
            putString(AudioTagFieldKey.AlbumArtist, data.albumArtist, patchMode)
            putString(AudioTagFieldKey.Genre, data.genre, patchMode)
            putString(AudioTagFieldKey.Date, data.date, patchMode)
            putString(AudioTagFieldKey.Language, data.language, patchMode)
            putString(AudioTagFieldKey.TrackNumber, data.trackNumber, patchMode)
            putInt(AudioTagFieldKey.DiscNumber, data.discNumber, patchMode)
            putString(AudioTagFieldKey.Composer, data.composer, patchMode)
            putString(AudioTagFieldKey.Lyricist, data.lyricist, patchMode)
            putString(AudioTagFieldKey.Comment, data.comment, patchMode)
            putString(AudioTagFieldKey.Lyrics, data.lyrics, patchMode)
            putString(AudioTagFieldKey.Copyright, data.copyright, patchMode)
            putInt(AudioTagFieldKey.Rating, data.rating, patchMode)
            putString(AudioTagFieldKey.ReplayGainTrackGain, data.replayGainTrackGain, patchMode)
            putString(AudioTagFieldKey.ReplayGainTrackPeak, data.replayGainTrackPeak, patchMode)
            putString(AudioTagFieldKey.ReplayGainAlbumGain, data.replayGainAlbumGain, patchMode)
            putString(AudioTagFieldKey.ReplayGainAlbumPeak, data.replayGainAlbumPeak, patchMode)
            putString(
                AudioTagFieldKey.ReplayGainReferenceLoudness,
                data.replayGainReferenceLoudness,
                patchMode
            )
        }

        val customFields = when {
            data.customFields.isNotEmpty() -> listOf(CustomTagFieldMutation.ReplaceAll(data.customFields))
            mode == AudioTagMutationMode.Overwrite -> listOf(CustomTagFieldMutation.ReplaceAll(emptyList()))
            else -> emptyList()
        }

        return AudioTagMutation(
            mode = mode,
            fields = fields,
            customFields = customFields,
            pictureUpdate = data.toPictureUpdate(mode)
        )
    }

    private fun MutableMap<AudioTagFieldKey, FieldMutation>.putString(
        key: AudioTagFieldKey,
        value: String?,
        patchMode: Boolean
    ) {
        when {
            value != null -> put(key, FieldMutation.Set(value))
            !patchMode -> put(key, FieldMutation.Clear)
        }
    }

    private fun MutableMap<AudioTagFieldKey, FieldMutation>.putInt(
        key: AudioTagFieldKey,
        value: Int?,
        patchMode: Boolean
    ) {
        when {
            value != null -> put(key, FieldMutation.Set(value.toString()))
            !patchMode -> put(key, FieldMutation.Clear)
        }
    }

    private fun AudioTagData.toPictureUpdate(mode: AudioTagMutationMode): PictureUpdate {
        val picUrl = picUrl
        if (picUrl != null) {
            val normalizedPicUrl = picUrl.trim()
            return if (normalizedPicUrl.isEmpty()) {
                PictureUpdate.RemoveFrontCover
            } else if (normalizedPicUrl.startsWith("http")) {
                PictureUpdate.ReplaceFrontCover(PictureSource.UrlSource(normalizedPicUrl))
            } else {
                PictureUpdate.ReplaceFrontCover(PictureSource.UriSource(Uri.parse(normalizedPicUrl)))
            }
        }

        return if (mode == AudioTagMutationMode.Overwrite && pictures.isNotEmpty()) {
            PictureUpdate.ReplaceAll(pictures)
        } else {
            PictureUpdate.Unchanged
        }
    }
}
