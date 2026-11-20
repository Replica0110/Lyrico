package com.lonx.lyrico.domain.poster

import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.artistPictureTypes
import com.lonx.audiotag.model.type
import com.lonx.lyrico.domain.artist.normalizedArtistKey

/**
 * 一张艺术家图片及其归属。
 *
 * 归属由 `description` 决定（对不上时退回描述本身），归属键即图片身份：一个艺术家只保留一张
 * （ID3v2 同一 content descriptor 只能挂一张图），定位时用 [ownerKey] 而不是列表下标或实例。
 */
data class ArtistPictureEntry(
    val picture: AudioPicture,
    val description: String,
    val artist: String?
) {
    /** 描述与当前艺术家字段对不上，需要用户重新指定归属。 */
    val isUnmatched: Boolean get() = artist == null

    /** 归属名：匹配到的艺术家，否则退回标签描述（可能为空）。 */
    val ownerName: String get() = artist ?: description

    /** 归属键：归组与定位都按它比较（忽略大小写与首尾空白）。 */
    val ownerKey: String get() = ownerName.normalizedArtistKey()

    /**
     * 界面显示名：优先归属的艺术家，其次标签描述，都为空时用 [untagged]。
     */
    fun displayName(untagged: String): String = ownerName.ifBlank { untagged }
}

/**
 * 把标签里的艺术家图片按描述归属到当前歌曲的艺术家；对不上的图片由界面让用户重新指定归属。
 */
object ArtistPosterGrouping {

    /**
     * 取出 [pictures] 里所有艺术家图片并归属到 [artistNames]（忽略大小写与首尾空白）。
     * 描述非空时按同名归属；描述为空时只有唯一艺术家才能确定归属。
     */
    fun entries(
        pictures: List<AudioPicture>,
        artistNames: List<String>
    ): List<ArtistPictureEntry> {
        if (pictures.isEmpty()) return emptyList()

        val artists = artistNames.map { it.trim() }.filter { it.isNotEmpty() }
        val byKey = artists.associateBy { it.normalizedArtistKey() }
        val soleArtist = artists.singleOrNull()

        return pictures.mapNotNull { picture ->
            if (picture.type !in artistPictureTypes) return@mapNotNull null

            val description = picture.description.trim()
            ArtistPictureEntry(
                picture = picture,
                description = description,
                artist = if (description.isEmpty()) {
                    soleArtist
                } else {
                    byKey[description.normalizedArtistKey()]
                }
            )
        }
    }

    /** [ownerName] 对应的归属键；写操作按它定位。 */
    fun ownerKeyOf(ownerName: String): String = ownerName.normalizedArtistKey()

    /** 单张图片的归属键；不是艺术家图片时返回 `null`。 */
    fun ownerKeyOf(picture: AudioPicture, artistNames: List<String>): String? =
        entries(listOf(picture), artistNames).firstOrNull()?.ownerKey

    /** [artistName] 在 [entries] 中已经拥有的图片数量。 */
    fun countFor(entries: List<ArtistPictureEntry>, artistName: String): Int {
        val key = ownerKeyOf(artistName)
        return entries.count { it.ownerKey == key }
    }
}
