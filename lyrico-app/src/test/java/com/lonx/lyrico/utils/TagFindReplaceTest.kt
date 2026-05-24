package com.lonx.lyrico.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TagFindReplaceTest {
    @Test
    fun replaceGenre_removesOnlyMatchedToken() {
        val config = TagFindReplaceConfig(find = "kuwo")

        assertEquals(
            "Pop; Rock",
            TagFindReplace.replaceValue(TagTextField.GENRE, "Pop; kuwo; Rock", config)
        )
    }

    @Test
    fun replaceGenre_supportsCommonSeparators() {
        val config = TagFindReplaceConfig(find = "kuwo")

        assertEquals(
            "Pop; Rock; Jazz",
            TagFindReplace.replaceValue(TagTextField.GENRE, "Pop / kuwo，Rock、Jazz", config)
        )
    }

    @Test
    fun replaceText_removesKeywordAndTrimsSpaces() {
        val config = TagFindReplaceConfig(find = "kuwo")

        assertEquals(
            "from music",
            TagFindReplace.replaceValue(TagTextField.COMMENT, "from kuwo music", config)
        )
    }

    @Test
    fun replaceText_clearsWhenOnlyKeyword() {
        val config = TagFindReplaceConfig(find = "kuwo")

        assertEquals("", TagFindReplace.replaceValue(TagTextField.COMMENT, "kuwo", config))
    }

    @Test
    fun replaceText_exactMatchDoesNotTouchNormalComment() {
        val config = TagFindReplaceConfig(
            find = "kuwo",
            mode = TagFindReplaceMode.EXACT
        )

        assertEquals(
            "from kuwo music",
            TagFindReplace.replaceValue(TagTextField.COMMENT, "from kuwo music", config)
        )
    }

    @Test
    fun replaceText_regexSupportsIgnoreCase() {
        val config = TagFindReplaceConfig(
            find = """kuwo\s+music""",
            mode = TagFindReplaceMode.REGEX
        )

        assertEquals(
            "from",
            TagFindReplace.replaceValue(TagTextField.COMMENT, "from KuWo music", config)
        )
    }
}
