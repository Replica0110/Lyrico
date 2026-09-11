package com.lonx.lyrico.plugin.source

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * structured 协议扩展解析测试：
 * - Line 第 4 元素（行级扩展属性，前缀白名单过滤）
 * - agents（演唱者列表，id 必填）
 * - metadata 三分支规则（官方 key 按规范保留 / 非官方透传 / 官方 key 错结构丢弃）
 */
class PluginJsonParserTest {
    private val parser = PluginJsonParser(Json)

    private fun structuredJson(
        original: String,
        agents: String? = null,
        metadata: String? = null
    ): String {
        val agentsPart = agents?.let { """, "agents": $it""" } ?: ""
        val metadataPart = metadata?.let { """, "metadata": $it""" } ?: ""
        return """{"type": "structured", "original": $original$agentsPart$metadataPart}"""
    }

    // ---------- Line 第 4 元素：行级扩展属性 ----------

    @Test
    fun lineFourthElementExtensionsParsed() {
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"], [1500, 2000, "前"]], {"ttm:agent": "v1", "itunes:songPart": "Verse"}]]"""
        )
        val result = parser.parseLyrics(json)!!

        assertEquals(
            mapOf("ttm:agent" to "v1", "itunes:songPart" to "Verse"),
            result.original.single().extensions
        )
    }

    @Test
    fun lineFourthElementPrefixWhitelistFiltersUnknownPrefix() {
        // custom: 前缀不在白名单（ttm/itunes/无前缀），解析时即过滤，避免写出非法 XML
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]], {"custom:evil": "x", "ttm:agent": "v1", "id": "abc"}]]"""
        )
        val result = parser.parseLyrics(json)!!

        assertEquals(mapOf("ttm:agent" to "v1", "id" to "abc"), result.original.single().extensions)
    }

    @Test
    fun lineFourthElementMissingDefaultsToEmpty() {
        // 旧插件无第 4 元素 → 空 extensions
        val json = structuredJson(original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""")
        val result = parser.parseLyrics(json)!!

        assertTrue(result.original.single().extensions.isEmpty())
    }

    // ---------- word 第 4 元素：Ruby 注音音节 ----------

    @Test
    fun wordFourthElementMultiSyllableRubyParsed() {
        // 多音节：基文本「詮」→ せ / ん，音节与词同构 [startMs, endMs, text]
        val json = structuredJson(
            original = """[[27000, 28000, [[27820, 27950, "詮", [[27820, 27880, "せ"], [27880, 27950, "ん"]]]]]]"""
        )
        val result = parser.parseLyrics(json)!!

        val ruby = result.original.single().words.single().ruby
        assertEquals(listOf("せ", "ん"), ruby?.map { it.text })
        assertEquals(listOf(27820L, 27880L), ruby?.map { it.start })
        assertEquals(listOf(27880L, 27950L), ruby?.map { it.end })
    }

    @Test
    fun wordFourthElementSingleSyllableRubyParsed() {
        // 单音节也是单元素数组
        val json = structuredJson(
            original = """[[27000, 28000, [[27690, 27820, "所", [[27690, 27820, "しょ"]]]]]]"""
        )
        val result = parser.parseLyrics(json)!!

        val ruby = result.original.single().words.single().ruby
        assertEquals(1, ruby?.size)
        assertEquals("しょ", ruby?.single()?.text)
    }

    @Test
    fun wordFourthElementRubyTimingOptional() {
        // 音节时间可缺省（宿主写回时用词时间兜底）
        val json = structuredJson(
            original = """[[27000, 28000, [[27820, 27950, "詮", [[null, null, "せん"]]]]]]"""
        )
        val result = parser.parseLyrics(json)!!

        val ruby = result.original.single().words.single().ruby
        assertEquals("せん", ruby?.single()?.text)
        assertNull(ruby?.single()?.start)
        assertNull(ruby?.single()?.end)
    }

    @Test
    fun wordWithoutRubyFourthElementDefaultsNull() {
        // 旧插件词只有 3 元素 → ruby 为 null
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"], [1500, 2000, "前"]]]]"""
        )
        val result = parser.parseLyrics(json)!!

        assertTrue(result.original.single().words.all { it.ruby == null })
    }

    @Test
    fun wordFourthElementEmptyOrInvalidRubyIgnored() {
        // 空数组 / 非数组 / 音节文本缺失 → ruby 为 null（词本身保留，不影响行解析）
        val json = structuredJson(
            original = """[[1000, 2000, [
                [1000, 1200, "眼", []],
                [1200, 1500, "前", "ruby"],
                [1500, 2000, "詮", [[1500, 2000, ""]]]
            ]]]"""
        )
        val result = parser.parseLyrics(json)!!

        assertTrue(result.original.single().words.all { it.ruby == null })
    }

    // ---------- agents：演唱者列表 ----------

    @Test
    fun agentsParsedFromObjectArray() {
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""",
            agents = """[{"id": "v1", "type": "person", "name": "演唱者A"}, {"id": "v1000", "type": "group"}]"""
        )
        val result = parser.parseLyrics(json)!!

        assertEquals(2, result.agents.size)
        assertEquals("v1", result.agents[0].id)
        assertEquals("person", result.agents[0].type)
        assertEquals("演唱者A", result.agents[0].name)
        assertEquals("v1000", result.agents[1].id)
        assertNull(result.agents[1].name)
    }

    @Test
    fun agentsEntryWithoutIdDropped() {
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""",
            agents = """[{"type": "person"}, {"id": "v1"}]"""
        )
        val result = parser.parseLyrics(json)!!

        assertEquals(1, result.agents.size)
        assertEquals("v1", result.agents[0].id)
    }

    // ---------- metadata 三分支规则 ----------

    @Test
    fun metadataOfficialSongwritersKept() {
        // 官方 key + 官方结构：songwriters 包裹带文本的 songwriter children → 保留
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""",
            metadata = """[{"name": "songwriters", "children": [{"name": "songwriter", "text": "BuzzY.D"}, {"name": "songwriter", "text": "NKidd"}]}]"""
        )
        val result = parser.parseLyrics(json)!!

        assertEquals(1, result.metadata.size)
        val songwriters = result.metadata[0]
        assertEquals("songwriters", songwriters.name)
        assertEquals(2, songwriters.children.size)
        assertEquals("BuzzY.D", songwriters.children[0].text)
        assertEquals("NKidd", songwriters.children[1].text)
    }

    @Test
    fun metadataOfficialSongwritersWrongStructureDropped() {
        // 官方 key + 错误结构（songWriters 顶层直接放字符串）→ 丢弃 + warn
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""",
            metadata = """[{"name": "songwriters", "text": "BuzzY.D"}]"""
        )
        val result = parser.parseLyrics(json)!!

        assertTrue(result.metadata.isEmpty())
    }

    @Test
    fun metadataOfficialSongwritersWrongChildNameDropped() {
        // 官方 key + children 名不是 songwriter → 丢弃
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""",
            metadata = """[{"name": "songwriters", "children": [{"name": "writer", "text": "BuzzY.D"}]}]"""
        )
        val result = parser.parseLyrics(json)!!

        assertTrue(result.metadata.isEmpty())
    }

    @Test
    fun metadataOfficialSongwritersEmptyChildrenDropped() {
        // 官方 key + 空 children → 丢弃
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""",
            metadata = """[{"name": "songwriters", "children": []}]"""
        )
        val result = parser.parseLyrics(json)!!

        assertTrue(result.metadata.isEmpty())
    }

    @Test
    fun metadataDuplicatedOfficialKeysDropped() {
        // 官方 key 但已有专门字段承载（translated/romanization/agents）→ 丢弃 + warn
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""",
            metadata = """[{"name": "translations"}, {"name": "transliterations"}, {"name": "ttm:agent"}]"""
        )
        val result = parser.parseLyrics(json)!!

        assertTrue(result.metadata.isEmpty())
    }

    @Test
    fun metadataNonOfficialKeysPassthrough() {
        // 非官方 key → 原样透传（保留树结构：attributes/text/children）
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""",
            metadata = """[{"name": "myExt", "namespace": "http://example.com/ns/my", "attributes": {"foo": "bar"}, "children": [{"name": "item", "text": "a"}, {"name": "item", "text": "b"}]}]"""
        )
        val result = parser.parseLyrics(json)!!

        assertEquals(1, result.metadata.size)
        val ext = result.metadata[0]
        assertEquals("myExt", ext.name)
        assertEquals("http://example.com/ns/my", ext.namespace)
        assertEquals(mapOf("foo" to "bar"), ext.attributes)
        assertEquals(2, ext.children.size)
        assertEquals("a", ext.children[0].text)
        assertEquals("b", ext.children[1].text)
    }

    @Test
    fun metadataCamelCaseSongwritersTreatedAsPassthrough() {
        // 官方 key 大小写敏感：songWriters（camelCase）不是官方元素名 → 按非官方透传，不做归一化
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""",
            metadata = """[{"name": "songWriters", "children": [{"name": "songWriter", "text": "BuzzY.D"}]}]"""
        )
        val result = parser.parseLyrics(json)!!

        assertEquals(1, result.metadata.size)
        assertEquals("songWriters", result.metadata[0].name)
    }

    @Test
    fun metadataPrefixedNameWithoutNamespaceDropped() {
        // 带前缀（非 ttm/itunes/xml）但未提供 namespace URI → 丢弃（写出会破坏 XML）
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""",
            metadata = """[{"name": "amll:meta", "attributes": {"key": "musicName"}}]"""
        )
        val result = parser.parseLyrics(json)!!

        assertTrue(result.metadata.isEmpty())
    }

    @Test
    fun metadataAmmMetaWithNamespacePassthrough() {
        // amll:meta 带命名空间 → 透传保留
        val json = structuredJson(
            original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""",
            metadata = """[{"name": "amll:meta", "namespace": "http://www.example.com/ns/amll", "attributes": {"key": "musicName", "value": "歌曲名"}}]"""
        )
        val result = parser.parseLyrics(json)!!

        assertEquals(1, result.metadata.size)
        assertEquals("amll:meta", result.metadata[0].name)
        assertEquals("http://www.example.com/ns/amll", result.metadata[0].namespace)
    }

    // ---------- bodyDur：<body dur> 参考总时长 ----------

    @Test
    fun bodyDurParsedAsRawTimeExpression() {
        // 驼峰 bodyDur：TTML 时间字符串原文透传，不做转换
        val json = """{"type": "structured", "original": [[1000, 2000, [[1000, 1500, "眼"]]]], "bodyDur": "04:24.660"}"""
        val result = parser.parseLyrics(json)!!

        assertEquals("04:24.660", result.bodyDur)
    }

    @Test
    fun bodyDurSnakeCaseAlsoAccepted() {
        // 下划线 body_dur 兼容写法
        val json = """{"type": "structured", "original": [[1000, 2000, [[1000, 1500, "眼"]]]], "body_dur": "00:10.000"}"""
        val result = parser.parseLyrics(json)!!

        assertEquals("00:10.000", result.bodyDur)
    }

    @Test
    fun bodyDurMissingDefaultsToEmpty() {
        // 旧插件不传 → 空字符串（写回 <body> 不带 dur）
        val json = structuredJson(original = """[[1000, 2000, [[1000, 1500, "眼"]]]]""")
        val result = parser.parseLyrics(json)!!

        assertEquals("", result.bodyDur)
    }
}
