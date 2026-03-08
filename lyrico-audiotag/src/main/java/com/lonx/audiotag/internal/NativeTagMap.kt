package com.lonx.audiotag.internal

import androidx.annotation.Keep

@Keep
class NativeTagMap {
    private val map = mutableMapOf<String, List<String>>()

    fun addID(id: String, value: String) {
        addID(id, listOf(value))
    }

    fun addID(id: String, values: List<String>) {
        if (values.isEmpty()) {
            return
        }
        val correctedValues = values.mapNotNull { it.correctWhitespace() }
        if (correctedValues.isEmpty()) {
            return
        }
        map[id] = correctedValues
    }

    fun addCustom(description: String, value: String) {
        addCustom(description, listOf(value))
    }

    fun addCustom(description: String, values: List<String>) {
        if (values.isEmpty()) {
            return
        }
        val correctedValues = values.mapNotNull { it.correctWhitespace() }
        if (correctedValues.isEmpty()) {
            return
        }
        map[description.uppercase()] = correctedValues
    }

    fun addCombined(id: String, description: String, value: String) {
        addCombined(id, description, listOf(value))
    }

    fun addCombined(id: String, description: String, values: List<String>) {
        if (values.isEmpty()) {
            return
        }
        val correctedValues = values.mapNotNull { it.correctWhitespace() }
        if (correctedValues.isEmpty()) {
            return
        }
        map["$id:${description.uppercase()}"] = correctedValues
    }

    fun getObject(): Map<String, List<String>> {
        return map
    }
}
internal fun String.correctWhitespace() = trim().ifBlank { null }