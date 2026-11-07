package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.plugin.PluginManifest
import com.lonx.lyrico.plugin.runtime.HostApiRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
data class PluginCatalog(
    val schemaVersion: Int,
    val plugins: List<PluginRelease>
)

@Serializable
data class PluginRelease(
    val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val apiVersion: Int,
    val minHostApiVersion: Int = 1,
    val downloadUrl: String,
    val sha256: String,
    val size: Long,
    val changelog: String = ""
) {
    val compatible: Boolean
        get() = HostApiRegistry.supportsPluginApiVersion(apiVersion) &&
            HostApiRegistry.supportsHostApiVersion(minHostApiVersion)

    fun verify(manifest: PluginManifest) {
        require(manifest.id == id && manifest.versionCode == versionCode &&
            manifest.versionName == versionName && manifest.apiVersion == apiVersion &&
            manifest.minHostApiVersion == minHostApiVersion) {
            "Downloaded manifest does not match catalog: $id"
        }
    }
}

object PluginCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val pluginIdPattern = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")

    fun parse(text: String, url: String): PluginCatalog {
        val base = requireUrl(url).toHttpUrl()
        val catalog = json.decodeFromString<PluginCatalog>(text)
        require(catalog.schemaVersion == 1) { "Unsupported plugin catalog format" }
        require(catalog.plugins.size <= 500) { "Too many catalog entries" }
        require(catalog.plugins.map { it.id }.distinct().size == catalog.plugins.size) {
            "Duplicate plugin IDs in catalog"
        }
        return catalog.copy(plugins = catalog.plugins.map { release ->
            require(pluginIdPattern.matches(release.id) && release.name.isNotBlank()) { "Invalid plugin identity" }
            require(release.versionCode >= 1 && release.versionName.isNotBlank()) { "Invalid plugin version" }
            require(release.apiVersion >= 1 && release.minHostApiVersion >= 1) { "Invalid plugin API version" }
            require(release.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid SHA-256" }
            require(release.size in 1..MAX_PACKAGE_BYTES) { "Invalid plugin package size" }
            val resolved = base.resolve(release.downloadUrl)?.toString()
                ?: error("Invalid download URL")
            release.copy(downloadUrl = requireUrl(resolved))
        })
    }

    fun requireUrl(value: String): String {
        val url = value.toHttpUrl()
        require(url.username.isEmpty() && url.password.isEmpty() && url.fragment == null) {
            "Expected an HTTP or HTTPS URL without credentials or fragment"
        }
        return url.toString()
    }

    const val MAX_PACKAGE_BYTES = 30L * 1024 * 1024
}
