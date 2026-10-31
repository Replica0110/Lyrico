package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import com.lonx.lyrico.R
import kotlinx.serialization.Serializable

/**
 * 字符映射规则。
 * @param charMappings 原字符 → 替换字符；value 为 `null` 表示移除。
 */
@Serializable
data class CharacterMappingRule(
    val id: String,
    val name: String,
    val charMappings: Map<String, String?> = emptyMap(),
    val description: String = "",
    val isBuiltIn: Boolean = false,
    val isEnabled: Boolean = true
)

/** 字符映射规则列表。 */
@Serializable
data class CharacterMappingConfig(
    val rules: List<CharacterMappingRule> = emptyList()
)
enum class ReplacementCharOption(
    val value: String,
    @field:StringRes val labelRes: Int
) {
    REMOVE(
        value = "",
        labelRes = R.string.replacement_remove
    ),

    IDEOGRAPHIC_COMMA(
        value = "、",
        labelRes = R.string.replacement_ideographic_comma
    ),

    HALF_WIDTH_COMMA(
        value = ",",
        labelRes = R.string.replacement_half_width_comma
    ),

    FULL_WIDTH_COMMA(
        value = "，",
        labelRes = R.string.replacement_full_width_comma
    ),

    FULL_WIDTH_BACKSLASH(
        value = "＼",
        labelRes = R.string.replacement_full_width_backslash
    ),

    FULL_WIDTH_SLASH(
        value = "／",
        labelRes = R.string.replacement_full_width_slash
    ),

    FULL_WIDTH_COLON(
        value = "：",
        labelRes = R.string.replacement_full_width_colon
    ),

    FULL_WIDTH_ASTERISK(
        value = "＊",
        labelRes = R.string.replacement_full_width_asterisk
    ),

    FULL_WIDTH_QUESTION_MARK(
        value = "？",
        labelRes = R.string.replacement_full_width_question_mark
    ),

    FULL_WIDTH_QUOTATION_MARK(
        value = "＂",
        labelRes = R.string.replacement_full_width_quotation_mark
    ),

    FULL_WIDTH_LESS_THAN_SIGN(
        value = "＜",
        labelRes = R.string.replacement_full_width_less_than_sign
    ),

    FULL_WIDTH_GREATER_THAN_SIGN(
        value = "＞",
        labelRes = R.string.replacement_full_width_greater_than_sign
    ),

    FULL_WIDTH_VERTICAL_BAR(
        value = "｜",
        labelRes = R.string.replacement_full_width_vertical_bar
    ),

    AMPERSAND(
        value = "&",
        labelRes = R.string.replacement_ampersand
    );

    companion object {
        fun fromValue(value: String?): ReplacementCharOption? {
            return entries.find { it.value == value }
        }
    }
}
fun String?.toReplacementOption(): ReplacementCharOption? {
    return ReplacementCharOption.fromValue(this)
}
object CharacterMappingDefaults {
    val DEFAULT_INVALID_CHARS = CharacterMappingRule(
        id = "default_invalid_chars",
        name = "文件系统非法字符",
        charMappings = mapOf(
            "\\" to "＼",
            "/" to "／",
            ":" to "：",
            "*" to "＊",
            "?" to "？",
            "\"" to "＂",
            "<" to "＜",
            ">" to "＞",
            "|" to "｜"
        ),
        description = "Windows/Linux文件系统中的非法字符",
        isBuiltIn = true,
        isEnabled = true
    )

    val ALL_BUILTIN_RULES = listOf(
        DEFAULT_INVALID_CHARS
    )
}
