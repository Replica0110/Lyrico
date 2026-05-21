package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.plugin.PluginManifest
import com.lonx.lyrico.data.repository.SourcePluginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class SourcePluginInstaller(
    private val repository: SourcePluginRepository,
    private val json: Json
) {
    suspend fun installFromArchive(
        input: InputStream,
        installRoot: File,
        enabled: Boolean = false
    ): SourcePluginEntity = withContext(Dispatchers.IO) {
        installRoot.mkdirs()
        val tempDir = File(installRoot, ".import-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            extractZip(input, tempDir)
            val manifestFile = File(tempDir, MANIFEST_FILE)
            require(manifestFile.isFile) { "Plugin manifest not found" }

            val manifest = json.decodeFromString<PluginManifest>(manifestFile.readText())
            validateManifest(manifest)

            val targetDir = File(installRoot, manifest.id)
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            require(tempDir.copyRecursively(targetDir, overwrite = true)) {
                "Failed to copy plugin into ${targetDir.absolutePath}"
            }

            installFromDirectory(targetDir, enabled)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun installFromDirectory(pluginDir: File, enabled: Boolean = false): SourcePluginEntity =
        withContext(Dispatchers.IO) {
            val manifestFile = File(pluginDir, MANIFEST_FILE)
            require(manifestFile.isFile) { "Plugin manifest not found: ${manifestFile.absolutePath}" }

            val manifest = json.decodeFromString<PluginManifest>(manifestFile.readText())
            validateManifest(manifest)

            val entryFile = File(pluginDir, manifest.entry)
            require(entryFile.isFile) { "Plugin entry not found: ${entryFile.absolutePath}" }

            val now = System.currentTimeMillis()
            val existing = repository.getPlugin(manifest.id)
            val entity = SourcePluginEntity(
                id = manifest.id,
                name = manifest.name,
                versionCode = manifest.versionCode,
                versionName = manifest.versionName,
                author = manifest.author,
                description = manifest.description,
                apiVersion = manifest.apiVersion,
                pluginDir = pluginDir.absolutePath,
                entryFile = manifest.entry,
                iconPath = null,
                enabled = existing?.enabled ?: enabled,
                sortOrder = existing?.sortOrder ?: nextSortOrder(),
                installedAt = existing?.installedAt ?: now,
                updatedAt = now
            )
            repository.upsertPlugin(entity)
            entity
        }

    private fun extractZip(input: InputStream, targetDir: File) {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(targetDir, entry.name)
                val canonicalTarget = targetDir.canonicalFile
                val canonicalOutput = output.canonicalFile
                require(canonicalOutput.path.startsWith(canonicalTarget.path + File.separator)) {
                    "Unsafe zip entry: ${entry.name}"
                }

                if (entry.isDirectory) {
                    canonicalOutput.mkdirs()
                } else {
                    canonicalOutput.parentFile?.mkdirs()
                    canonicalOutput.outputStream().use { out ->
                        zip.copyTo(out)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private suspend fun nextSortOrder(): Int {
        return repository.getPlugins().maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
    }

    private fun validateManifest(manifest: PluginManifest) {
        require(manifest.id.matches(ID_PATTERN)) {
            "Plugin id must contain only lowercase letters, numbers, dots, underscores, or hyphens"
        }
        require(manifest.name.isNotBlank()) { "Plugin name is required" }
        require(manifest.versionCode >= 1) { "Plugin versionCode must be >= 1" }
        require(manifest.apiVersion == SUPPORTED_API_VERSION) {
            "Unsupported plugin apiVersion: ${manifest.apiVersion}"
        }
        require(manifest.capabilities.isEmpty() || PluginCapability.SEARCH_SONGS in manifest.capabilities) {
            "A source plugin must support SEARCH_SONGS"
        }
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val SUPPORTED_API_VERSION = 1
        val ID_PATTERN = Regex("[a-z0-9._-]+")
    }
}
