package com.lonx.audiotag.model

import androidx.annotation.Keep

/**
 * Metadata contains audio properties, property map and pictures of an audio file.
 */
@Keep
public data class Metadata(
    val propertyMap: PropertyMap,
    val pictures: Array<Picture>,
) {

    override fun toString(): String {
        return "Metadata(propertyMap=${propertyMap.mapValues { it.value.contentToString() }}, " +
                "pictures=${pictures.contentToString()})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Metadata) return false

        if (propertyMap != other.propertyMap) return false

        return pictures.contentEquals(other.pictures)
    }

    override fun hashCode(): Int {
        var result = propertyMap.hashCode()
        result = 31 * result + pictures.contentHashCode()
        return result
    }
}
