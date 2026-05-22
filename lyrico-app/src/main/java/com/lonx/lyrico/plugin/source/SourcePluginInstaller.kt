package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.plugin.PluginManifest
import com.lonx.lyrico.data.repository.SourcePluginRepository
import com.lonx.lyrico.plugin.runtime.HostApiRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class PluginImportLimits(
    val maxTotalUncompressedBytes: Long = 30L * 1024 * 1024,
    val maxSinglePluginBytes: Long = 5L * 1024 * 1024,
    val maxManifestBytes: Long = 128L * 1024,
    val maxEntryScriptBytes: Long = 1L * 1024 * 1024,
    val maxPluginCountPerArchive: Int = 20,
    val maxFileCountPerArchive: Int = 1000,
    val maxDepth: Int = 16
)

data class PluginInstallCandidate(
    val manifest: PluginManifest,
    val pluginRoot: File,
    val manifestFile: File,
    val relativeRootInArchive: String,
    val entryFile: File,
    val includeDirs: List<File>,
    val existingPlugin: SourcePluginEntity? = null,
    val versionConflict: PluginVersionConflict = PluginVersionConflict.NONE,
    val warnings: List<String> = emptyList()
)

enum class PluginVersionConflict {
    NONE,
    UPDATE,
    OVERWRITE,
    DOWNGRADE
}

data class PluginInstallResult(
    val installed: List<SourcePluginEntity>,
    val failed: List<PluginInstallFailed>
)

data class PluginImportSession(
    val tempDir: File,
    val installRoot: File,
    val candidates: List<PluginInstallCandidate>,
    val failed: List<PluginInstallFailed>
)

data class PluginInstallFailed(
    val rootPath: String,
    val reason: String,
    val pluginId: String? = null,
    val pluginName: String? = null,
    val versionCode: Int? = null,
    val versionName: String? = null,
    val existingVersionCode: Int? = null,
    val existingVersionName: String? = null,
    val conflict: PluginVersionConflict? = null
) {
    val displayName: String
        get() = pluginName
            ?.takeIf { it.isNotBlank() }
            ?: pluginId
                ?.takeIf { it.isNotBlank() }
            ?: rootPath.takeIf { it.isNotBlank() }
            ?: "."

    val hasPluginInfo: Boolean
        get() = !pluginId.isNullOrBlank() ||
                !pluginName.isNullOrBlank() ||
                versionCode != null ||
                !versionName.isNullOrBlank()
}
fun PluginInstallCandidate.toFailed(reason: String): PluginInstallFailed {
    return PluginInstallFailed(
        rootPath = relativeRootInArchive,
        reason = reason,
        pluginId = manifest.id,
        pluginName = manifest.name,
        versionCode = manifest.versionCode,
        versionName = manifest.versionName,
        existingVersionCode = existingPlugin?.versionCode,
        existingVersionName = existingPlugin?.versionName,
        conflict = versionConflict
    )
}
fun PluginManifest.toFailed(
    rootPath: String,
    reason: String,
    existingPlugin: SourcePluginEntity? = null,
    conflict: PluginVersionConflict? = null
): PluginInstallFailed {
    return PluginInstallFailed(
        rootPath = rootPath,
        reason = reason,
        pluginId = id,
        pluginName = name,
        versionCode = versionCode,
        versionName = versionName,
        existingVersionCode = existingPlugin?.versionCode,
        existingVersionName = existingPlugin?.versionName,
        conflict = conflict
    )
}
fun Throwable.toFailedPlugin(rootPathFallback: String = "."): PluginInstallFailed {
    val message = message ?: javaClass.simpleName
    return PluginInstallFailed(
        rootPath = message.substringBefore(": ").ifBlank { rootPathFallback },
        reason = message
    )
}
class SourcePluginInstaller(
    private val repository: SourcePluginRepository,
    private val json: Json,
    private val limits: PluginImportLimits = PluginImportLimits()
) {

    suspend fun prepareImport(
        input: InputStream,
        installRoot: File
    ): PluginImportSession = withContext(Dispatchers.IO) {
        installRoot.mkdirs()
        val tempDir = File(installRoot, ".import-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            extractZip(input, tempDir)
            val manifests = findManifestFiles(tempDir)
            if (manifests.isEmpty()) {
                return@withContext PluginImportSession(
                    tempDir = tempDir,
                    installRoot = installRoot,
                    candidates = emptyList(),
                    failed = listOf(PluginInstallFailed(".", "Plugin manifest not found"))
                )
            }

            val validated = manifests.map { manifestFile ->
                buildCandidate(manifestFile = manifestFile, packageRoot = tempDir)
            }
            val duplicateIds = validated
                .mapNotNull { it.getOrNull()?.manifest?.id }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys

            val candidates = mutableListOf<PluginInstallCandidate>()
            val failed = mutableListOf<PluginInstallFailed>()
            validated.forEach { result ->
                result.fold(
                    onSuccess = { candidate ->
                        if (candidate.manifest.id in duplicateIds) {
                            failed += candidate.toFailed(
                                reason = "Duplicate plugin id in archive: ${candidate.manifest.id}"
                            )
                        } else {
                            val existing = repository.getPlugin(candidate.manifest.id)
                            candidates += candidate.copy(
                                existingPlugin = existing,
                                versionConflict = candidate.versionConflict(existing)
                            )
                        }
                    },
                    onFailure = { throwable ->
                        failed += throwable.toFailedPlugin()
                    }
                )
            }

            PluginImportSession(
                tempDir = tempDir,
                installRoot = installRoot,
                candidates = candidates,
                failed = failed
            )
        } catch (throwable: Throwable) {
            tempDir.deleteRecursively()
            throw throwable
        }
    }

    suspend fun installPrepared(
        session: PluginImportSession,
        enabled: Boolean = false,
        selectedRoots: Set<String>? = null,
        allowDowngrade: Boolean = false
    ): PluginInstallResult = withContext(Dispatchers.IO) {
        val failed = session.failed.toMutableList()
        try {
            val selectedCandidates = session.candidates.filter { candidate ->
                selectedRoots == null || candidate.relativeRootInArchive in selectedRoots
            }
            val installed = selectedCandidates.mapNotNull { candidate ->
                if (candidate.versionConflict == PluginVersionConflict.DOWNGRADE && !allowDowngrade) {
                    failed += candidate.toFailed(
                        reason = "Import version is lower than installed version: ${candidate.manifest.id}"
                    )
                    return@mapNotNull null
                }
                runCatching {
                    installCandidate(
                        candidate = candidate,
                        allCandidates = session.candidates,
                        installRoot = session.installRoot,
                        enabled = enabled
                    )
                }.onFailure { throwable ->
                    failed += candidate.toFailed(
                        reason = throwable.message ?: throwable.javaClass.simpleName
                    )
                }.getOrNull()
            }
            PluginInstallResult(installed = installed, failed = failed)
        } finally {
            discardImport(session)
        }
    }

    fun discardImport(session: PluginImportSession) {
        session.tempDir.deleteRecursively()
    }

    private fun extractZip(input: InputStream, targetDir: File) {
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
                require(output.path.isUnder(canonicalTarget.path)) {
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

    private fun findManifestFiles(root: File): List<File> {
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

    private fun buildCandidate(
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

    private suspend fun installCandidate(
        candidate: PluginInstallCandidate,
        allCandidates: List<PluginInstallCandidate>,
        installRoot: File,
        enabled: Boolean
    ): SourcePluginEntity {
        val targetDir = File(installRoot, candidate.manifest.id)
        val stagingDir = File(installRoot, ".staging-${candidate.manifest.id}-${System.currentTimeMillis()}")
        if (stagingDir.exists()) stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        try {
            copyPluginRoot(candidate, allCandidates, stagingDir)
            validatePluginSize(stagingDir)
            if (targetDir.exists()) targetDir.deleteRecursively()
            if (!stagingDir.renameTo(targetDir)) {
                require(stagingDir.copyRecursively(targetDir, overwrite = true)) {
                    "Failed to copy plugin into ${targetDir.absolutePath}"
                }
                stagingDir.deleteRecursively()
            }
            return upsertInstalledPlugin(candidate.manifest, targetDir, enabled)
        } finally {
            if (stagingDir.exists()) stagingDir.deleteRecursively()
        }
    }

    private fun copyPluginRoot(
        candidate: PluginInstallCandidate,
        allCandidates: List<PluginInstallCandidate>,
        targetDir: File
    ) {
        val sourceRoot = candidate.pluginRoot.canonicalFile
        val excludedRoots = allCandidates
            .filter { it.pluginRoot.canonicalFile != sourceRoot }
            .map { it.pluginRoot.canonicalFile }
            .filter { it.path.isUnder(sourceRoot.path) }

        sourceRoot.walkTopDown()
            .onEnter { dir ->
                val canonicalDir = dir.canonicalFile
                excludedRoots.none { excluded ->
                    canonicalDir.path == excluded.path || canonicalDir.path.isUnder(excluded.path)
                }
            }
            .forEach { file ->
                val canonicalFile = file.canonicalFile
                if (excludedRoots.any { excluded ->
                        canonicalFile.path == excluded.path || canonicalFile.path.isUnder(excluded.path)
                    }
                ) {
                    return@forEach
                }
                val relativePath = canonicalFile.relativeTo(sourceRoot).path
                if (relativePath.isBlank()) return@forEach
                val target = File(targetDir, relativePath)
                if (file.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    file.copyTo(target, overwrite = true)
                }
            }
    }

    private suspend fun upsertInstalledPlugin(
        manifest: PluginManifest,
        pluginDir: File,
        enabled: Boolean
    ): SourcePluginEntity {
        val now = System.currentTimeMillis()
        val existing = repository.getPlugin(manifest.id)
        val iconPath = manifest.icon?.let { File(pluginDir, it).absolutePath }
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
            includeDirsJson = json.encodeToString(manifest.includeDirs),
            iconPath = iconPath,
            enabled = existing?.enabled ?: enabled,
            sortOrder = existing?.sortOrder ?: nextSortOrder(),
            installedAt = existing?.installedAt ?: now,
            updatedAt = now
        )
        repository.upsertPlugin(entity)
        return entity
    }

    private fun validatePluginSize(pluginDir: File) {
        val totalSize = pluginDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        require(totalSize <= limits.maxSinglePluginBytes) {
            "Plugin is too large"
        }
    }

    private suspend fun nextSortOrder(): Int {
        return repository.getPlugins().maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
    }

    private fun validateManifest(manifest: PluginManifest) {
        require(manifest.id.matches(ID_PATTERN)) {
            "Plugin id must use reverse-domain format"
        }
        require(manifest.name.isNotBlank()) { "Plugin name is required" }
        require(manifest.versionCode >= 1) { "Plugin versionCode must be >= 1" }
        require(manifest.apiVersion == HostApiRegistry.PLUGIN_API_VERSION) {
            "Unsupported plugin apiVersion: ${manifest.apiVersion}"
        }
        require(manifest.requiredHostApis.all { it in HostApiRegistry.SUPPORTED_HOST_APIS }) {
            "Unsupported host APIs: ${manifest.requiredHostApis - HostApiRegistry.SUPPORTED_HOST_APIS}"
        }
        require(manifest.capabilities.isEmpty() || PluginCapability.SEARCH_SONGS in manifest.capabilities) {
            "A source plugin must support SEARCH_SONGS"
        }
    }

    private fun validateEntry(pluginRoot: File, entryPath: String): File {
        require(isSafeRelativePath(entryPath)) { "Unsafe entry path: $entryPath" }
        val entryFile = File(pluginRoot, entryPath).canonicalFile
        require(entryFile.path.isUnder(pluginRoot.canonicalPath)) { "Entry escapes plugin root: $entryPath" }
        require(entryFile.isFile) { "Plugin entry not found: $entryPath" }
        require(entryFile.extension.equals("js", ignoreCase = true)) { "Plugin entry must be a .js file" }
        require(entryFile.length() <= limits.maxEntryScriptBytes) { "Plugin entry is too large" }
        return entryFile
    }

    private fun validateIncludeDirs(pluginRoot: File, includeDirs: List<String>): List<File> {
        return includeDirs.map { dir ->
            require(isSafeRelativePath(dir) && dir != ".") { "Unsafe includeDir: $dir" }
            val includeDir = File(pluginRoot, dir).canonicalFile
            require(includeDir.path.isUnder(pluginRoot.canonicalPath)) { "includeDir escapes plugin root: $dir" }
            require(includeDir.isDirectory) { "includeDir not found: $dir" }
            includeDir
        }
    }

    private fun validateIcon(pluginRoot: File, iconPath: String) {
        require(isSafeRelativePath(iconPath)) { "Unsafe icon path: $iconPath" }
        val iconFile = File(pluginRoot, iconPath).canonicalFile
        require(iconFile.path.isUnder(pluginRoot.canonicalPath)) { "Icon escapes plugin root: $iconPath" }
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

    private fun String.isUnder(basePath: String): Boolean {
        return this == basePath || startsWith(basePath + File.separator)
    }

    private fun String.depth(): Int {
        return trim('/').split('/').count { it.isNotBlank() }
    }

    private fun PluginInstallCandidate.versionConflict(
        existing: SourcePluginEntity?
    ): PluginVersionConflict {
        if (existing == null) return PluginVersionConflict.NONE
        return when {
            manifest.versionCode > existing.versionCode -> PluginVersionConflict.UPDATE
            manifest.versionCode == existing.versionCode -> PluginVersionConflict.OVERWRITE
            else -> PluginVersionConflict.DOWNGRADE
        }
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
        val ID_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
        val SUPPORTED_ICON_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}
