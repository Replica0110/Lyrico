package com.lonx.lyrico.data.song.mapper

class RawPropertiesFormatter {
    fun format(rawProperties: Map<String, Array<String>>?): String? {
        if (rawProperties == null) return null
        return rawProperties.entries.joinToString(
            prefix = "{",
            postfix = "}"
        ) { (key, values) ->
            "$key=${values.joinToString("; ")}"
        }
    }
}
