package com.lonx.audiotag.model

import androidx.annotation.Keep

/**
 * Picture contains information and data of a picture.
 *
 * @param data ByteVector with picture data
 * @param description String with description
 * @param pictureType String with type as specified for ID3v2, e.g. "Front Cover", "Back Cover", "Band"
 * @param mimeType String with image format, e.g. "image/jpeg"
 */
@Keep
public data class Picture(
    val data: ByteArray,
    val description: String,
    val pictureType: String,
    val mimeType: String,
) {

    override fun toString(): String {
        return "Picture(data=[${data.size} bytes], " +
                "description=$description, " +
                "pictureType=$pictureType, " +
                "mimeType=$mimeType)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Picture) return false

        if (!data.contentEquals(other.data)) return false
        if (description != other.description) return false
        if (pictureType != other.pictureType) return false

        return mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + pictureType.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
