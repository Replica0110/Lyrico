package com.lonx.lyrico.plugin.install

import com.lonx.lyrico.data.plugin.PluginManifest
import com.lonx.lyrico.plugin.requirePluginUrl
import com.lonx.lyrico.plugin.runtime.HostApiRegistry
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class PluginArchiveReader(
    private val json: Json,
    private val limits: PluginImportLimits
) {
    fun extractZip(input: InputStream, targetDir: File) {
        val canonicalTarget = targetDir.canonicalFile
        var fileCount = 0
        var totalBytes = 0L

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val entryName = entry.name
                validateZipEntryName(entryName)
                require(entryName.depth() <= limits.maxDepth) {
                    "Zip entry is too deep: $entryName"
                }
                require(++fileCount <= limits.maxFileCountPerArchive) {
                    "Archive contains too many files"
                }

                val output = File(canonicalTarget, entryName).canonicalFile
                require(output.path.isUnderPluginRoot(canonicalTarget.path)) {
                    "Unsafe zip entry: $entryName"
                }

                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            require(totalBytes <= limits.maxTotalUncompressedBytes) {
                                "Archive is too large after extraction"
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    fun findManifestFiles(root: File): List<File> {
        return root.walkTopDown()
            .filter { it.isFile && it.name == MANIFEST_FILE }
            .take(limits.maxPluginCountPerArchive + 1)
            .toList()
            .also {
                require(it.size <= limits.maxPluginCountPerArchive) {
                    "Archive contains too many plugins"
                }
            }
    }

    fun buildCandidate(
        manifestFile: File,
        packageRoot: File
    ): Result<PluginInstallCandidate> = runCatching {
        require(manifestFile.isFile) { "Plugin manifest not found: ${manifestFile.absolutePath}" }
        require(manifestFile.length() <= limits.maxManifestBytes) {
            "${manifestFile.relativeToOrSelf(packageRoot).invariantSeparatorsPath}: manifest is too large"
        }

        val manifest = try {
            json.decodeFromString<PluginManifest>(manifestFile.readText())
        } catch (e: SerializationException) {
            throw IllegalArgumentException(
                "${manifestFile.relativeToOrSelf(packageRoot).invariantSeparatorsPath}: manifest parse failed: ${e.message}"
            )
        }

        val pluginRoot = manifestFile.parentFile
            ?: error("${manifestFile.absolutePath}: manifest has no parent directory")
        validateManifest(manifest)
        com.lonx.lyrico.plugin.i18n.PluginStrings.load(pluginRoot, manifest)
        val entryFile = validateEntry(pluginRoot, manifest.entry)
        val includeDirs = validateIncludeDirs(pluginRoot, manifest.includeDirs)
        val icon = manifest.icon
        if (icon != null) validateIcon(pluginRoot, icon)

        PluginInstallCandidate(
            manifest = manifest,
            pluginRoot = pluginRoot,
            manifestFile = manifestFile,
            relativeRootInArchive = pluginRoot.relativeToOrSelf(packageRoot).invariantSeparatorsPath.ifBlank { "." },
            entryFile = entryFile,
            includeDirs = includeDirs
        )
    }

    private fun validateManifest(manifest: PluginManifest) {
        require(manifest.id.matches(ID_PATTERN)) {
            "Plugin id must use reverse-domain format"
        }
        require(manifest.name.isNotBlank()) { "Plugin name is required" }
        require(manifest.versionCode >= 1) { "Plugin versionCode must be >= 1" }
        manifest.updateUrl?.let { requirePluginUrl(it) }
        require(HostApiRegistry.supportsPluginApiVersion(manifest.apiVersion)) {
            "Unsupported plugin apiVersion: ${manifest.apiVersion} " +
                "(supported: ${HostApiRegistry.MIN_PLUGIN_PROTOCOL_VERSION}..${HostApiRegistry.PLUGIN_PROTOCOL_VERSION})"
        }
        require(HostApiRegistry.supportsHostApiVersion(manifest.minHostApiVersion)) {
            "Unsupported minHostApiVersion: ${manifest.minHostApiVersion} " +
                "(host: ${HostApiRegistry.PLATFORM_API_VERSION})"
        }
    }

    private fun validateEntry(pluginRoot: File, entryPath: String): File {
        require(isSafeRelativePath(entryPath)) { "Unsafe entry path: $entryPath" }
        val entryFile = File(pluginRoot, entryPath).canonicalFile
        require(entryFile.path.isUnderPluginRoot(pluginRoot.canonicalPath)) { "Entry escapes plugin root: $entryPath" }
        require(entryFile.isFile) { "Plugin entry not found: $entryPath" }
        require(entryFile.extension.equals("js", ignoreCase = true)) { "Plugin entry must be a .js file" }
        require(entryFile.length() <= limits.maxEntryScriptBytes) { "Plugin entry is too large" }
        return entryFile
    }

    private fun validateIncludeDirs(pluginRoot: File, includeDirs: List<String>): List<File> {
        return includeDirs.map { dir ->
            require(isSafeRelativePath(dir) && dir != ".") { "Unsafe includeDir: $dir" }
            val includeDir = File(pluginRoot, dir).canonicalFile
            require(includeDir.path.isUnderPluginRoot(pluginRoot.canonicalPath)) { "includeDir escapes plugin root: $dir" }
            require(includeDir.isDirectory) { "includeDir not found: $dir" }
            includeDir
        }
    }

    private fun validateIcon(pluginRoot: File, iconPath: String) {
        require(isSafeRelativePath(iconPath)) { "Unsafe icon path: $iconPath" }
        val iconFile = File(pluginRoot, iconPath).canonicalFile
        require(iconFile.path.isUnderPluginRoot(pluginRoot.canonicalPath)) { "Icon escapes plugin root: $iconPath" }
        require(iconFile.isFile) { "Icon not found: $iconPath" }
        require(iconFile.extension.lowercase() in SUPPORTED_ICON_EXTENSIONS) {
            "Unsupported icon type: $iconPath"
        }
    }

    private fun validateZipEntryName(entryName: String) {
        require(entryName.isNotBlank()) { "Zip entry name is blank" }
        require(!entryName.contains('\u0000')) { "Zip entry contains NUL byte" }
        require(!entryName.startsWith("/") && !entryName.startsWith("\\")) {
            "Absolute zip entry is not allowed: $entryName"
        }
        require(!entryName.contains('\\')) { "Backslash zip entry is not allowed: $entryName" }
        require(entryName.split('/').none { it == ".." }) { "Unsafe zip entry: $entryName" }
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains('\\') || path.contains('\u0000')) return false
        return path.split('/').none { it == ".." || it.isBlank() }
    }

    private fun String.depth(): Int {
        return trim('/').split('/').count { it.isNotBlank() }
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
        val ID_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
        val SUPPORTED_ICON_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}

internal fun String.isUnderPluginRoot(basePath: String): Boolean {
    return this == basePath || startsWith(basePath + File.separator)
}
