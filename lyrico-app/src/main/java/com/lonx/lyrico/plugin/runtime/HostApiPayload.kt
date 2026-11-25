package com.lonx.lyrico.plugin.runtime

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun JsonObject.string(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

internal fun JsonObject.intOrNull(key: String): Int? {
    return this[key]?.jsonPrimitive?.int
}

internal fun JsonObject.longOrNull(key: String): Long? {
    return this[key]?.jsonPrimitive?.longOrNull
}

internal fun JsonObject.obj(key: String): JsonObject? {
    return this[key] as? JsonObject
}
