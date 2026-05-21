package com.lonx.lyrico.plugin.runtime

import android.util.Base64
import android.util.Log
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
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class QuickJsHostApi(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun call(name: String, payloadJson: String): String {
        val payload = runCatching { json.parseToJsonElement(payloadJson).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))

        return when (name) {
            "crypto.md5" -> text(md5(payload.string("text")))
            "crypto.aesEcbPkcs5EncryptBase64" -> text(aesEcbPkcs5EncryptBase64(payload.string("text"), payload.string("key")))
            "crypto.aesEcbPkcs5EncryptHex" -> text(aesEcbPkcs5Encrypt(payload.string("text"), payload.string("key")).toHex())
            "crypto.aesEcbPkcs5DecryptBase64ToText" -> text(aesEcbPkcs5DecryptBase64ToText(payload.string("base64"), payload.string("key")))
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
            "http.postText" -> text(httpPostText(payload))
            "http.postBytes" -> text(httpPostBytes(payload))
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

    private fun httpPostText(payload: JsonObject): String {
        val url = URL(payload.string("url"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = payload.intOrNull("connectTimeoutMs") ?: 8000
            readTimeout = payload.intOrNull("readTimeoutMs") ?: 12000
            setRequestProperty("Content-Type", payload.string("contentType").ifBlank { "application/json; charset=utf-8" })
            payload.obj("headers")?.forEach { (key, value) ->
                setRequestProperty(key, value.jsonPrimitive.contentOrNull.orEmpty())
            }
        }

        return try {
            connection.outputStream.use { output ->
                output.write(payload.string("body").toByteArray(Charsets.UTF_8))
            }
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

    private fun httpPostBytes(payload: JsonObject): String {
        val url = URL(payload.string("url"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = payload.intOrNull("connectTimeoutMs") ?: 8000
            readTimeout = payload.intOrNull("readTimeoutMs") ?: 12000
            setRequestProperty("Content-Type", payload.string("contentType").ifBlank { "application/octet-stream" })
            payload.obj("headers")?.forEach { (key, value) ->
                setRequestProperty(key, value.jsonPrimitive.contentOrNull.orEmpty())
            }
        }

        return try {
            connection.outputStream.use { output ->
                output.write(payload.string("body").toByteArray(Charsets.UTF_8))
            }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            Base64.encodeToString(stream.use { it.readBytes() }, Base64.NO_WRAP)
        } finally {
            connection.disconnect()
        }
    }

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun aesEcbPkcs5EncryptBase64(text: String, key: String): String {
        return Base64.encodeToString(aesEcbPkcs5Encrypt(text, key), Base64.NO_WRAP)
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
        return String(cipher.doFinal(Base64.decode(base64, Base64.DEFAULT)), Charsets.UTF_8)
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

private fun ByteArray.toHex(): String {
    return joinToString("") { "%02X".format(it) }
}

private fun JsonObject.logTag(): String {
    return string("tag")
        .ifBlank { "LyricoPlugin" }
        .take(48)
}
