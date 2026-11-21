package com.lonx.lyrico.plugin

import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun requirePluginUrl(value: String): String {
    val url = value.toHttpUrl()
    require(url.username.isEmpty() && url.password.isEmpty() && url.fragment == null) {
        "Expected an HTTP or HTTPS URL without credentials or fragment"
    }
    return url.toString()
}
