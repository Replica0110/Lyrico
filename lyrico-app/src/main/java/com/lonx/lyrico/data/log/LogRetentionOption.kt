package com.lonx.lyrico.data.log

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class LogRetentionOption(
    @field:StringRes val labelRes: Int,
    val retentionMillis: Long?
) {
    NONE(R.string.log_retention_none, null),
    SEVEN_DAYS(R.string.log_retention_7_days, 7L * 24L * 60L * 60L * 1000L),
    THIRTY_DAYS(R.string.log_retention_30_days, 30L * 24L * 60L * 60L * 1000L),
    NINETY_DAYS(R.string.log_retention_90_days, 90L * 24L * 60L * 60L * 1000L),
    FOREVER(R.string.log_retention_forever, Long.MAX_VALUE);

    val isRecordingEnabled: Boolean
        get() = this != NONE
}