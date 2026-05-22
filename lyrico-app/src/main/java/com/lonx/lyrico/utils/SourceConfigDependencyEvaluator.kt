package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.plugin.PluginConfigDependency

fun PluginConfigDependency?.isSatisfied(values: Map<String, String>): Boolean {
    return when (this) {
        null -> true
        is PluginConfigDependency.Match -> values[key] == value
        is PluginConfigDependency.And -> conditions.all { it.isSatisfied(values) }
        is PluginConfigDependency.Or -> conditions.any { it.isSatisfied(values) }
        is PluginConfigDependency.Not -> !condition.isSatisfied(values)
    }
}
