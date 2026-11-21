package com.lonx.lyrico.data.song.mapper

import com.github.promeg.pinyinhelper.Pinyin

object SortKeyUtils {

    data class SortKeys(
        val groupKey: String,
        val sortKey: String
    )

    fun getSortKeys(text: String): SortKeys {
        val raw = text.trim()
        if (raw.isBlank()) {
            return SortKeys("#", "2_")
        }

        val firstChar = raw[0]

        if (firstChar.isDigit()) {
            return SortKeys("0", "0_$raw")
        }

        if (firstChar.isLetter() && firstChar.code < 128) {
            val sortKey = raw.uppercase()
            val groupKey = sortKey[0].toString()
            return SortKeys(groupKey, "1_$sortKey")
        }

        val pinyinFull = try {
            Pinyin.toPinyin(raw, "")
        } catch (e: Exception) {
            ""
        }

        if (pinyinFull.isNotBlank()) {
            val sortKey = pinyinFull.uppercase()
            val firstPinyinChar = sortKey[0]

            if (firstPinyinChar in 'A'..'Z') {
                return SortKeys(
                    firstPinyinChar.toString(),
                    "1_$sortKey"
                )
            }
        }

        return SortKeys("#", "2_$raw")
    }
}
