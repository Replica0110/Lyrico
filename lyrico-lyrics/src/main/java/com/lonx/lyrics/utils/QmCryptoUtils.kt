package com.lonx.lyrics.utils


import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

object QmCryptoUtils {
    private const val TAG = "QmCryptoUtils"
    // QQ 音乐云端歌词标准 Key (24 bytes)
    private const val QRC_KEY_STR = "!@#)(*$%123ZXC!@!@#)(NHL"

    fun decryptQrc(rawHexString: String): String {
        try {
            // 1. 数据清洗
            val hexString = rawHexString.replace(Regex("[^0-9A-Fa-f]"), "")
            if (hexString.isEmpty()) return ""

            val encryptedBytes = hexStringToByteArray(hexString)
            if (encryptedBytes.size % 8 != 0) {
                Log.e(TAG, "Encrypted bytes size not multiple of 8")
                return ""
            }

            // 2. 自定义 3DES 解密 (替换标准库)
            val keyBytes = QRC_KEY_STR.toByteArray(Charsets.UTF_8)

            // 初始化密钥调度表 (耗时操作，可考虑缓存)
            val schedules = TripleDesCustom.tripleDesKeySetup(keyBytes, TripleDesCustom.DECRYPT)

            // 执行解密
            val decryptedBytes = TripleDesCustom.tripleDesCrypt(encryptedBytes, schedules)

            // 调试：检查解密头是否为 78 xx
            if (decryptedBytes.isNotEmpty()) {
                Log.d(TAG, "Decrypted Header: %02X %02X".format(decryptedBytes[0], decryptedBytes[1]))
            }

            // 3. Zlib 解压
            return decompress(decryptedBytes)

        } catch (e: Exception) {
            Log.e(TAG, "Decrypt Error", e)
            return ""
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val h = Character.digit(s[i], 16)
            val l = Character.digit(s[i + 1], 16)
            if (h == -1 || l == -1) { i += 2; continue }
            data[i / 2] = ((h shl 4) + l).toByte()
            i += 2
        }
        return data
    }

    private fun decompress(data: ByteArray): String {
        val inflater = Inflater(false)
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream(data.size * 2)
        try {
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput()) break
                    if (inflater.needsDictionary()) break
                }
                outputStream.write(buffer, 0, count)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Zlib Decompress failed: ${e.message}")
            // 如果 3DES 解密还是错的，这里依然会爆 DataFormatException
            return ""
        } finally {
            outputStream.close()
            inflater.end()
        }
        return outputStream.toString("UTF-8")
    }
}