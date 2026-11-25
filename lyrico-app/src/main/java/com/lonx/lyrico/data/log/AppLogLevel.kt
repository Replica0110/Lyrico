package com.lonx.lyrico.data.log

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class AppLogLevel(
    @field:StringRes val labelRes: Int
) {
    DEBUG(R.string.app_log_level_debug),
    INFO(R.string.app_log_level_info),
    WARNING(R.string.app_log_level_warning),
    ERROR(R.string.app_log_level_error)
}