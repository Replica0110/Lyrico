package com.lonx.lyrico.data.model.lyrics

import kotlinx.serialization.Serializable

enum class SourceConfigFieldType {
    TEXT,
    PASSWORD,
    NUMBER,
    SWITCH,
    DROPDOWN
}

data class SourceConfigOption(
    val value: String,
    val label: String
)

data class SourceConfigField(
    val key: String,
    val title: String,
    val summary: String = "",
    val type: SourceConfigFieldType,
    val required: Boolean = false,
    val defaultValue: String = "",
    val options: List<SourceConfigOption> = emptyList(),
    val dependency: SourceConfigDependency? = null
)

sealed interface SourceConfigDependency {
    data class Match(
        val key: String,
        val value: String
    ) : SourceConfigDependency

    data class And(
        val conditions: List<SourceConfigDependency>
    ) : SourceConfigDependency

    data class Or(
        val conditions: List<SourceConfigDependency>
    ) : SourceConfigDependency

    data class Not(
        val condition: SourceConfigDependency
    ) : SourceConfigDependency
}

@Serializable
data class SourceRuntimeConfig(
    val values: Map<String, String> = emptyMap()
) {
    fun getString(key: String, defaultValue: String = ""): String {
        return values[key].takeUnless { it.isNullOrBlank() } ?: defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return values[key]?.toBooleanStrictOrNull() ?: defaultValue
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return values[key]?.toIntOrNull() ?: defaultValue
    }
}
