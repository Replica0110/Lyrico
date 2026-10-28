package com.lonx.lyrico.plugin.i18n

import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.plugin.PluginManifest
import kotlinx.serialization.json.Json
import java.io.File

/** Resolve diagnostic names without letting damaged resources hide the original failure. */
internal object PluginNames {
    private val json = Json { ignoreUnknownKeys = true }

    fun resolve(manifest: PluginManifest, root: File): String = try {
        PluginStrings.load(root, manifest).snapshot(PluginLocales.preferences.value).text(manifest.name)
            .takeIf { it.isNotBlank() } ?: fallback(manifest.name, manifest.id)
    } catch (_: Exception) {
        fallback(manifest.name, manifest.id)
    }

    fun resolve(plugin: SourcePluginEntity): String {
        plugin.customName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return try {
            val root = File(plugin.pluginDir)
            val manifest = json.decodeFromString<PluginManifest>(File(root, "manifest.json").readText())
            resolve(manifest, root)
        } catch (_: Exception) {
            fallback(plugin.name, plugin.id)
        }
    }

    private fun fallback(name: String, id: String): String = when {
        name.startsWith("@@") -> name.drop(1)
        name.startsWith('@') || name.isBlank() -> id
        else -> name
    }
}
