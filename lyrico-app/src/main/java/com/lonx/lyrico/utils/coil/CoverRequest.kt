package com.lonx.lyrico.utils.coil

import android.net.Uri


data class CoverRequest(val uri: Uri, val lastUpdate: Long) {
    companion object {
        /**
         * 创建缓存键：文件路径 + 时间戳
         * 当文件修改时间改变时，缓存键也会改变，从而自动清除旧缓存
         */
        fun getCacheKey(path: String, timestamp: Long): String {
            return "$path@$timestamp"
        }
    }
}
