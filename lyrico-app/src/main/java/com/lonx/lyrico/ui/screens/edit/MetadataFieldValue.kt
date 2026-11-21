package com.lonx.lyrico.ui.screens.edit

import androidx.compose.foundation.text.input.TextFieldState
import com.lonx.audiotag.model.AudioTagData

/** 一个单行标签字段的输入状态，以及它和 [AudioTagData] 之间的读写映射。 */
internal data class SimpleTextField(
    val state: TextFieldState,
    val valueOf: (AudioTagData) -> String?,
    val write: AudioTagData.(String?) -> AudioTagData,
)

internal fun editedValue(data: AudioTagData?, field: SimpleTextField): String =
    data?.let(field.valueOf).orEmpty()

/** 比较两个字符串是否相等，将 null 和空字符串视为相等。 */
internal fun String?.isEqualIgnoringBlank(other: String?): Boolean {
    val thisBlank = this.isNullOrBlank()
    val otherBlank = other.isNullOrBlank()
    return if (thisBlank && otherBlank) {
        true
    } else if (!thisBlank && !otherBlank) {
        this == other
    } else {
        false
    }
}
