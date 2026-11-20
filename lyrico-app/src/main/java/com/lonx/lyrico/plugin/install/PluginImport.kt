package com.lonx.lyrico.plugin.install

import com.lonx.lyrico.data.db.entity.SourcePluginEntity
import com.lonx.lyrico.data.plugin.PluginManifest
import com.lonx.lyrico.plugin.i18n.PluginNames
import java.io.File

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
    DOWNGRADE;

    companion object {
        fun between(installed: Int?, candidate: Int): PluginVersionConflict = when {
            installed == null -> NONE
            candidate > installed -> UPDATE
            candidate == installed -> OVERWRITE
            else -> DOWNGRADE
        }
    }
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
        pluginName = PluginNames.resolve(manifest, pluginRoot),
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
