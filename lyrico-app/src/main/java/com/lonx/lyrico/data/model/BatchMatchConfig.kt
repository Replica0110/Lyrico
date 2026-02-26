package com.lonx.lyrico.data.model

import android.os.Parcelable
import androidx.annotation.StringRes
import com.lonx.lyrico.R
import kotlinx.parcelize.Parcelize

@Parcelize
data class BatchMatchConfig(
    val fields: Map<BatchMatchField, BatchMatchMode>,
    val concurrency: Int = 3
) : Parcelable {
}

enum class BatchMatchMode(
    @field:StringRes val labelRes: Int
) {
    SUPPLEMENT(R.string.batch_match_mode_supplement), // 仅为空时补充
    OVERWRITE(R.string.batch_match_mode_overwrite)   // 覆盖
}

enum class BatchMatchField(
    @field:StringRes val labelRes: Int
) {
    TITLE(R.string.label_title),
    ARTIST(R.string.label_artists),
    ALBUM(R.string.label_album),
    GENRE(R.string.label_genre),
    DATE(R.string.label_date),
    TRACK_NUMBER(R.string.label_track_number),
    LYRICS(R.string.label_lyrics),
    COVER(R.string.label_cover)
}
