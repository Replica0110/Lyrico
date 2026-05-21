package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.plugin.PluginManifest
import com.lonx.lyrico.plugin.runtime.PluginJsRuntime
import com.lonx.lyrico.plugin.runtime.QuickJsRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class ScriptSearchSourceFactory(
    private val json: Json,
    private val runtimeFactory: () -> PluginJsRuntime = { QuickJsRuntime() }
) {
    suspend fun create(plugin: SourcePluginEntity): ScriptSearchSource = withContext(Dispatchers.IO) {
        val pluginDir = File(plugin.pluginDir)
        val manifestFile = File(pluginDir, MANIFEST_FILE)
        val manifest = json.decodeFromString<PluginManifest>(manifestFile.readText())
        val entryFile = File(pluginDir, plugin.entryFile.ifBlank { manifest.entry })
        val script = entryFile.readText()

        ScriptSearchSource(
            manifest = manifest,
            script = script,
            json = json,
            runtimeFactory = runtimeFactory
        )
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
    }
}
