package com.lonx.lyrico.utils.coil

import coil3.key.Keyer
import coil3.request.Options

class AudioCoverKeyer: Keyer<CoverRequest> {
    override fun key(data: CoverRequest, options: Options): String {
        // 提取文件路径作为主键，结合时间戳生成唯一缓存键
        val path = data.uri.path ?: data.uri.toString()
        return CoverRequest.getCacheKey(path, data.lastUpdate)
    }
}