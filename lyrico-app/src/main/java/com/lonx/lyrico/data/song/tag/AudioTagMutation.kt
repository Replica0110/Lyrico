package com.lonx.lyrico.data.song.tag

import android.net.Uri
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.model.CustomTagField

data class AudioTagMutation(
    val mode: AudioTagMutationMode,
    val fields: Map<AudioTagFieldKey, FieldMutation> = emptyMap(),
    val customFields: List<CustomTagFieldMutation> = emptyList(),
    val pictureUpdate: PictureUpdate = PictureUpdate.Unchanged
)

enum class AudioTagMutationMode {
    Patch,
    Overwrite
}

enum class AudioTagFieldKey {
    Title,
    Artist,
    Album,
    AlbumArtist,
    Genre,
    Date,
    Language,
    TrackNumber,
    DiscNumber,
    Composer,
    Lyricist,
    Comment,
    Lyrics,
    TtmlLyrics,
    Copyright,
    Rating,
    ReplayGainTrackGain,
    ReplayGainTrackPeak,
    ReplayGainAlbumGain,
    ReplayGainAlbumPeak,
    ReplayGainReferenceLoudness
}

sealed interface FieldMutation {
    data object Unchanged : FieldMutation
    data object Clear : FieldMutation
    data class Set(val value: String) : FieldMutation
}

sealed interface CustomTagFieldMutation {
    data object Unchanged : CustomTagFieldMutation
    data class ReplaceAll(val fields: List<CustomTagField>) : CustomTagFieldMutation
    data class Set(val key: String, val value: String) : CustomTagFieldMutation
    data class Clear(val key: String) : CustomTagFieldMutation
}

sealed interface PictureUpdate {
    data object Unchanged : PictureUpdate
    data object RemoveFrontCover : PictureUpdate
    data object RemoveAllPictures : PictureUpdate

    data class RemovePicture(
        val type: AudioPictureType,
        val basePictures: List<AudioPicture>? = null
    ) : PictureUpdate

    data class ReplaceFrontCover(
        val source: PictureSource
    ) : PictureUpdate

    data class ReplacePicture(
        val type: AudioPictureType,
        val source: PictureSource,
        val basePictures: List<AudioPicture>? = null
    ) : PictureUpdate

    data class ReplaceAll(
        val pictures: List<AudioPicture>
    ) : PictureUpdate
}

sealed interface PictureSource {
    data class Bytes(
        val bytes: ByteArray,
        val mimeType: String? = null
    ) : PictureSource {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bytes) return false
            if (!bytes.contentEquals(other.bytes)) return false
            return mimeType == other.mimeType
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + (mimeType?.hashCode() ?: 0)
            return result
        }
    }

    data class UriSource(
        val uri: Uri
    ) : PictureSource

    data class UrlSource(
        val url: String
    ) : PictureSource
}

sealed interface PictureWriteCommand {
    data object Unchanged : PictureWriteCommand

    data class ReplaceAll(
        val pictures: List<AudioPicture>
    ) : PictureWriteCommand
}
