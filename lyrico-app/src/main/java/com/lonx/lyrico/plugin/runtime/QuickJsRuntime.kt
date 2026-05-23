package com.lonx.lyrico.plugin.runtime

import androidx.annotation.Keep

@Keep
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

              function normalizeOptions(options) {
                options = options || {};
                return {
                  headers: options.headers || {},
                  contentType: options.contentType,
                  connectTimeoutMs: options.connectTimeoutMs,
                  readTimeoutMs: options.readTimeoutMs,
                  followRedirects: options.followRedirects
                };
              }

              function normalizeBodyPayload(url, body, options) {
                options = normalizeOptions(options);
                return {
                  url: String(url || ""),
                  body: body == null ? "" : String(body),
                  bodyBase64: options.bodyBase64 || "",
                  bodyBytes: options.bodyBytes || null,
                  contentType: options.contentType || "application/json; charset=utf-8",
                  headers: options.headers || {},
                  connectTimeoutMs: options.connectTimeoutMs,
                  readTimeoutMs: options.readTimeoutMs,
                  followRedirects: options.followRedirects
                };
              }

              globalThis.app = {
                getInfo: function() {
                  return hostCall("app.info", {});
                },
                getUserAgent: function() {
                  return hostCall("app.userAgent", {});
                }
              };

              globalThis.runtime = {
                getInfo: function() {
                  return hostCall("runtime.info", {});
                }
              };

              globalThis.Platform = {
                app: globalThis.app,
                runtime: globalThis.runtime,

                crypto: {
                  md5: function(text) {
                    return hostCall("crypto.md5", {
                      text: String(text || "")
                    });
                  },
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
                  encodeText: function(text) {
                    return hostCall("base64.encodeText", {
                      text: String(text || "")
                    });
                  },
                  decodeText: function(base64) {
                    return hostCall("base64.decodeText", {
                      base64: String(base64 || "")
                    });
                  },
                  dropBytes: function(base64, count) {
                    return hostCall("base64.dropBytes", {
                      base64: String(base64 || ""),
                      count: count || 0
                    });
                  },
                  decodeBytes: function(base64) {
                    return hostCall("base64.decodeBytes", {
                      base64: String(base64 || "")
                    });
                  },
                  encodeBytes: function(bytes) {
                    return hostCall("base64.encodeBytes", {
                      bytes: Array.from(bytes || [])
                    });
                  }
                },

                bytes: {
                  xor: function(bytes, key) {
                    return hostCall("bytes.xor", {
                      bytes: Array.from(bytes || []),
                      key: Array.from(key || [])
                    });
                  },
                  xorBase64: function(base64, key) {
                    return hostCall("bytes.xorBase64", {
                      base64: String(base64 || ""),
                      key: Array.from(key || [])
                    });
                  }
                },

                compression: {
                  inflateBytesToText: function(bytes) {
                    return hostCall("compression.inflateBytesToText", {
                      bytes: Array.from(bytes || [])
                    });
                  },
                  inflateBase64ToText: function(base64) {
                    return hostCall("compression.inflateBase64ToText", {
                      base64: String(base64 || "")
                    });
                  }
                },

                http: {
                  /*
                   * 旧 API：返回纯文本 body。
                   */
                  getText: function(url, options) {
                    options = normalizeOptions(options);
                    return hostCall("http.getText", {
                      url: String(url || ""),
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs,
                      followRedirects: options.followRedirects
                    });
                  },

                  postText: function(url, body, options) {
                    options = normalizeOptions(options);
                    return hostCall("http.postText", {
                      url: String(url || ""),
                      body: body == null ? "" : String(body),
                      contentType: options.contentType || "application/json; charset=utf-8",
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs,
                      followRedirects: options.followRedirects
                    });
                  },

                  /*
                   * 旧 API：返回响应 body 的 Base64。
                   * 网易云旧 eapiRequest 当前用的就是这个。
                   */
                  postBytes: function(url, body, options) {
                    options = normalizeOptions(options);
                    return hostCall("http.postBytes", {
                      url: String(url || ""),
                      body: body == null ? "" : String(body),
                      contentType: options.contentType || "application/octet-stream",
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs,
                      followRedirects: options.followRedirects
                    });
                  },

                  /*
                   * 新 API：返回完整文本响应。
                   *
                   * {
                   *   code: 200,
                   *   message: "OK",
                   *   headers: {
                   *     "Set-Cookie": ["MUSIC_A=...; Path=/; ..."]
                   *   },
                   *   body: "...",
                   *   bodyBase64: ""
                   * }
                   */
                  get: function(url, options) {
                    options = normalizeOptions(options);
                    return hostCall("http.get", {
                      url: String(url || ""),
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs,
                      followRedirects: options.followRedirects
                    });
                  },

                  /*
                   * 新 API：返回完整文本响应。
                   */
                  post: function(url, body, options) {
                    return hostCall(
                      "http.post",
                      normalizeBodyPayload(url, body, options)
                    );
                  },

                  /*
                   * 新 API：返回完整二进制响应，bodyBase64 存响应体。
                   */
                  getBytes: function(url, options) {
                    options = normalizeOptions(options);
                    return hostCall("http.getBytes", {
                      url: String(url || ""),
                      headers: options.headers || {},
                      connectTimeoutMs: options.connectTimeoutMs,
                      readTimeoutMs: options.readTimeoutMs,
                      followRedirects: options.followRedirects
                    });
                  },

                  /*
                   * 新 API：返回完整二进制响应，bodyBase64 存响应体。
                   *
                   * body 默认按 UTF-8 字符串写出。
                   * 也可以通过 options.bodyBase64 或 options.bodyBytes 传二进制请求体。
                   */
                  postBytesResponse: function(url, body, options) {
                    options = normalizeOptions(options);
                    var payload = normalizeBodyPayload(url, body, options);
                    payload.contentType = options.contentType || "application/octet-stream";
                    return hostCall("http.postBytesResponse", payload);
                  }
                },

                log: {
                  debug: function(tag, message) {
                    if (message === undefined) {
                      message = tag;
                      tag = "PlatformPlugin";
                    }
                    return hostCall("log.debug", {
                      tag: String(tag || "PlatformPlugin"),
                      message: String(message || "")
                    });
                  },
                  warn: function(tag, message) {
                    if (message === undefined) {
                      message = tag;
                      tag = "PlatformPlugin";
                    }
                    return hostCall("log.warn", {
                      tag: String(tag || "PlatformPlugin"),
                      message: String(message || "")
                    });
                  },
                  error: function(tag, message) {
                    if (message === undefined) {
                      message = tag;
                      tag = "PlatformPlugin";
                    }
                    return hostCall("log.error", {
                      tag: String(tag || "PlatformPlugin"),
                      message: String(message || "")
                    });
                  }
                }
              };
            })();
        """.trimIndent()
    }
}
