package com.lonx.lyrico.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TagKeywordCleanerTest {
    @Test
    fun cleanGenre_removesOnlyMatchedToken() {
        val config = TagKeywordCleanConfig(keyword = "kuwo")

        assertEquals(
            "Pop; Rock",
            TagKeywordCleaner.cleanGenre("Pop; kuwo; Rock", config)
        )
    }

    @Test
    fun cleanGenre_supportsCommonSeparators() {
        val config = TagKeywordCleanConfig(keyword = "kuwo")

        assertEquals(
            "Pop; Rock; Jazz",
            TagKeywordCleaner.cleanGenre("Pop / kuwo，Rock、Jazz", config)
        )
    }

    @Test
    fun cleanComment_removesKeywordAndTrimsSpaces() {
        val config = TagKeywordCleanConfig(keyword = "kuwo")

        assertEquals(
            "from music",
            TagKeywordCleaner.cleanComment("from kuwo music", config)
        )
    }

    @Test
    fun cleanComment_clearsWhenOnlyKeyword() {
        val config = TagKeywordCleanConfig(keyword = "kuwo")

        assertEquals("", TagKeywordCleaner.cleanComment("kuwo", config))
    }

    @Test
    fun cleanComment_exactMatchDoesNotTouchNormalComment() {
        val config = TagKeywordCleanConfig(
            keyword = "kuwo",
            matchMode = TagKeywordMatchMode.EXACT
        )

        assertEquals(
            "from kuwo music",
            TagKeywordCleaner.cleanComment("from kuwo music", config)
        )
    }

    @Test
    fun cleanComment_regexSupportsIgnoreCase() {
        val config = TagKeywordCleanConfig(
            keyword = """kuwo\s+music""",
            matchMode = TagKeywordMatchMode.REGEX
        )

        assertEquals(
            "from",
            TagKeywordCleaner.cleanComment("from KuWo music", config)
        )
    }
}
