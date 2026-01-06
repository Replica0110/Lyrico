package com.lonx.audiotag.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.ArrayList
@Parcelize
data class AudioTagData(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val date: String? = null,
    val trackerNumber: String? = null,
    val lyrics: String? = null,

    val durationMilliseconds: Int = 0,
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0,

    val rawProperties: Map<String, Array<String>>? = null,

    val pictures: List<AudioPicture> = ArrayList()
): Parcelable

@Parcelize
data class AudioPicture(
    val data: ByteArray,
    val mimeType: String = "image/jpeg",
    val description: String = "",
    val pictureType: String = "Front Cover"
): Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioPicture) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}