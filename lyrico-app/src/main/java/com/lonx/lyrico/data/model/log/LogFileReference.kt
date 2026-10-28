package com.lonx.lyrico.data.model.log

import java.security.MessageDigest

/** Keep provider identity and a stable file reference; failures retain their full URI. */
internal object LogFileReference {
    private val uri = Regex("(?:content|file)://[^\\s\"'<>\\)\\]]+")

    fun compact(text: String): String = uri.replace(text) { match ->
        val value = match.value
        if ("/[file:" in value) return@replace value
        val provider = value.substringBefore("://") + "://" +
            value.substringAfter("://").substringBefore('/')
        val hash = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it) }
        "$provider/[file:$hash]"
    }
}
