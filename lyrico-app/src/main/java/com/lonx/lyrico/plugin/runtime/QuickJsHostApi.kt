package com.lonx.lyrico.plugin.runtime

import android.util.Base64
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class QuickJsHostApi(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
) {
    fun call(name: String, payloadJson: String): String {
        val payload = runCatching {
            json.parseToJsonElement(payloadJson).jsonObject
        }.getOrDefault(JsonObject(emptyMap()))

        return when (name) {
            "crypto.md5" -> text(md5(payload.string("text")))

            "crypto.aesEcbPkcs5EncryptBase64" -> text(
                aesEcbPkcs5EncryptBase64(
                    text = payload.string("text"),
                    key = payload.string("key")
                )
            )

            "crypto.aesEcbPkcs5EncryptHex" -> text(
                aesEcbPkcs5Encrypt(
                    text = payload.string("text"),
                    key = payload.string("key")
                ).toHex()
            )

            "crypto.aesEcbPkcs5DecryptBase64ToText" -> text(
                aesEcbPkcs5DecryptBase64ToText(
                    base64 = payload.string("base64"),
                    key = payload.string("key")
                )
            )

            "base64.encodeText" -> text(
                Base64.encodeToString(
                    payload.string("text").toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
            )

            "base64.decodeText" -> text(
                String(
                    Base64.decode(payload.string("base64"), Base64.DEFAULT),
                    Charsets.UTF_8
                )
            )

            "base64.dropBytes" -> text(
                Base64.encodeToString(
                    Base64.decode(payload.string("base64"), Base64.DEFAULT)
                        .drop(payload.intOrNull("count") ?: 0)
                        .toByteArray(),
                    Base64.NO_WRAP
                )
            )

            "base64.decodeBytes" -> bytes(
                Base64.decode(payload.string("base64"), Base64.DEFAULT)
            )

            "base64.encodeBytes" -> text(
                Base64.encodeToString(payload.bytes("bytes"), Base64.NO_WRAP)
            )

            "bytes.xor" -> bytes(
                xor(
                    bytes = payload.bytes("bytes"),
                    key = payload.bytes("key")
                )
            )

            "bytes.xorBase64" -> text(
                Base64.encodeToString(
                    xor(
                        bytes = Base64.decode(payload.string("base64"), Base64.DEFAULT),
                        key = payload.bytes("key")
                    ),
                    Base64.NO_WRAP
                )
            )

            "compression.inflateBytesToText" -> text(
                inflate(payload.bytes("bytes"))
            )

            "compression.inflateBase64ToText" -> text(
                inflate(Base64.decode(payload.string("base64"), Base64.DEFAULT))
            )

            /*
             * 旧 API：只返回 body。
             * 为了兼容现有 source.js / 01_http.js，不改变语义。
             */
            "http.getText" -> text(
                executeHttp(
                    method = "GET",
                    payload = payload,
                    binaryResponse = false
                ).bodyText
            )

            "http.postText" -> text(
                executeHttp(
                    method = "POST",
                    payload = payload,
                    binaryResponse = false
                ).bodyText
            )

            "http.postBytes" -> text(
                executeHttp(
                    method = "POST",
                    payload = payload,
                    binaryResponse = true
                ).bodyBase64
            )

            /*
             * 新 API：返回完整响应对象。
             *
             * http.get:
             * {
             *   code: 200,
             *   headers: { "Set-Cookie": ["..."] },
             *   body: "..."
             * }
             *
             * http.getBytes / http.postBytesResponse:
             * {
             *   code: 200,
             *   headers: { "Set-Cookie": ["..."] },
             *   bodyBase64: "..."
             * }
             */
            "http.get" -> value(
                executeHttp(
                    method = "GET",
                    payload = payload,
                    binaryResponse = false
                ).toJsonObject()
            )

            "http.post" -> value(
                executeHttp(
                    method = "POST",
                    payload = payload,
                    binaryResponse = false
                ).toJsonObject()
            )

            "http.getBytes" -> value(
                executeHttp(
                    method = "GET",
                    payload = payload,
                    binaryResponse = true
                ).toJsonObject()
            )

            "http.postBytesResponse" -> value(
                executeHttp(
                    method = "POST",
                    payload = payload,
                    binaryResponse = true
                ).toJsonObject()
            )

            "log.debug" -> {
                Log.d(payload.logTag(), payload.string("message"))
                text("")
            }

            "log.warn" -> {
                Log.w(payload.logTag(), payload.string("message"))
                text("")
            }

            "log.error" -> {
                Log.e(payload.logTag(), payload.string("message"))
                text("")
            }

            else -> error("Unsupported host api: $name")
        }
    }

    private fun executeHttp(
        method: String,
        payload: JsonObject,
        binaryResponse: Boolean
    ): HostHttpResponse {
        val urlText = payload.string("url")
        require(urlText.isNotBlank()) { "HTTP url is blank" }

        val connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = payload.booleanOrNull("followRedirects") ?: true
            connectTimeout = payload.intOrNull("connectTimeoutMs") ?: 8_000
            readTimeout = payload.intOrNull("readTimeoutMs") ?: 12_000

            if (method == "POST" || method == "PUT" || method == "PATCH") {
                doOutput = true
                setRequestProperty(
                    "Content-Type",
                    payload.string("contentType").ifBlank {
                        if (binaryResponse) {
                            "application/octet-stream"
                        } else {
                            "application/json; charset=utf-8"
                        }
                    }
                )
            }

            payload.obj("headers")?.forEach { (key, value) ->
                val headerValue = when (value) {
                    is JsonPrimitive -> value.contentOrNull.orEmpty()
                    is JsonArray -> value.joinToString(", ") {
                        it.jsonPrimitive.contentOrNull.orEmpty()
                    }
                    else -> value.toString()
                }
                setRequestProperty(key, headerValue)
            }
        }

        return try {
            if (method == "POST" || method == "PUT" || method == "PATCH") {
                val bodyBytes = payload.requestBodyBytes()
                connection.outputStream.use { output ->
                    output.write(bodyBytes)
                }
            }

            val code = connection.responseCode
            val responseBytes = connection.responseStream(code).useOrEmpty { stream ->
                stream.readAllBytesCompat()
            }

            val bodyText = if (binaryResponse) {
                ""
            } else {
                responseBytes.toString(Charsets.UTF_8)
            }

            val bodyBase64 = if (binaryResponse) {
                Base64.encodeToString(responseBytes, Base64.NO_WRAP)
            } else {
                ""
            }

            HostHttpResponse(
                code = code,
                message = connection.responseMessage.orEmpty(),
                headers = connection.headerFields.orEmpty()
                    .filterKeys { it != null }
                    .mapKeys { it.key.orEmpty() }
                    .mapValues { it.value.orEmpty() },
                bodyText = bodyText,
                bodyBase64 = bodyBase64
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.requestBodyBytes(): ByteArray {
        val bodyBase64 = string("bodyBase64")
        if (bodyBase64.isNotBlank()) {
            return Base64.decode(bodyBase64, Base64.DEFAULT)
        }

        val bodyBytes = this["bodyBytes"] as? JsonArray
        if (bodyBytes != null) {
            return ByteArray(bodyBytes.size) { index ->
                bodyBytes[index].jsonPrimitive.int.toByte()
            }
        }

        return string("body").toByteArray(Charsets.UTF_8)
    }

    private fun HttpURLConnection.responseStream(code: Int): InputStream? {
        return if (code in 200..299) {
            inputStream
        } else {
            errorStream ?: inputStream
        }
    }

    private inline fun <T> InputStream?.useOrEmpty(block: (InputStream) -> T): T where T : Any {
        return if (this == null) {
            @Suppress("UNCHECKED_CAST")
            ByteArray(0) as T
        } else {
            use(block)
        }
    }

    private fun InputStream.readAllBytesCompat(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(
            text.toByteArray(Charsets.UTF_8)
        )
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun aesEcbPkcs5EncryptBase64(text: String, key: String): String {
        return Base64.encodeToString(
            aesEcbPkcs5Encrypt(text, key),
            Base64.NO_WRAP
        )
    }

    private fun aesEcbPkcs5Encrypt(text: String, key: String): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }

    private fun aesEcbPkcs5DecryptBase64ToText(base64: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return String(
            cipher.doFinal(Base64.decode(base64, Base64.DEFAULT)),
            Charsets.UTF_8
        )
    }

    private fun xor(bytes: ByteArray, key: ByteArray): ByteArray {
        if (key.isEmpty()) return bytes
        return ByteArray(bytes.size) { index ->
            (bytes[index].toInt() xor key[index % key.size].toInt()).toByte()
        }
    }

    private fun inflate(bytes: ByteArray): String {
        val inflater = Inflater()
        inflater.setInput(bytes)

        val buffer = ByteArray(4096)
        val output = ByteArrayOutputStream()

        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                output.write(buffer, 0, count)
            }
        } finally {
            inflater.end()
        }

        return output.toString("UTF-8")
    }

    private fun text(value: String): String {
        return value(JsonPrimitive(value))
    }

    private fun bytes(value: ByteArray): String {
        return value(
            JsonArray(
                value.map { byte ->
                    JsonPrimitive(byte.toInt() and 0xff)
                }
            )
        )
    }

    private fun value(value: JsonElement): String {
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("value", value)
            }
        )
    }

    private data class HostHttpResponse(
        val code: Int,
        val message: String,
        val headers: Map<String, List<String>>,
        val bodyText: String,
        val bodyBase64: String
    ) {
        fun toJsonObject(): JsonObject {
            return buildJsonObject {
                put("code", code)
                put("message", message)

                put(
                    "headers",
                    JsonObject(
                        headers.mapValues { (_, values) ->
                            JsonArray(values.map { JsonPrimitive(it) })
                        }
                    )
                )

                if (bodyText.isNotEmpty()) {
                    put("body", bodyText)
                } else {
                    put("body", "")
                }

                if (bodyBase64.isNotEmpty()) {
                    put("bodyBase64", bodyBase64)
                } else {
                    put("bodyBase64", "")
                }
            }
        }
    }
}

private fun JsonObject.string(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.intOrNull(key: String): Int? {
    return this[key]?.jsonPrimitive?.int
}

private fun JsonObject.booleanOrNull(key: String): Boolean? {
    return this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
}

private fun JsonObject.obj(key: String): JsonObject? {
    return this[key] as? JsonObject
}

private fun JsonObject.bytes(key: String): ByteArray {
    val array = this[key]?.jsonArray ?: return ByteArray(0)
    return ByteArray(array.size) { index ->
        array[index].jsonPrimitive.int.toByte()
    }
}

private fun ByteArray.toHex(): String {
    return joinToString("") { "%02X".format(it) }
}

private fun JsonObject.logTag(): String {
    return string("tag")
        .ifBlank { "LyricoPlugin" }
        .take(48)
}