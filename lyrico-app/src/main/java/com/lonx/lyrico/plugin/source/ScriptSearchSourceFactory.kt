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
    suspend fun create(plugin: SourcePluginEntity): ScriptSearchSource =
        withContext(Dispatchers.IO) {
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
                    .map { file -> includeDir to file }
            }
            .map { (includeDir, file) ->
                val relativePath =
                    file.relativeTo(File(pluginDir, includeDir)).invariantSeparatorsPath
                IncludedScript(
                    path = "$includeDir/$relativePath",
                    content = file.readText()
                )
            }
            .toList()

        val includePathSetJson = json.encodeToString(includeSources.map { it.path }.toSet())

        val includeBootstrap = """
        (function() {
          var __lyricoDeclaredIncludes = $includePathSetJson;
          var __lyricoDeclaredIncludeMap = Object.create(null);

          __lyricoDeclaredIncludes.forEach(function(path) {
            __lyricoDeclaredIncludeMap[path] = true;
          });

          /*
           * All declared include files have already been concatenated into the same
           * script unit before source.js.
           *
           * Keep include(path) for compatibility with plugins that call it manually.
           * It validates the path, then becomes a no-op.
           */
          globalThis.include = function(path) {
            path = String(path || "");
            if (!Object.prototype.hasOwnProperty.call(__lyricoDeclaredIncludeMap, path)) {
              throw new Error("Include path is not declared in includeDirs: " + path);
            }
          };
        })();
    """.trimIndent()

        return buildString {
            append(includeBootstrap)
            append('\n')

            includeSources.forEach { source ->
                append("\n;")
                append("\n// ===== Platform include: ")
                append(source.path)
                append(" =====\n")
                append(source.content)
                append("\n//# sourceURL=")
                append(source.path)
                append('\n')
            }

            append("\n;")
            append("\n// ===== Platform entry: ")
            append(manifest.entry)
            append(" =====\n")
            append(entryFile.readText())
            append("\n//# sourceURL=")
            append(manifest.entry)
            append('\n')
        }
    }

    private data class IncludedScript(
        val path: String,
        val content: String
    )
}
