package com.lonx.lyrico.data.model.log

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class AppLogType(
    @field:StringRes val labelRes: Int
) {
    APP(R.string.app_log_type_app),
    CRASH(R.string.app_log_type_crash),
    METADATA(R.string.app_log_type_metadata),
    BATCH(R.string.app_log_type_batch),
    DATABASE(R.string.app_log_type_database),
    NETWORK(R.string.app_log_type_network),
    PLUGIN(R.string.app_log_type_plugin)
}
