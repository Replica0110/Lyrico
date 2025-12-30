package com.lonx.lyrics.utils

import android.util.Base64
import java.security.MessageDigest
import java.util.zip.Inflater

object KgCryptoUtils {

    // Python: hashlib.md5(str.encode()).hexdigest()
    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // 酷狗 API 签名生成
    // Python逻辑: md5(salt + sorted_params_string + body + salt)
    fun signParams(params: Map<String, Any>, body: String = "", salt: String = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"): String {
        val sortedString = params.toSortedMap()
            .entries.joinToString("") { "${it.key}=${it.value}" }

        val raw = "$salt$sortedString$body$salt"
        return md5(raw)
    }

    // KRC 解密
    // KRC 是酷狗特有的加密格式：4字节头部 + (XOR异或加密 -> Zlib压缩)
    fun decryptKrc(base64Content: String): String {
        try {
            val encryptedBytes = Base64.decode(base64Content, Base64.DEFAULT)
            if (encryptedBytes.size <= 4) return ""

            // 跳过前4个字节 (Header: 'krc1')
            val dataBytes = encryptedBytes.copyOfRange(4, encryptedBytes.size)
            val key = byteArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, -50, -46, 110, 105)

            // XOR 解密
            for (i in dataBytes.indices) {
                dataBytes[i] = (dataBytes[i].toInt() xor key[i % key.size].toInt()).toByte()
            }

            // Zlib 解压
            val inflater = Inflater()
            inflater.setInput(dataBytes)
            val buffer = ByteArray(4096)
            val outputStream = java.io.ByteArrayOutputStream()

            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                outputStream.write(buffer, 0, count)
            }
            inflater.end()

            return outputStream.toString("UTF-8")
        } catch (e: Exception) {
            e.printStackTrace()
            return "" // 解密失败返回空
        }
    }
}