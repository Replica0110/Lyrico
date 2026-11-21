package com.lonx.lyrico.domain.rename

import com.lonx.lyrico.data.rename.CharacterMappingRule

/** 按映射规则清理文件名中的非法字符。 */
object FileNameSanitizer {

    /**
     * 按 [rules]（优先级从高到低）清理文件名。
     */
    fun sanitize(fileName: String, rules: List<CharacterMappingRule>): String {
        var result = fileName

        for (rule in rules) {
            if (!rule.isEnabled) continue
            result = replaceCharacters(result, rule.charMappings)
        }

        return result.trim()
    }

    /**
     * 将指定的字符替换为对应的目标字符
     */
    private fun replaceCharacters(
        input: String,
        charMappings: Map<String, String?>
    ): String {
        var result = input

        // 从后向前替换，避免索引偏移
        val indices = mutableListOf<Pair<IntRange, String?>>() // IntRange = 连续字符范围，String? = 替换值
        var i = 0

        while (i < result.length) {
            val char = result[i].toString()
            if (char in charMappings) {
                val start = i
                val replacement = charMappings[char]
                // 连续相同映射的字符可以合并
                while (i < result.length && charMappings[result[i].toString()] == replacement) {
                    i++
                }
                indices.add(IntRange(start, i - 1) to replacement)
            } else {
                i++
            }
        }

        // 从后向前替换
        for ((range, replacement) in indices.asReversed()) {
            val replaceStr = replacement ?: ""
            result = result.replaceRange(range.first, range.last + 1, replaceStr)
        }

        return result
    }
}
