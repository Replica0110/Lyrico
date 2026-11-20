package com.lonx.lyrico.plugin.runtime

import android.util.Base64
import com.lonx.lyrico.data.log.AppLog
import com.lonx.lyrico.data.log.AppLogType
import com.lonx.lyrico.data.log.AppLogLevel
import androidx.annotation.Keep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import okhttp3.OkHttpClient

@Keep
class QuickJsHostApi(
    private val appInfo: HostAppInfo = HostAppInfo(),
    private val runtimeInfo: HostRuntimeInfo = HostRuntimeInfo(),
    okHttpClient: OkHttpClient = OkHttpClient(),
    private val pluginId: String = "default",
    cacheRootDir: File? = null,
    private val pluginStrings: com.lonx.lyrico.plugin.i18n.PluginStrings? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
) {
    private val cache = PluginCache(pluginId, cacheRootDir, json)
    private val http = PluginHttpClient(appInfo, okHttpClient, pluginId)

    fun resetCancellation() { http.resetCancellation() }

    fun cancelInvocation() { http.cancelInvocation() }

    private var stringsSnapshot: com.lonx.lyrico.plugin.i18n.PluginStrings.Snapshot? = null

    fun beginInvocation(timeoutMs: Long) {
        http.beginInvocation(timeoutMs)
        stringsSnapshot = pluginStrings?.snapshot(com.lonx.lyrico.plugin.i18n.PluginLocales.preferences.value)
    }

    fun call(name: String, payloadJson: String): String {
        val payload = runCatching {
            json.parseToJsonElement(payloadJson).jsonObject
        }.getOrDefault(JsonObject(emptyMap()))

        return when (name) {
            "i18n.getLocale" -> text(stringsSnapshot?.locale ?: "und")

            "i18n.t" -> text(requireNotNull(stringsSnapshot) { "Plugin has no string resources" }.format(
                payload.string("key"),
                (payload["args"] as? JsonArray).orEmpty().map { element ->
                    val primitive = element as? JsonPrimitive
                    if (primitive?.isString == true) primitive.content else primitive?.longOrNull
                }
            ))

            "app.info" -> value(appInfo.toJsonObject())

            "app.userAgent" -> text(buildDefaultUserAgent(appInfo))

            "runtime.info" -> value(runtimeInfo.toJsonObject())

            "cache.get" -> text(cache.get(payload.string("key")))

            "cache.set" -> {
                cache.set(
                    key = payload.string("key"),
                    value = payload.string("value"),
                    ttlMs = payload.longOrNull("ttlMs")
                )
                text("")
            }

            "cache.remove" -> {
                cache.remove(payload.string("key"))
                text("")
            }

            "cache.clear" -> {
                cache.clear()
                text("")
            }

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

            "base64.encodeUrlText" -> text(
                Base64.encodeToString(
                    payload.string("text").toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            )

            "base64.decodeUrlText" -> text(
                String(
                    Base64.decode(fromBase64Url(payload.string("base64Url")), Base64.DEFAULT),
                    Charsets.UTF_8
                )
            )

            "base64.encodeUrlBytes" -> text(
                Base64.encodeToString(
                    payload.bytes("bytes"),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            )

            "base64.decodeUrlBytes" -> bytes(
                Base64.decode(fromBase64Url(payload.string("base64Url")), Base64.DEFAULT)
            )

            "base64.toUrl" -> text(
                toBase64Url(payload.string("base64"))
            )

            "base64.fromUrl" -> text(
                fromBase64Url(payload.string("base64Url"))
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
                http.execute(
                    method = "GET",
                    payload = payload,
                    binaryResponse = false
                ).bodyText
            )

            "http.postText" -> text(
                http.execute(
                    method = "POST",
                    payload = payload,
                    binaryResponse = false
                ).bodyText
            )

            "http.postBytes" -> text(
                http.execute(
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
                http.execute(
                    method = "GET",
                    payload = payload,
                    binaryResponse = false
                ).toJsonObject()
            )

            "http.post" -> value(
                http.execute(
                    method = "POST",
                    payload = payload,
                    binaryResponse = false
                ).toJsonObject()
            )

            "http.getBytes" -> value(
                http.execute(
                    method = "GET",
                    payload = payload,
                    binaryResponse = true
                ).toJsonObject()
            )

            "http.postBytesResponse" -> value(
                http.execute(
                    method = "POST",
                    payload = payload,
                    binaryResponse = true
                ).toJsonObject()
            )
            "xml.getRootAttributes" -> value(
                HostXmlApi.getRootAttributes(
                    xml = payload.string("xml")
                )
            )

            "xml.findElements" -> value(
                HostXmlApi.findElements(
                    xml = payload.string("xml"),
                    query = payload.obj("query") ?: JsonObject(emptyMap()),
                    json = json
                )
            )

            "xml.replaceChildrenByAttr" -> text(
                HostXmlApi.replaceChildrenByAttr(
                    xml = payload.string("xml"),
                    options = payload.obj("options") ?: JsonObject(emptyMap())
                )
            )

            "xml.removeElements" -> text(
                HostXmlApi.removeElements(
                    xml = payload.string("xml"),
                    query = payload.obj("query") ?: JsonObject(emptyMap())
                )
            )
            "log.debug" -> {
                AppLog.record(AppLogType.PLUGIN, payload.logTag(), "Plugin script debug", payload.string("message"), pluginId)
                text("")
            }

            "log.warn" -> {
                AppLog.record(AppLogType.PLUGIN, payload.logTag(), "Plugin script warning", payload.string("message"), pluginId, AppLogLevel.WARNING)
                text("")
            }

            "log.error" -> {
                AppLog.record(AppLogType.PLUGIN, payload.logTag(), "Plugin script error", payload.string("message"), pluginId, AppLogLevel.ERROR)
                text("")
            }

            else -> error("Unsupported host api: $name")
        }
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

    private fun toBase64Url(base64: String): String {
        return base64
            .trim()
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
    }

    private fun fromBase64Url(base64Url: String): String {
        val normalized = base64Url
            .trim()
            .replace('-', '+')
            .replace('_', '/')

        val padding = when (normalized.length % 4) {
            0 -> ""
            2 -> "=="
            3 -> "="
            else -> ""
        }

        return normalized + padding
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

    private fun HostHttpResponse.toJsonObject(): JsonObject {
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

            put("body", bodyText)
            put("bodyBase64", bodyBase64)
        }
    }

}

data class HostAppInfo(
    val name: String = "Lyrico",
    val packageName: String = "com.lonx.lyrico",
    val versionName: String = "0.0.0",
    val versionCode: Long = 0,
    val buildType: String = "unknown",
    val debug: Boolean = false
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("packageName", packageName)
            put("versionName", versionName)
            put("versionCode", versionCode)
            put("buildType", buildType)
            put("debug", debug)
        }
    }
}

data class HostRuntimeInfo(
    val pluginApiVersion: Int = HostApiRegistry.PLUGIN_PROTOCOL_VERSION,
    val hostApiVersion: Int = HostApiRegistry.PLATFORM_API_VERSION,
    val engine: String = "quickjs",
    val engineVersion: String? = null,
    val supportedHostApis: Set<String> = HostApiRegistry.SUPPORTED_HOST_APIS
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("pluginApiVersion", pluginApiVersion)
            put("hostApiVersion", hostApiVersion)
            put("engine", engine)
            if (engineVersion != null) {
                put("engineVersion", engineVersion)
            } else {
                put("engineVersion", JsonNull)
            }
            put(
                "supportedHostApis",
                JsonArray(supportedHostApis.sorted().map { JsonPrimitive(it) })
            )
        }
    }
}

fun buildDefaultUserAgent(appInfo: HostAppInfo): String {
    return "${appInfo.name}/${appInfo.versionName}"
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
        .ifBlank { "PlatformPlugin" }
        .take(48)
}

internal fun md5(text: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(
        text.toByteArray(Charsets.UTF_8)
    )
    return digest.joinToString("") { "%02x".format(it) }
}
