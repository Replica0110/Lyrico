package com.lonx.lyrico.plugin.runtime

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun readsExistingFormatAndRemovesExpiredOrCorruptEntries() {
        val root = temporaryFolder.newFolder()
        val cache = PluginCache("provider", root, Json)
        val directory = File(root, "9e9f3d70bd8c8957627eada96d967706").apply { mkdirs() }
        val entry = File(directory, "368cc7b17925cc0f9c4b5a90db1e68c1.json")

        entry.writeText("""{"value":"旧缓存","expiresAt":0}""")
        assertEquals("旧缓存", cache.get(" track "))

        entry.writeText("""{"value":"expired","expiresAt":1}""")
        assertEquals("", cache.get("track"))
        assertFalse(entry.exists())

        entry.writeText("invalid json")
        assertEquals("", cache.get("track"))
        assertFalse(entry.exists())
    }

    @Test
    fun preservesHashedPathsAndIsolatesPluginRemoval() {
        val root = temporaryFolder.newFolder()
        val first = PluginCache("first", root, Json)
        val second = PluginCache("second", root, Json)
        first.set(" ../song ", "first value", 0)
        second.set("../song", "second value", null)
        assertTrue(File(File(root, "8b04d5e3775d298e78455efc5ca404d5"), "288672c75b851a6307333f5cc5159655.json").isFile)
        assertEquals("first value", first.get("../song"))
        assertEquals("second value", second.get("../song"))

        first.remove(" ../song ")
        assertEquals("", first.get("../song"))
        assertEquals("second value", second.get("../song"))
        first.set("another", "value", -1)
        first.clear()
        assertEquals("", first.get("another"))
        assertEquals("second value", second.get("../song"))
    }

    @Test
    fun keepsDefaultPluginPathAndIgnoresUnavailableStorageOrBlankKeys() {
        val root = temporaryFolder.newFolder()
        val blankPlugin = PluginCache(" ", root, Json)
        val defaultPlugin = PluginCache("default", root, Json)
        blankPlugin.set("key", "value", null)
        assertEquals("value", defaultPlugin.get("key"))
        blankPlugin.set(" ", "unused", null)
        assertEquals("", blankPlugin.get(" "))

        val unavailable = PluginCache("first", null, Json)
        unavailable.set("key", "unused", 100)
        assertEquals("", unavailable.get("key"))
        unavailable.remove("key")
        unavailable.clear()
    }
}
