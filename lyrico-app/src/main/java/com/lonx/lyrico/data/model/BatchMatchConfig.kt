package com.lonx.lyrico.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BatchMatchConfig(
    val fields: Map<BatchMatchField, BatchMatchMode>,
    val parallelism: Int = 3
) : Parcelable {
}

enum class BatchMatchMode {
    SUPPLEMENT, // 仅为空时补充
    OVERWRITE   // 覆盖
}

enum class BatchMatchField(val displayName: String) {
    TITLE("标题"),
    ARTIST("艺术家"),
    ALBUM("专辑"),
    GENRE("流派"),
    DATE("日期"),
    TRACK_NUMBER("音轨号"),
    LYRICS("歌词"),
    COVER("封面")
}
