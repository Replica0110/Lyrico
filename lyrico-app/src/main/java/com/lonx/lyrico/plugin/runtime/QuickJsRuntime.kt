package com.lonx.lyrico.plugin.runtime

class QuickJsRuntime(
    memoryLimitBytes: Long = DEFAULT_MEMORY_LIMIT_BYTES,
    stackSizeBytes: Long = DEFAULT_STACK_SIZE_BYTES,
    timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    hostApi: QuickJsHostApi? = QuickJsHostApi()
) : PluginJsRuntime {
    private var runtimePtr: Long = QuickJsNative.createRuntime(
        memoryLimitBytes = memoryLimitBytes,
        stackSizeBytes = stackSizeBytes,
        timeoutMs = timeoutMs,
        hostApi = hostApi
    ).also { ptr ->
        if (hostApi != null) {
            QuickJsNative.eval(ptr, HOST_API_BOOTSTRAP, "<lyrico-host>")
        }
    }

    fun eval(script: String): String = eval(script, "<eval>")

    override fun eval(script: String, filename: String): String {
        val ptr = runtimePtr
        check(ptr != 0L) { "QuickJS runtime is closed" }
        return QuickJsNative.eval(ptr, script, filename)
    }

    override fun call(functionName: String, requestJson: String): String {
        val ptr = runtimePtr
        check(ptr != 0L) { "QuickJS runtime is closed" }
        return QuickJsNative.call(ptr, functionName, requestJson)
    }

    override fun close() {
        val ptr = runtimePtr
        if (ptr != 0L) {
            runtimePtr = 0L
            QuickJsNative.closeRuntime(ptr)
        }
    }

    private companion object {
        const val DEFAULT_MEMORY_LIMIT_BYTES = 64L * 1024L * 1024L
        const val DEFAULT_STACK_SIZE_BYTES = 2L * 1024L * 1024L
        const val DEFAULT_TIMEOUT_MS = 15_000L

        val HOST_API_BOOTSTRAP = """
            (function() {
              function hostCall(name, payload) {
                return JSON.parse(__lyricoHostCall(name, JSON.stringify(payload || {}))).value;
              }
              globalThis.Lyrico = {
                crypto: {
                  md5: function(text) { return hostCall("crypto.md5", { text: String(text || "") }); },
                  aesEcbPkcs5EncryptBase64: function(text, key) {
                    return hostCall("crypto.aesEcbPkcs5EncryptBase64", {
                      text: String(text || ""),
                      key: String(key || "")
                    });
                  },
                  aesEcbPkcs5EncryptHex: function(text, key) {
                    return hostCall("crypto.aesEcbPkcs5EncryptHex", {
                      text: String(text || ""),
                      key: String(key || "")
                    });
                  },
                  aesEcbPkcs5DecryptBase64ToText: function(base64, key) {
                    return hostCall("crypto.aesEcbPkcs5DecryptBase64ToText", {
                      base64: String(base64 || ""),
                      key: String(key || "")
                    });
                  }
                },
                base64: {
                  encodeText: function(text) { return hostCall("base64.encodeText", { text: String(text || "") }); },
                  decodeText: function(base64) { return hostCall("base64.decodeText", { base64: String(base64 || "") }); },
                  dropBytes: function(base64, count) { return hostCall("base64.dropBytes", { base64: String(base64 || ""), count: count || 0 }); },
                  decodeBytes: function(base64) { return hostCall("base64.decodeBytes", { base64: String(base64 || "") }); },
                  encodeBytes: function(bytes) { return hostCall("base64.encodeBytes", { bytes: Array.from(bytes || []) }); }
                },
                bytes: {
                  xor: function(bytes, key) { return hostCall("bytes.xor", { bytes: Array.from(bytes || []), key: Array.from(key || []) }); },
                  xorBase64: function(base64, key) { return hostCall("bytes.xorBase64", { base64: String(base64 || ""), key: Array.from(key || []) }); }
                },
                compression: {
                  inflateBytesToText: function(bytes) { return hostCall("compression.inflateBytesToText", { bytes: Array.from(bytes || []) }); },
                  inflateBase64ToText: function(base64) { return hostCall("compression.inflateBase64ToText", { base64: String(base64 || "") }); }
                },
                http: {
                  getText: function(url, options) {
                    options = options || {};
                    return hostCall("http.getText", {
                      url: String(url || ""),
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs
                    });
                  },
                  postText: function(url, body, options) {
                    options = options || {};
                    return hostCall("http.postText", {
                      url: String(url || ""),
                      body: String(body || ""),
                      contentType: options.contentType || "application/json; charset=utf-8",
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs
                    });
                  },
                  postBytes: function(url, body, options) {
                    options = options || {};
                    return hostCall("http.postBytes", {
                      url: String(url || ""),
                      body: String(body || ""),
                      contentType: options.contentType || "application/octet-stream",
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs
                    });
                  }
                },
                log: {
                  debug: function(tag, message) {
                    return hostCall("log.debug", { tag: String(tag || "LyricoPlugin"), message: String(message || "") });
                  },
                  warn: function(tag, message) {
                    return hostCall("log.warn", { tag: String(tag || "LyricoPlugin"), message: String(message || "") });
                  },
                  error: function(tag, message) {
                    return hostCall("log.error", { tag: String(tag || "LyricoPlugin"), message: String(message || "") });
                  }
                }
              };
            })();
        """.trimIndent()
    }
}
