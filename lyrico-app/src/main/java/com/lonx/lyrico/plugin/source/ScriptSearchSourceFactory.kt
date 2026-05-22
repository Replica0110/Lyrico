package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.plugin.PluginManifest
import com.lonx.lyrico.plugin.runtime.PluginJsRuntime
import com.lonx.lyrico.plugin.runtime.QuickJsRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
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
        val script = buildScript(pluginDir, entryFile, manifest)

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

    private fun buildScript(pluginDir: File, entryFile: File, manifest: PluginManifest): String {
        val includeSources = manifest.includeDirs
            .asSequence()
            .map { includeDir -> includeDir to File(pluginDir, includeDir) }
            .filter { (_, dir) -> dir.isDirectory }
            .flatMap { (includeDir, dir) ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("js", ignoreCase = true) }
                    .sortedBy { it.relativeTo(dir).invariantSeparatorsPath }
                    .asSequence()
                    .map { file -> includeDir to file }
            }
            .associate { (includeDir, file) ->
                val relativePath = file.relativeTo(File(pluginDir, includeDir)).invariantSeparatorsPath
                "$includeDir/$relativePath" to file.readText()
            }
        val includeSourcesJson = json.encodeToString(includeSources)
        val includeOrderJson = json.encodeToString(includeSources.keys.toList())
        val includeBootstrap = """
            (function() {
              var __lyricoIncludeSources = $includeSourcesJson;
              var __lyricoIncludeOrder = $includeOrderJson;
              var __lyricoIncluded = Object.create(null);
              globalThis.include = function(path) {
                path = String(path || "");
                if (!Object.prototype.hasOwnProperty.call(__lyricoIncludeSources, path)) {
                  throw new Error("Include path is not declared in includeDirs: " + path);
                }
                if (__lyricoIncluded[path]) return;
                __lyricoIncluded[path] = true;
                (0, eval)(__lyricoIncludeSources[path] + "\n//# sourceURL=" + path);
              };
              __lyricoIncludeOrder.forEach(function(path) {
                globalThis.include(path);
              });
            })();
        """.trimIndent()

        return "$includeBootstrap\n//# sourceURL=${manifest.entry}\n${entryFile.readText()}"
    }
}
