package com.lonx.lyrico.plugin

import com.lonx.lyrico.plugin.distribution.PluginCatalog
import com.lonx.lyrico.plugin.distribution.PluginCatalogParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PluginUrlTest {
    @Test
    fun normalizesHttpUrlsAndRetainsQueries() {
        assertEquals("https://example.com/", requirePluginUrl("https://EXAMPLE.com:443"))
        assertEquals(
            "http://example.com/catalog.json?channel=beta",
            requirePluginUrl("http://example.com/catalog.json?channel=beta"),
        )
    }

    @Test
    fun rejectsCredentialsFragmentsAndNonHttpSchemes() {
        for (url in listOf(
            "https://user:password@example.com/catalog.json",
            "https://example.com/catalog.json#fragment",
            "file:///storage/plugins/catalog.json",
            "ftp://example.com/catalog.json",
        )) {
            assertThrows(url, IllegalArgumentException::class.java) { requirePluginUrl(url) }
        }
    }

    @Test
    fun catalogResolvesRelativeDownloadsAndReadsExistingCacheFormat() {
        val catalog = PluginCatalogParser.parse(
            """
                {"schemaVersion":1,"plugins":[{
                    "id":"org.example.source","name":"Example","versionCode":1,"versionName":"1.0",
                    "apiVersion":1,"downloadUrl":"packages/source.zip","sha256":"${"a".repeat(64)}","size":100
                }]}
            """.trimIndent(),
            "https://example.com/plugins/catalog.json",
        )

        assertEquals("https://example.com/plugins/packages/source.zip", catalog.plugins.single().downloadUrl)
        val persisted = Json.encodeToString(catalog)
        assertEquals(catalog, Json.decodeFromString<PluginCatalog>(persisted))
        assertEquals(catalog, PluginCatalogParser.parse(persisted, "https://example.com/plugins/catalog.json"))
    }
}
