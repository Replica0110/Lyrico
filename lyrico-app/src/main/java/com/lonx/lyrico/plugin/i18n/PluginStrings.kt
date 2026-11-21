package com.lonx.lyrico.plugin.i18n

import com.lonx.lyrico.data.plugin.PluginConfigFieldType
import com.lonx.lyrico.data.plugin.PluginManifest
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/** Deliberately small, portable subset of Android's positional format syntax. */
object PluginStringFormat {
    private val token = Regex("%(?:([1-9][0-9]*)\\$)?([sd%])")
    fun hasArguments(text: String): Boolean = Regex("%(?:[1-9][0-9]*\\$)?[sd]").containsMatchIn(text)

    fun signature(text: String): Map<Int, String> {
        val result = sortedMapOf<Int, String>()
        var offset = 0
        var implicit = 0
        var hasImplicit = false
        var hasExplicit = false
        while (true) {
            val start = text.indexOf('%', offset)
            if (start < 0) break
            val match = token.find(text, start)
            require(match != null && match.range.first == start) { "Unsupported format at $start: $text" }
            offset = match.range.last + 1
            val type = match.groupValues[2]
            val position = match.groupValues[1]
            if (type == "%") {
                require(position.isEmpty()) { "Use %% for a literal percent" }
                continue
            }
            val index = if (position.isEmpty()) {
                hasImplicit = true
                ++implicit
            } else {
                hasExplicit = true
                position.toInt()
            }
            require(index <= 64) { "At most 64 format arguments are supported" }
            require(result[index] == null || result[index] == type) { "Conflicting argument types at $index" }
            result[index] = type
        }
        require(!(hasImplicit && hasExplicit)) { "Do not mix indexed and unindexed placeholders" }
        require(!hasImplicit || implicit <= 1) { "Multiple arguments require positional placeholders" }
        require(result.keys.toList() == (1..result.size).toList()) { "Argument indexes must be contiguous" }
        return result
    }

    fun format(text: String, args: List<Any?>): String {
        val signature = signature(text)
        require(args.size == signature.size) { "Expected ${signature.size} arguments, got ${args.size}" }
        signature.forEach { (index, type) ->
            val value = args[index - 1]
            require(if (type == "s") value is String else
                value is Number && value.toDouble().isFinite() &&
                    kotlin.math.abs(value.toDouble()) <= 9007199254740991.0 &&
                    value.toDouble() == value.toLong().toDouble()) { "Invalid argument $index for %$type" }
        }
        var implicit = 0
        return token.replace(text) { match ->
            val type = match.groupValues[2]
            if (type == "%") "%" else {
                val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: implicit++
                if (type == "d") (args[index] as Number).toLong().toString() else args[index] as String
            }
        }
    }
}

class PluginStrings private constructor(
    private val defaultLocale: String,
    private val resources: Map<String, Map<String, String>>
) {
    fun snapshot(preferences: List<String>): Snapshot {
        val available = resources.keys.sorted()
        val selected = preferences.firstNotNullOfOrNull { preference ->
            val wanted = Locale.forLanguageTag(preference)
            available.firstOrNull { it.equals(wanted.toLanguageTag(), true) }
                ?: available.filter {
                    val candidate = Locale.forLanguageTag(it)
                    candidate.language == wanted.language && script(candidate) == script(wanted)
                }.sortedWith(compareBy<String> { Locale.forLanguageTag(it).country.isNotEmpty() }
                    .thenBy { it }).firstOrNull()
        } ?: defaultLocale
        val locale = Locale.forLanguageTag(selected)
        val parents = listOfNotNull(
            if (locale.country.isNotEmpty()) listOf(locale.language, locale.script).filter(String::isNotEmpty).joinToString("-") else null,
            locale.language.takeIf { script(Locale.forLanguageTag(it)) == script(locale) }
        )
        return Snapshot(selected, (listOf(selected) + parents + defaultLocale).distinct().mapNotNull(resources::get))
    }

    class Snapshot(val locale: String, private val chain: List<Map<String, String>>) {
        /** Resolve once. Neither literal text nor catalog values are implicitly formatted. */
        fun text(value: String): String = when {
            value.startsWith("@@") -> value.drop(1)
            value.startsWith('@') -> lookup(value.drop(1))
            else -> value
        }

        private fun lookup(key: String): String = chain.firstNotNullOfOrNull { it[key] }
            ?: error("Unknown plugin string: $key")

        fun format(key: String, args: List<Any?>): String {
            val template = lookup(key)
            return if (args.isEmpty()) template else PluginStringFormat.format(template, args)
        }

        fun localize(manifest: PluginManifest): PluginManifest = manifest.copy(
            name = text(manifest.name),
            description = text(manifest.description),
            configFields = manifest.configFields.map { field ->
                field.copy(
                    title = text(field.title),
                    summary = field.summary?.let(::text),
                    groupTitle = text(field.group),
                    defaultValue = if (field.type == PluginConfigFieldType.MARKDOWN)
                        text(field.defaultValue.ifBlank { field.summary.orEmpty() }) else field.defaultValue,
                    options = field.options.map { option -> option.copy(
                        label = text(option.label),
                        summary = text(option.summary)
                    ) }
                )
            }
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private const val MAX_BYTES = 512 * 1024L

        // Script inference is injected by Android ICU; pure JVM tests also cover Chinese explicitly.
        var inferScript: (Locale) -> String = { locale ->
            if (locale.language == "zh") {
                if (locale.country in setOf("TW", "HK", "MO")) "Hant" else "Hans"
            } else ""
        }
        private fun script(locale: Locale) = locale.script.ifEmpty { inferScript(locale) }

        fun load(root: File, manifest: PluginManifest): PluginStrings {
            val references = buildList {
                add(manifest.name); add(manifest.description)
                manifest.configFields.forEach { field ->
                    add(field.title); add(field.summary.orEmpty()); add(field.group)
                    if (field.type == PluginConfigFieldType.MARKDOWN) add(field.defaultValue)
                    field.options.forEach { add(it.label); add(it.summary) }
                }
            }.filter { it.startsWith('@') && !it.startsWith("@@") }.map { it.drop(1) }.toSet()
            require(references.none { it.isBlank() }) { "Empty plugin string reference" }
            val spec = manifest.i18n ?: run {
                require(references.isEmpty()) { "String references require i18n resources" }
                return PluginStrings("und", emptyMap())
            }
            require(references.isEmpty() || manifest.minHostApiVersion >= 4) { "String references require minHostApiVersion >= 4" }
            require(spec.resources.size in 1..64) { "Expected 1..64 plugin locales" }
            require(spec.defaultLocale in spec.resources) { "Default locale resource is missing" }
            val canonicalRoot = root.canonicalFile
            val catalogs = spec.resources.mapValues { (tag, path) ->
                require(Locale.Builder().setLanguageTag(tag).build().toLanguageTag() == tag && tag != "und") {
                    "Use a canonical BCP 47 locale: $tag"
                }
                require(!File(path).isAbsolute && !path.contains('\\') && path.split('/').none { it == ".." || it.isEmpty() }) { "Unsafe locale path: $path" }
                val file = File(canonicalRoot, path).canonicalFile
                require(file.toPath().startsWith(canonicalRoot.toPath()) && file.isFile && file.extension == "json") { "Invalid locale resource: $path" }
                require(file.length() <= MAX_BYTES) { "Locale resource exceeds 512 KiB: $path" }
                json.decodeFromString<Map<String, String>>(file.readText()).also { values ->
                    values.forEach { (key, _) ->
                        require(key.isNotBlank()) { "Empty resource key" }
                    }
                }
            }
            val defaults = catalogs.getValue(spec.defaultLocale)
            // Static UI strings may contain literal percentages and URL escapes. Only
            // non-UI resources with recognizable placeholders are format-checked here.
            val formattedKeys = catalogs.values.flatMap { values ->
                values.filter { (key, value) -> key !in references && PluginStringFormat.hasArguments(value) }.keys
            }.toSet()
            catalogs.forEach { (tag, values) -> values.forEach { (key, value) ->
                require(key in defaults) { "$tag/$key is absent from default resources" }
                require(key !in formattedKeys || PluginStringFormat.signature(value) == PluginStringFormat.signature(defaults.getValue(key))) {
                    "$tag/$key has incompatible placeholders"
                }
            } }
            references.forEach { key ->
                require(key in defaults) { "Missing default string: $key" }
            }
            return PluginStrings(spec.defaultLocale, catalogs)
        }
    }
}
