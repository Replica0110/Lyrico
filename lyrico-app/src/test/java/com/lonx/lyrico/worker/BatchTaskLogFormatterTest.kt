package com.lonx.lyrico.worker

import com.lonx.lyrico.data.model.batch.BatchTaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchTaskLogFormatterTest {
    @Test
    fun absentConfigurationUsesExistingPlaceholder() {
        for (type in BatchTaskType.entries) {
            for (config in listOf(null, "", " \n")) {
                assertEquals("config=(none)", BatchTaskLogFormatter.formatConfiguration(type, config))
            }
        }
    }

    @Test
    fun everyTaskTypeUsesItsConfigurationFormat() {
        val match = """{"matchConfig":{"targetModes":{}},"separator":"/","enabledSourceOrderIds":[]}"""
        val cases = mapOf(
            BatchTaskType.MATCH_METADATA to (match to "enabledSources=(default)"),
            BatchTaskType.MATCH_LYRICS to (match to "enabledSources=(default)"),
            BatchTaskType.MATCH_COVER to (match to "enabledSources=(default)"),
            BatchTaskType.RENAME_FILES to ("""{"renameFormat":"@1 - @2"}""" to "renameFormat=@1 - @2"),
            BatchTaskType.EDIT_TAGS to ("{}" to "modifiedFields=(none)"),
            BatchTaskType.CONVERT_LYRICS_FORMAT to ("""{"concurrency":2}""" to "targetFormat=KEEP"),
            BatchTaskType.SCAN_REPLAY_GAIN to ("""{"concurrency":2}""" to "concurrency=2"),
            BatchTaskType.EXPORT_LYRICS to ("{}" to "destination=SELECTED_DIRECTORY"),
            BatchTaskType.EXPORT_COVER to ("{}" to "destination=SELECTED_DIRECTORY"),
        )

        assertEquals(BatchTaskType.entries.toSet(), cases.keys)
        for ((type, case) in cases) {
            val summary = BatchTaskLogFormatter.formatConfiguration(type, case.first)
            assertTrue("$type: $summary", case.second in summary.lines())
            assertFalse(summary, summary.contains("configParseError="))
        }
    }

    @Test
    fun invalidConfigurationProducesDiagnosticInsteadOfThrowing() {
        for (config in listOf("not-json", "{}")) {
            val summary = BatchTaskLogFormatter.formatConfiguration(BatchTaskType.RENAME_FILES, config)

            assertTrue(summary, summary.startsWith("configParseError="))
            assertTrue(summary, summary.endsWith("configCharacters=${config.length}"))
        }
    }

    @Test
    fun editedFieldsDistinguishClearingFromKeepingAndSummarizeLongText() {
        val summary = BatchTaskLogFormatter.formatConfiguration(
            BatchTaskType.EDIT_TAGS,
            """{"title":"","comment":"Private comment","lyrics":"Private lyrics","removeCover":true}""",
        )

        assertTrue(summary, "modifiedFields=title, comment, lyrics, removeFrontCover" in summary.lines())
        assertTrue(summary, "title=" in summary.lines())
        assertTrue(summary, "comment=set (15 chars)" in summary.lines())
        assertTrue(summary, "lyrics=(modified, 14 chars)" in summary.lines())
        assertFalse(summary.contains("Private comment"))
        assertFalse(summary.contains("Private lyrics"))
        assertFalse(summary.contains("artist="))
        assertFalse(summary.contains("<keep>"))
    }

    @Test
    fun fieldValuesEscapeNewlinesAndRetainExistingTruncationLimit() {
        val short = BatchTaskLogFormatter.formatConfiguration(
            BatchTaskType.EDIT_TAGS,
            """{"title":"Line one\r\nLine two"}""",
        )
        val long = BatchTaskLogFormatter.formatConfiguration(
            BatchTaskType.EDIT_TAGS,
            """{"title":"${"a".repeat(201)}"}""",
        )

        assertTrue(short, "title=Line one\\r\\nLine two" in short.lines())
        assertTrue(long, "title=${"a".repeat(200)}... (201 chars)" in long.lines())
    }

    @Test
    fun summaryIncludesTaskIdentityCountsAndElapsedTime() {
        assertEquals(
            """
                taskId=task-42
                type=EDIT_TAGS
                status=finished_with_errors

                configuration:
                  concurrency=3
                  modifiedFields=title

                startedAt=1000
                finishedAt=1250
                durationMs=250
                total=12
                items=10
                concurrency=3
                processed=10
                success=7
                skipped=2
                failure=1
            """.trimIndent() + "\n",
            BatchTaskLogFormatter.formatSummary(
                taskId = "task-42",
                taskType = BatchTaskType.EDIT_TAGS,
                status = "finished_with_errors",
                configSummary = "concurrency=3\nmodifiedFields=title",
                startedAt = 1000L,
                total = 12,
                pending = 10,
                concurrency = 3,
                processed = 10,
                success = 7,
                skipped = 2,
                failure = 1,
                itemDetails = emptyList(),
                finishedAt = 1250L,
            ),
        )
    }

    @Test
    fun summaryLimitsItemDetailsAndReportsUnsampledFailures() {
        val summary = BatchTaskLogFormatter.formatSummary(
            taskId = "task-42",
            taskType = BatchTaskType.EDIT_TAGS,
            status = "finished_with_errors",
            configSummary = "config=(none)",
            startedAt = 1000L,
            total = 60,
            pending = 60,
            concurrency = 3,
            processed = 60,
            success = 0,
            skipped = 10,
            failure = 50,
            itemDetails = (1..50).map { "FAILED item=$it: reason\n" },
            finishedAt = 1250L,
        )

        assertEquals(50, summary.lines().count { it.startsWith("FAILED item=") })
        assertTrue(summary, summary.contains("FAILED item=50: reason\n\n"))
        assertTrue(summary, summary.endsWith("... 10 more item details omitted\n"))
    }

    @Test
    fun completionMessageRetainsExistingCountLabels() {
        assertEquals(
            "Batch task finished: EDIT_TAGS (processed=10, success=7, skipped=2, failure=1)",
            BatchTaskLogFormatter.formatMessage(BatchTaskType.EDIT_TAGS, "finished", 10, 7, 2, 1),
        )
    }
}
