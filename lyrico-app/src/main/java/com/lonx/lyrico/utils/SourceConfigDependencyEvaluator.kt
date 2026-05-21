package com.lonx.lyrico.utils

import com.lonx.lyrics.model.SourceConfigDependency

fun SourceConfigDependency?.isSatisfied(values: Map<String, String>): Boolean {
    return when (this) {
        null -> true
        is SourceConfigDependency.Match -> values[key] == value
        is SourceConfigDependency.And -> conditions.all { it.isSatisfied(values) }
        is SourceConfigDependency.Or -> conditions.any { it.isSatisfied(values) }
        is SourceConfigDependency.Not -> !condition.isSatisfied(values)
    }
}
