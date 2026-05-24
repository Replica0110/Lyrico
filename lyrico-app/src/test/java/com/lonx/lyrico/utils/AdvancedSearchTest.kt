package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.entity.SongEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedSearchTest {
    @Test
    fun matchesLyricsAgainstPlainTextWithoutTimeline() {
        val song = testSong(
            lyrics = """
                [00:01.000]hello
                [00:02.000]world
            """.trimIndent()
        )

        assertTrue(
            AdvancedSearch.matches(
                song = song,
                conditions = listOf(
                    AdvancedSearchCondition(
                        field = TagTextField.LYRICS,
                        operator = AdvancedSearchOperator.CONTAINS,
                        value = "hello\nworld"
                    )
                ),
                joinMode = AdvancedSearchJoinMode.AND
            )
        )
    }

    @Test
    fun matchesLyricsRegexAgainstPlainTextWithoutTimeline() {
        val song = testSong(
            lyrics = """
                [00:01.000]hello
                [00:02.000]world
            """.trimIndent()
        )

        assertTrue(
            AdvancedSearch.matches(
                song = song,
                conditions = listOf(
                    AdvancedSearchCondition(
                        field = TagTextField.LYRICS,
                        operator = AdvancedSearchOperator.REGEX,
                        value = "hello\\s+world"
                    )
                ),
                joinMode = AdvancedSearchJoinMode.AND
            )
        )
    }

    @Test
    fun doesNotMatchLyricsTimelineTextAfterPlainTextEncoding() {
        val song = testSong(
            lyrics = "[00:01.000]hello"
        )

        assertFalse(
            AdvancedSearch.matches(
                song = song,
                conditions = listOf(
                    AdvancedSearchCondition(
                        field = TagTextField.LYRICS,
                        operator = AdvancedSearchOperator.CONTAINS,
                        value = "00:01"
                    )
                ),
                joinMode = AdvancedSearchJoinMode.AND
            )
        )
    }

    private fun testSong(lyrics: String): SongEntity {
        return SongEntity(
            folderId = 1,
            mediaId = 1,
            filePath = "/music/test.mp3",
            fileName = "test.mp3",
            lyrics = lyrics
        )
    }
}
