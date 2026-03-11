package com.lonx.lyrico.utils

object FileNameSanitizer {
    private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|]")

    fun sanitize(fileName: String, replaceWith: String = "、"): String {
        val sanitized = fileName.replace(INVALID_CHARS, replaceWith)
        return if (replaceWith.isNotEmpty()) {
            sanitized.trim(replaceWith[0], ' ')
        } else {
            sanitized.trim(' ')
        }
    }

    fun sanitizePath(path: String): String {
        val parts = path.split('/')
        return parts.joinToString("/") { sanitize(it) }
    }
}
