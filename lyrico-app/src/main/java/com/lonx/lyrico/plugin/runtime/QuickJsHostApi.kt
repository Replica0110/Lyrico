package com.lonx.lyrico.plugin.runtime

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.Inflater

class QuickJsHostApi(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun call(name: String, payloadJson: String): String {
        val payload = runCatching { json.parseToJsonElement(payloadJson).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))

        return when (name) {
            "crypto.md5" -> text(md5(payload.string("text")))
            "base64.encodeText" -> text(
                Base64.encodeToString(payload.string("text").toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            )
            "base64.decodeText" -> text(
                String(Base64.decode(payload.string("base64"), Base64.DEFAULT), Charsets.UTF_8)
            )
            "base64.dropBytes" -> text(
                Base64.encodeToString(
                    Base64.decode(payload.string("base64"), Base64.DEFAULT)
                        .drop(payload.intOrNull("count") ?: 0)
                        .toByteArray(),
                    Base64.NO_WRAP
                )
            )
            "base64.decodeBytes" -> bytes(Base64.decode(payload.string("base64"), Base64.DEFAULT))
            "base64.encodeBytes" -> text(Base64.encodeToString(payload.bytes("bytes"), Base64.NO_WRAP))
            "bytes.xor" -> bytes(xor(payload.bytes("bytes"), payload.bytes("key")))
            "bytes.xorBase64" -> text(
                Base64.encodeToString(
                    xor(Base64.decode(payload.string("base64"), Base64.DEFAULT), payload.bytes("key")),
                    Base64.NO_WRAP
                )
            )
            "compression.inflateBytesToText" -> text(inflate(payload.bytes("bytes")))
            "compression.inflateBase64ToText" -> text(
                inflate(Base64.decode(payload.string("base64"), Base64.DEFAULT))
            )
            "http.getText" -> text(httpGetText(payload))
            else -> error("Unsupported host api: $name")
        }
    }

    private fun httpGetText(payload: JsonObject): String {
        val url = URL(payload.string("url"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = payload.intOrNull("connectTimeoutMs") ?: 8000
            readTimeout = payload.intOrNull("readTimeoutMs") ?: 12000
            payload.obj("headers")?.forEach { (key, value) ->
                setRequestProperty(key, value.jsonPrimitive.contentOrNull.orEmpty())
            }
        }

        return try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
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
        val output = java.io.ByteArrayOutputStream()
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
        return json.encodeToString(JsonObject.serializer(), buildJsonObject { put("value", value) })
    }

    private fun bytes(value: ByteArray): String {
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("value", JsonArray(value.map { JsonPrimitive(it.toInt() and 0xff) }))
            }
        )
    }
}

private fun JsonObject.string(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.intOrNull(key: String): Int? {
    return this[key]?.jsonPrimitive?.int
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
