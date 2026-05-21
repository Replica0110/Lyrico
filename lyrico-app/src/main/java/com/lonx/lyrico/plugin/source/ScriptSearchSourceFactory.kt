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
        val script = buildScript(pluginDir, entryFile)

        ScriptSearchSource(
            manifest = manifest,
            script = script,
            json = json,
            runtimeFactory = runtimeFactory
        )
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val LIB_DIR = "lib"
    }

    private fun buildScript(pluginDir: File, entryFile: File): String {
        val libDir = File(pluginDir, LIB_DIR)
        val libScripts = libDir
            .takeIf { it.isDirectory }
            ?.walkTopDown()
            ?.filter { it.isFile && it.extension.equals("js", ignoreCase = true) }
            ?.sortedBy { it.relativeTo(libDir).invariantSeparatorsPath }
            ?.map { file ->
                "\n//# sourceURL=lib/${file.relativeTo(libDir).invariantSeparatorsPath}\n${file.readText()}\n"
            }
            ?.joinToString(separator = "\n")
            .orEmpty()

        return "$libScripts\n//# sourceURL=${entryFile.name}\n${entryFile.readText()}"
    }
}
