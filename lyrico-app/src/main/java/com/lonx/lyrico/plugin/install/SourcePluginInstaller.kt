package com.lonx.lyrico.plugin.install

import android.util.Log
import com.lonx.lyrico.data.db.entity.SourcePluginEntity
import com.lonx.lyrico.data.db.entity.capabilities
import com.lonx.lyrico.data.db.entity.displayName
import com.lonx.lyrico.data.log.AppLogLevel
import com.lonx.lyrico.data.log.AppLogRepository
import com.lonx.lyrico.data.log.AppLogType
import com.lonx.lyrico.data.log.LogOperation
import com.lonx.lyrico.data.log.recordOperation
import com.lonx.lyrico.data.log.withLogOperation
import com.lonx.lyrico.data.plugin.PluginManifest
import com.lonx.lyrico.data.plugin.SourcePluginRepository
import com.lonx.lyrico.data.plugin.normalizedPluginCapabilities
import com.lonx.lyrico.plugin.i18n.PluginNames
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class SourcePluginInstaller(
    private val repository: SourcePluginRepository,
    private val json: Json,
    private val appLogRepository: AppLogRepository,
    private val limits: PluginImportLimits = PluginImportLimits()
) {
    private val archiveReader = PluginArchiveReader(json, limits)

    // All file/database mutations share this lock, including uninstall and recovery.
    val mutationMutex = Mutex()
    private var recovered = false
    private val manifestMetadataSynchronizationMutex = Mutex()
    private var installedManifestMetadataSynchronized = false

    suspend fun prepareImport(
        input: InputStream,
        installRoot: File
    ): PluginImportSession = withContext(Dispatchers.IO) {
        installRoot.mkdirs()
        recover(installRoot)
        val tempDir = File(installRoot, ".import-${UUID.randomUUID()}")
        tempDir.mkdirs()

        try {
            archiveReader.extractZip(input, tempDir)
            val manifests = archiveReader.findManifestFiles(tempDir)
            if (manifests.isEmpty()) {
                logInstaller(
                    level = AppLogLevel.ERROR,
                    message = "Plugin import failed: manifest not found",
                    detail = "installRoot=${installRoot.absolutePath}"
                )
                return@withContext PluginImportSession(
                    tempDir = tempDir,
                    installRoot = installRoot,
                    candidates = emptyList(),
                    failed = listOf(PluginInstallFailed(".", "Plugin manifest not found"))
                )
            }

            val validated = manifests.map { manifestFile ->
                archiveReader.buildCandidate(manifestFile = manifestFile, packageRoot = tempDir)
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
                            val failure = candidate.toFailed(
                                reason = "Duplicate plugin id in archive: ${candidate.manifest.id}"
                            )
                            failed += failure
                            logInstallFailure("Plugin import candidate rejected", failure)
                        } else {
                            val existing = repository.getPlugin(candidate.manifest.id)
                            candidates += candidate.copy(
                                existingPlugin = existing,
                                versionConflict = candidate.versionConflict(existing)
                            )
                        }
                    },
                    onFailure = { throwable ->
                        val failure = throwable.toFailedPlugin()
                        failed += failure
                        logInstallerException(
                            message = "Plugin import candidate validation failed",
                            throwable = throwable,
                            relatedId = failure.pluginId
                        )
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
            logInstallerException(
                message = "Plugin import preparation failed",
                throwable = throwable
            )
            tempDir.deleteRecursively()
            throw throwable
        }
    }

    suspend fun installPrepared(
        session: PluginImportSession,
        enabled: Boolean = false,
        selectedRoots: Set<String>? = null,
        allowDowngrade: Boolean = false,
        expectedInstalledDir: String? = null,
        discardSession: Boolean = true,
        checkInstalledState: Boolean = false,
        installSourceUrl: String? = null,
        installSha256: String? = null,
        onInstalled: (SourcePluginEntity) -> Unit = {}
    ): PluginInstallResult = withContext(Dispatchers.IO) {
        val failed = session.failed.toMutableList()
        try {
            val selectedCandidates = session.candidates.filter { candidate ->
                selectedRoots == null || candidate.relativeRootInArchive in selectedRoots
            }
            val installed = selectedCandidates.mapNotNull { candidate ->
                mutationMutex.withLock {
                    val current = repository.getPlugin(candidate.manifest.id)
                    require((!checkInstalledState && expectedInstalledDir == null) || current?.pluginDir == expectedInstalledDir) {
                        "Plugin changed while downloading; check for updates again"
                    }
                    if (current != null && candidate.manifest.versionCode < current.versionCode && !allowDowngrade) {
                        val failure = candidate.toFailed(
                            reason = "Import version is lower than installed version: ${candidate.manifest.id}"
                        )
                        failed += failure
                        logInstallFailure("Plugin install rejected", failure)
                        return@withLock null
                    }
                    runCatching {
                        installCandidate(
                            candidate = candidate,
                            allCandidates = session.candidates,
                            installRoot = session.installRoot,
                            enabled = enabled,
                            installSourceUrl = installSourceUrl,
                            installSha256 = installSha256,
                            onInstalled = onInstalled
                        )
                    }.onFailure { throwable ->
                        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                        val failure = candidate.toFailed(
                            reason = throwable.message ?: throwable.javaClass.simpleName
                        )
                        failed += failure
                        logInstallerException(
                            message = "Plugin install failed\nplugin=${candidate.manifest.id}\n" +
                                    "name=${PluginNames.resolve(candidate.manifest, candidate.pluginRoot)}\nroot=${candidate.relativeRootInArchive}",
                            throwable = throwable,
                            relatedId = candidate.manifest.id
                        )
                    }.getOrNull()
                }
            }
            if (installed.isNotEmpty() || failed.isNotEmpty()) {
                logInstaller(
                    level = if (failed.isEmpty()) AppLogLevel.INFO else AppLogLevel.WARNING,
                    message = "Plugin install finished: installed=${installed.size}, failed=${failed.size}",
                    detail = buildString {
                        appendLine("installed=${installed.map { "${it.id}:${it.versionName}(${it.versionCode})" }}")
                        appendLine("failed=${failed.map { "${it.displayName}:${it.reason}" }}")
                    }
                )
            }
            PluginInstallResult(installed = installed, failed = failed)
        } finally {
            if (discardSession) discardImport(session)
        }
    }

    fun discardImport(session: PluginImportSession) {
        session.tempDir.deleteRecursively()
    }

    /** Called once before runtime instances exist. Keep old versions during this process's lifetime. */
    suspend fun recover(installRoot: File) = mutationMutex.withLock {
        if (recovered) return@withLock
        val referenced = repository.getPlugins().map { File(it.pluginDir).canonicalPath }.toSet()
        fun clean(file: File) {
            val path = file.canonicalPath
            if (path in referenced) return
            if (referenced.any { it.startsWith(path + File.separator) }) {
                file.listFiles()?.forEach(::clean)
            } else {
                runCatching { file.deleteRecursively() }
            }
        }
        installRoot.listFiles()?.forEach(::clean)
        recovered = true
    }

    private suspend fun installCandidate(
        candidate: PluginInstallCandidate,
        allCandidates: List<PluginInstallCandidate>,
        installRoot: File,
        enabled: Boolean,
        installSourceUrl: String?,
        installSha256: String?,
        onInstalled: (SourcePluginEntity) -> Unit
    ): SourcePluginEntity = withLogOperation("plugin-install") {
        val started = System.nanoTime()
        var stage = "copy-to-staging"
        val targetDir = File(installRoot, "${candidate.manifest.id}/${UUID.randomUUID()}")
        val stagingDir = File(installRoot, ".staging-${candidate.manifest.id}-${System.currentTimeMillis()}")
        if (stagingDir.exists()) stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        try {
            appLogRepository.recordOperation(AppLogType.PLUGIN, TAG, "Plugin installation started",
                "plugin=${candidate.manifest.id}\nversion=${candidate.manifest.versionName}(${candidate.manifest.versionCode})\n" +
                    "previousVersion=${candidate.existingPlugin?.versionName}\nconflict=${candidate.versionConflict}\n" +
                    "target=${targetDir.absolutePath}\nexistingDirectory=${targetDir.exists()}", candidate.manifest.id)
            copyPluginRoot(candidate, allCandidates, stagingDir)
            stage = "validate-staging"
            validatePluginSize(stagingDir)
            require(targetDir.parentFile!!.isDirectory || targetDir.parentFile!!.mkdirs()) {
                "Cannot create plugin version directory"
            }
            stage = "move-staging"
            if (!stagingDir.renameTo(targetDir)) {
                require(stagingDir.copyRecursively(targetDir, overwrite = true)) {
                    "Failed to copy plugin into ${targetDir.absolutePath}"
                }
                stagingDir.deleteRecursively()
            }
            stage = "update-database"
            currentCoroutineContext().ensureActive()
            // Finish the database commit even if cancellation arrives at this point.
            withContext(NonCancellable) {
                upsertInstalledPlugin(candidate.manifest, targetDir, enabled, installSourceUrl, installSha256).also(onInstalled)
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw IOException("Plugin installation failed: operationId=${currentCoroutineContext()[LogOperation]?.id} stage=$stage plugin=${candidate.manifest.id} " +
                "elapsedMs=${(System.nanoTime() - started) / 1_000_000}; ${error.message}", error)
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
            .filter { it.path.isUnderPluginRoot(sourceRoot.path) }

        sourceRoot.walkTopDown()
            .onEnter { dir ->
                val canonicalDir = dir.canonicalFile
                excludedRoots.none { excluded ->
                    canonicalDir.path == excluded.path || canonicalDir.path.isUnderPluginRoot(excluded.path)
                }
            }
            .forEach { file ->
                val canonicalFile = file.canonicalFile
                if (excludedRoots.any { excluded ->
                        canonicalFile.path == excluded.path || canonicalFile.path.isUnderPluginRoot(excluded.path)
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
        enabled: Boolean,
        installSourceUrl: String?,
        installSha256: String?
    ): SourcePluginEntity {
        val now = System.currentTimeMillis()
        val existing = repository.getPlugin(manifest.id)
        val iconPath = manifest.icon?.let { File(pluginDir, it).absolutePath }
        val initialSortOrder = existing?.sortOrder ?: nextSortOrder()
        val entity = SourcePluginEntity(
            id = manifest.id,
            name = manifest.name,
            versionCode = manifest.versionCode,
            versionName = manifest.versionName,
            author = manifest.author,
            description = manifest.description,
            apiVersion = manifest.apiVersion,
            minHostApiVersion = manifest.minHostApiVersion,
            pluginDir = pluginDir.absolutePath,
            entryFile = manifest.entry,
            includeDirsJson = json.encodeToString(manifest.includeDirs),
            capabilitiesJson = json.encodeToString(
                manifest.capabilities.normalizedPluginCapabilities()
            ),
            customName = existing?.customName,
            iconPath = iconPath,
            enabled = existing?.enabled ?: enabled,
            metadataEnabled = existing?.metadataEnabled ?: enabled,
            lyricsEnabled = existing?.lyricsEnabled ?: enabled,
            coverEnabled = existing?.coverEnabled ?: enabled,
            sortOrder = initialSortOrder,
            metadataSortOrder = existing?.metadataSortOrder ?: initialSortOrder,
            lyricsSortOrder = existing?.lyricsSortOrder ?: initialSortOrder,
            coverSortOrder = existing?.coverSortOrder ?: initialSortOrder,
            installedAt = existing?.installedAt ?: now,
            updatedAt = now,
            updateUrl = manifest.updateUrl,
            installSourceUrl = installSourceUrl,
            installSha256 = installSha256
        )
        repository.upsertPlugin(entity)
        logInstaller(
            level = AppLogLevel.INFO,
            message = "Plugin installed: ${PluginNames.resolve(entity)}",
            detail = buildString {
                appendLine("plugin=${entity.id}")
                appendLine("version=${entity.versionName}(${entity.versionCode})")
                appendLine("enabled=${entity.enabled}")
                appendLine("capabilities=${entity.capabilitiesJson}")
                appendLine("entry=${entity.entryFile}")
                appendLine("dir=${entity.pluginDir}")
                appendLine("icon=${entity.iconPath.orEmpty()}")
            },
            relatedId = entity.id
        )
        return entity
    }

    suspend fun synchronizeInstalledPluginManifestMetadata() {
        manifestMetadataSynchronizationMutex.withLock {
            mutationMutex.withLock {
                if (installedManifestMetadataSynchronized) return

                withContext(Dispatchers.IO) {
                    repository.getPlugins().forEach { plugin ->
                        runCatching {
                            val manifestFile = File(plugin.pluginDir, MANIFEST_FILE)
                            val manifest = json.decodeFromString<PluginManifest>(manifestFile.readText())
                            require(manifest.id == plugin.id) {
                                "Installed manifest id does not match database record: ${plugin.id}"
                            }
                            val capabilitiesJson = json.encodeToString(
                                manifest.capabilities.normalizedPluginCapabilities()
                            )
                            if (
                                manifest.apiVersion != plugin.apiVersion ||
                                manifest.minHostApiVersion != plugin.minHostApiVersion ||
                                capabilitiesJson != plugin.capabilitiesJson ||
                                manifest.updateUrl != plugin.updateUrl
                            ) {
                                repository.updateManifestContract(
                                    id = plugin.id,
                                    apiVersion = manifest.apiVersion,
                                    minHostApiVersion = manifest.minHostApiVersion,
                                    capabilitiesJson = capabilitiesJson,
                                    updateUrl = manifest.updateUrl
                                )
                            }
                        }.onFailure { throwable ->
                            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                            Log.w(TAG, "Failed to synchronize manifest metadata for ${plugin.id}", throwable)
                        }
                    }
                }

                installedManifestMetadataSynchronized = true
            }
        }
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

    private fun PluginInstallCandidate.versionConflict(
        existing: SourcePluginEntity?
    ): PluginVersionConflict {
        return PluginVersionConflict.between(existing?.versionCode, manifest.versionCode)
    }

    private suspend fun logInstallFailure(message: String, failure: PluginInstallFailed) {
        logInstaller(
            level = AppLogLevel.ERROR,
            message = message,
            detail = buildString {
                appendLine("root=${failure.rootPath}")
                appendLine("reason=${failure.reason}")
                appendLine("plugin=${failure.pluginId.orEmpty()}")
                appendLine("name=${failure.pluginName.orEmpty()}")
                appendLine("version=${failure.versionName.orEmpty()}(${failure.versionCode ?: ""})")
                appendLine("existing=${failure.existingVersionName.orEmpty()}(${failure.existingVersionCode ?: ""})")
                appendLine("conflict=${failure.conflict}")
            },
            relatedId = failure.pluginId
        )
    }

    private suspend fun logInstaller(
        level: AppLogLevel,
        message: String,
        detail: String? = null,
        relatedId: String? = null
    ) {
        runCatching {
            appLogRepository.log(
                level = level,
                type = AppLogType.PLUGIN,
                tag = TAG,
                message = message,
                detail = detail,
                relatedId = relatedId
            )
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to write plugin installer log", throwable)
        }
    }

    private suspend fun logInstallerException(
        message: String,
        throwable: Throwable,
        relatedId: String? = null
    ) {
        runCatching {
            appLogRepository.logException(
                type = AppLogType.PLUGIN,
                tag = TAG,
                message = message,
                throwable = throwable,
                relatedId = relatedId
            )
        }.onFailure { logThrowable ->
            Log.w(TAG, "Failed to write plugin installer exception log", logThrowable)
        }
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val TAG = "SourcePluginInstaller"
    }
}
