package com.lonx.lyrico.plugin.runtime

object HostApiRegistry {
    const val PLUGIN_API_VERSION = 1
    const val HOST_API_VERSION = 1

    val SUPPORTED_HOST_APIS = setOf(
        "app.info",
        "app.userAgent",
        "runtime.info",
        "crypto.md5",
        "crypto.aesEcbPkcs5EncryptBase64",
        "crypto.aesEcbPkcs5EncryptHex",
        "crypto.aesEcbPkcs5DecryptBase64ToText",
        "base64.encodeText",
        "base64.decodeText",
        "base64.dropBytes",
        "base64.decodeBytes",
        "base64.encodeBytes",
        "bytes.xor",
        "bytes.xorBase64",
        "compression.inflateBytesToText",
        "compression.inflateBase64ToText",
        "http.getText",
        "http.postText",
        "http.postBytes",
        "http.get",
        "http.post",
        "http.getBytes",
        "http.postBytesResponse",
        "log.debug",
        "log.warn",
        "log.error"
    )
}
