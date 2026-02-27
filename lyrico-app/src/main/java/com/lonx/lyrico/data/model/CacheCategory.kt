package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class CacheCategory(
    @field:StringRes val labelRes: Int
) {
    IMAGE(R.string.cache_category_image),
    NETWORK(R.string.cache_category_network),
    EXTERNAL(R.string.cache_category_external),
    OTHER(R.string.cache_category_other)
}