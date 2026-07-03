package com.lonx.lyrico.ui.components

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
    val candidates: List<CoverCandidate> = emptyList()
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
            candidates: List<CoverCandidate> = emptyList()
        ): String {
            val fallbackKey = fallbackPictureTypes.joinToString(",") { it.tagLibName }
            val candidateKey = candidates.joinToString("|") {
                "${it.uri}@${it.lastUpdate}"
            }
            return "$path@${pictureType.tagLibName}@$fallbackKey@$fallbackToAny@$timestamp@$candidateKey"
        }
    }
}
