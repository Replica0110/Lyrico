package com.lonx.lyrico.plugin.install

import com.lonx.lyrico.data.plugin.PluginManifest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginArchiveReaderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `nested plugin manifests produce separate validated candidates`() {
        val root = temporary.newFolder()
        val reader = reader()
        reader.extractZip(archive(mapOf(
            "manifest.json" to manifest("test.parent"),
            "source.js" to "export default {}",
            "nested/manifest.json" to manifest("test.child"),
            "nested/source.js" to "export default {}"
        )), root)

        val candidates = reader.findManifestFiles(root).map { reader.buildCandidate(it, root).getOrThrow() }

        assertEquals(setOf("test.parent", "test.child"), candidates.map { it.manifest.id }.toSet())
        assertEquals(setOf(".", "nested"), candidates.map { it.relativeRootInArchive }.toSet())
        assertTrue(candidates.all { it.entryFile.isFile })
    }

    @Test
    fun `zip traversal is rejected before a file escapes the extraction root`() {
        val root = File(temporary.newFolder(), "extracted").apply { mkdirs() }

        expectFailure("Unsafe zip entry") { reader().extractZip(archive(mapOf("../outside.js" to "bad")), root) }
        assertTrue(!File(root.parentFile, "outside.js").exists())
    }

    @Test
    fun `extraction enforces byte count file count and depth limits`() {
        expectFailure("too large") {
            reader(PluginImportLimits(maxTotalUncompressedBytes = 2)).extractZip(archive(mapOf("a" to "abc")), temporary.newFolder())
        }
        expectFailure("too many files") {
            reader(PluginImportLimits(maxFileCountPerArchive = 1)).extractZip(archive(mapOf("a" to "a", "b" to "b")), temporary.newFolder())
        }
        expectFailure("too deep") {
            reader(PluginImportLimits(maxDepth = 1)).extractZip(archive(mapOf("a/b" to "b")), temporary.newFolder())
        }
    }

    @Test
    fun `manifest discovery enforces plugin count limit`() {
        val root = temporary.newFolder()
        writeManifest(root, "first/manifest.json", manifest("test.first"))
        writeManifest(root, "second/manifest.json", manifest("test.second"))

        expectFailure("too many plugins") { reader(PluginImportLimits(maxPluginCountPerArchive = 1)).findManifestFiles(root) }
    }

    @Test
    fun `entry include and icon paths cannot escape the plugin root`() {
        for ((field, reason) in listOf("entry" to "Unsafe entry path", "includeDirs" to "Unsafe includeDir", "icon" to "Unsafe icon path")) {
            val root = temporary.newFolder()
            File(root, "source.js").writeText("export default {}")
            val value = if (field == "includeDirs") "[\"../outside\"]" else "\"../outside\""
            val text = manifest("test.paths").dropLast(1) + ",\"$field\":$value}"
            val file = writeManifest(root, "manifest.json", text)

            assertTrue(reader().buildCandidate(file, root).exceptionOrNull()?.message.orEmpty().contains(reason))
        }
    }

    @Test
    fun `unsupported protocol and oversized manifest are rejected`() {
        val root = temporary.newFolder()
        val file = writeManifest(root, "manifest.json", manifest("test.api", apiVersion = Int.MAX_VALUE))

        assertTrue(reader().buildCandidate(file, root).exceptionOrNull()?.message.orEmpty().contains("Unsupported plugin apiVersion"))
        assertTrue(reader(PluginImportLimits(maxManifestBytes = 1)).buildCandidate(file, root).exceptionOrNull()?.message.orEmpty().contains("manifest is too large"))
    }

    private fun reader(limits: PluginImportLimits = PluginImportLimits()) = PluginArchiveReader(json, limits)

    private fun manifest(id: String, apiVersion: Int = 1): String = json.encodeToString(
        PluginManifest(id = id, name = id, versionCode = 1, versionName = "1.0", apiVersion = apiVersion)
    )

    private fun archive(files: Map<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private fun writeManifest(root: File, name: String, text: String): File = File(root, name).apply {
        requireNotNull(parentFile).mkdirs()
        writeText(text)
    }

    private fun expectFailure(message: String, action: () -> Unit) {
        val failure = runCatching(action).exceptionOrNull()
        assertTrue("Expected failure containing '$message', got $failure", failure is IllegalArgumentException && failure.message.orEmpty().contains(message))
    }
}
