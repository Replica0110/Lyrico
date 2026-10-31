package com.lonx.lyrico.data.model.artwork

import android.net.Uri
import com.lonx.audiotag.model.AudioPictureType

data class CoverCandidate(
    val uri: Uri,
    val lastUpdate: Long
)

data class CoverRequest(
    val uri: Uri,
    val lastUpdate: Long,
    val pictureType: AudioPictureType = AudioPictureType.FrontCover,
    val fallbackPictureTypes: List<AudioPictureType> = emptyList(),
    val fallbackToAny: Boolean = pictureType == AudioPictureType.FrontCover,
    val candidates: List<CoverCandidate> = emptyList(),
    val artistName: String? = null,
    val artistPosterFolders: List<String> = emptyList(),
    val artistPosterRevision: Long = 0L,
    /**
     * 只查外置海报文件夹，跳过标签里的内嵌图片。
     *
     * 单曲编辑页已经按 ArtistPosterGrouping 分配了内嵌图片；这里只为没有内嵌图片的
     * 艺术家位查外置文件，避免重新读取已编辑的标签或显示其他艺术家的兜底图片。
     */
    val skipEmbeddedPictures: Boolean = false
) {
    companion object {
        /**
         * 创建缓存键：文件路径 + 图片类型 + 时间戳
         * 当文件修改时间改变时，缓存键也会改变，从而自动清除旧缓存
         */
        fun getCacheKey(
            path: String,
            timestamp: Long,
            pictureType: AudioPictureType,
            fallbackPictureTypes: List<AudioPictureType>,
            fallbackToAny: Boolean,
            candidates: List<CoverCandidate> = emptyList(),
            skipEmbeddedPictures: Boolean = false
        ): String {
            val fallbackKey = fallbackPictureTypes.joinToString(",") { it.tagLibName }
            val candidateKey = candidates.joinToString("|") {
                "${it.uri}@${it.lastUpdate}"
            }
            return "$path@${pictureType.tagLibName}@$fallbackKey@$fallbackToAny@$timestamp@$candidateKey" +
                "@embedded:${!skipEmbeddedPictures}"
        }
    }
}
