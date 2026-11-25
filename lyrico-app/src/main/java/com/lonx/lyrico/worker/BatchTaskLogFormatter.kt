package com.lonx.lyrico.worker

import com.lonx.lyrico.data.batch.BatchTaskType
import com.lonx.lyrico.data.lyrics.LyricsFormatConfig
import com.lonx.lyrico.worker.processor.BatchExportTaskConfig
import com.lonx.lyrico.worker.processor.EditTagsTaskConfig
import com.lonx.lyrico.worker.processor.MatchMetadataTaskConfig
import com.lonx.lyrico.worker.processor.RenameFilesTaskConfig
import com.lonx.lyrico.worker.processor.ReplayGainTaskConfig
import kotlinx.serialization.json.Json

/** 批量任务配置与结果的日志格式，不读取或修改任务运行状态。 */
internal object BatchTaskLogFormatter {
    const val MAX_ITEM_DETAILS = 50
    private const val MAX_CONFIG_VALUE_LENGTH = 200

    fun formatSummary(
        taskId: String,
        taskType: BatchTaskType,
        status: String,
        configSummary: String,
        startedAt: Long,
        total: Int,
        pending: Int,
        concurrency: Int,
        processed: Int,
        success: Int,
        skipped: Int,
        failure: Int,
        itemDetails: List<String>,
        finishedAt: Long = System.currentTimeMillis(),
    ): String = buildString {
        appendLine("taskId=$taskId")
        appendLine("type=$taskType")
        appendLine("status=$status")
        appendLine()
        appendLine("configuration:")
        appendLine(configSummary.prependIndent("  "))
        appendLine()
        appendLine("startedAt=$startedAt")
        appendLine("finishedAt=$finishedAt")
        appendLine("durationMs=${finishedAt - startedAt}")
        appendLine("total=$total")
        appendLine("items=$pending")
        appendLine("concurrency=$concurrency")
        appendLine("processed=$processed")
        appendLine("success=$success")
        appendLine("skipped=$skipped")
        appendLine("failure=$failure")
        if (itemDetails.isNotEmpty()) {
            appendLine()
            appendLine("items:")
            itemDetails.take(MAX_ITEM_DETAILS).forEach { detail ->
                appendLine(detail.trimEnd())
                appendLine()
            }
            val omitted = skipped + failure - itemDetails.size
            if (omitted > 0) {
                appendLine("... $omitted more item details omitted")
            }
        }
    }

    fun formatConfiguration(type: BatchTaskType, configJson: String?): String {
        if (configJson.isNullOrBlank()) return "config=(none)"
        return runCatching {
            when (type) {
                BatchTaskType.MATCH_METADATA,
                BatchTaskType.MATCH_LYRICS,
                BatchTaskType.MATCH_COVER -> summarizeMatchConfig(configJson)
                BatchTaskType.RENAME_FILES -> summarizeRenameConfig(configJson)
                BatchTaskType.EDIT_TAGS -> summarizeEditTagsConfig(configJson)
                BatchTaskType.CONVERT_LYRICS_FORMAT -> summarizeLyricsFormatConfig(configJson)
                BatchTaskType.SCAN_REPLAY_GAIN -> summarizeReplayGainConfig(configJson)
                BatchTaskType.EXPORT_LYRICS,
                BatchTaskType.EXPORT_COVER -> summarizeExportConfig(configJson)
            }
        }.getOrElse { e ->
            buildString {
                appendLine("configParseError=${e.message ?: e::class.java.simpleName}")
                appendLine("configCharacters=${configJson.length}")
            }.trimEnd()
        }
    }

    private fun summarizeMatchConfig(configJson: String): String {
        val config = Json.decodeFromString<MatchMetadataTaskConfig>(configJson)
        return buildString {
            appendLine("concurrency=${config.concurrency}")
            appendLine("separator=${config.separator}")
            appendLine("preferFileName=${config.matchConfig.preferFileName}")
            appendLine("enabledSources=${config.enabledSourceOrderIds.joinToString(" > ").ifBlank { "(default)" }}")
            appendLine("fields=${config.matchConfig.targetModes.toSortedMap(compareBy { it.name }).entries.joinToString(", ") { "${it.key.name}:${it.value.name}" }}")
        }.trimEnd()
    }

    private fun summarizeRenameConfig(configJson: String): String {
        val config = Json.decodeFromString<RenameFilesTaskConfig>(configJson)
        return buildString {
            appendLine("renameFormat=${config.renameFormat}")
            appendLine("characterMappingRules=${config.characterMappingRules.size}")
            val customizedRules = config.characterMappingRules.filter { rule ->
                rule.charMappings.isNotEmpty()
            }
            appendLine("customizedMappingRules=${customizedRules.joinToString(", ") { it.name }.ifBlank { "(none)" }}")
        }.trimEnd()
    }

    private fun summarizeEditTagsConfig(configJson: String): String {
        val config = Json.decodeFromString<EditTagsTaskConfig>(configJson)
        val keep = EditTagsTaskConfig.KEEP_VALUE
        val modifiedFields = buildList {
            if (config.title != keep) add("title")
            if (config.artist != keep) add("artist")
            if (config.albumArtist != keep) add("albumArtist")
            if (config.album != keep) add("album")
            if (config.date != keep) add("date")
            if (config.genre != keep) add("genre")
            if (config.trackNumber != keep) add("trackNumber")
            if (config.discNumber != keep) add("discNumber")
            if (config.composer != keep) add("composer")
            if (config.lyricist != keep) add("lyricist")
            if (config.copyright != keep) add("copyright")
            if (config.comment != keep) add("comment")
            if (config.lyrics != keep) add("lyrics")
            if (config.ratingModified) add("rating")
            if (config.coverUri != null) add("cover")
            if (config.removeCover) add("removeFrontCover")
            if (config.lyricsOffset.isNotBlank()) add("lyricsOffset")
            if (config.replayGainTrackGain != keep) add("replayGainTrackGain")
            if (config.replayGainTrackPeak != keep) add("replayGainTrackPeak")
            if (config.replayGainAlbumGain != keep) add("replayGainAlbumGain")
            if (config.replayGainAlbumPeak != keep) add("replayGainAlbumPeak")
            if (config.replayGainReferenceLoudness != keep) add("replayGainReferenceLoudness")
            if (config.customFields.isNotEmpty()) add("customFields")
        }

        return buildString {
            appendLine("concurrency=${config.concurrency}")
            appendLine("modifiedFields=${modifiedFields.joinToString(", ").ifBlank { "(none)" }}")
            if (config.ratingModified) appendLine("rating=${config.rating}")
            if (config.lyricsOffset.isNotBlank()) appendLine("lyricsOffset=${config.lyricsOffset}")
            appendLine("customFieldKeys=${config.customFields.joinToString(", ") { it.key }.ifBlank { "(none)" }}")
            appendSanitizedValue("title", config.title, keep)
            appendSanitizedValue("artist", config.artist, keep)
            appendSanitizedValue("albumArtist", config.albumArtist, keep)
            appendSanitizedValue("album", config.album, keep)
            appendSanitizedValue("date", config.date, keep)
            appendSanitizedValue("genre", config.genre, keep)
            appendSanitizedValue("trackNumber", config.trackNumber, keep)
            appendSanitizedValue("discNumber", config.discNumber, keep)
            appendSanitizedValue("composer", config.composer, keep)
            appendSanitizedValue("lyricist", config.lyricist, keep)
            appendSanitizedValue("copyright", config.copyright, keep)
            appendSanitizedValue("comment", config.comment, keep)
            appendSanitizedValue("replayGainTrackGain", config.replayGainTrackGain, keep)
            appendSanitizedValue("replayGainTrackPeak", config.replayGainTrackPeak, keep)
            appendSanitizedValue("replayGainAlbumGain", config.replayGainAlbumGain, keep)
            appendSanitizedValue("replayGainAlbumPeak", config.replayGainAlbumPeak, keep)
            appendSanitizedValue("replayGainReferenceLoudness", config.replayGainReferenceLoudness, keep)
            if (config.lyrics != keep) appendLine("lyrics=(modified, ${config.lyrics.length} chars)")
            if (config.coverUri != null) appendLine("coverUri=(set)")
            if (config.removeCover) appendLine("removeFrontCover=true")
        }.trimEnd()
    }

    private fun summarizeLyricsFormatConfig(configJson: String): String {
        val config = Json.decodeFromString<LyricsFormatConfig>(configJson)
        return buildString {
            appendLine("targetFormat=${config.targetFormat?.name ?: "KEEP"}")
            appendLine("concurrency=${config.concurrency}")
            appendLine("operation=${config.operation}")
            appendLine("twoColumnMapping=${config.twoColumnMapping}")
            appendLine("threeColumnMapping=${config.threeColumnMapping}")
            appendLine("formatLineOrder=${config.formatLineOrder}")
            appendLine("removeTagLines=${config.removeTagLines}")
            appendLine("removeEmptyLines=${config.removeEmptyLines}")
        }.trimEnd()
    }

    private fun summarizeReplayGainConfig(configJson: String): String {
        val config = Json.decodeFromString<ReplayGainTaskConfig>(configJson)
        return buildString {
            appendLine("concurrency=${config.concurrency}")
            config.targetLoudness?.let { appendLine("targetLoudness=$it") }
            config.peakMode?.let { appendLine("peakMode=${it.name}") }
        }.trimEnd()
    }

    private fun summarizeExportConfig(configJson: String): String {
        val config = Json.decodeFromString<BatchExportTaskConfig>(configJson)
        return buildString {
            appendLine("destination=${config.destination}")
            if (config.destinationTreeUri != null) {
                appendLine("destinationTreeUri=${config.destinationTreeUri}")
            }
            appendLine("concurrency=${config.concurrency}")
        }.trimEnd()
    }

    private fun StringBuilder.appendSanitizedValue(
        name: String,
        value: String,
        keep: String
    ) {
        if (value == keep) return
        appendLine("$name=${if (name == "lyrics" || name == "comment") "set (${value.length} chars)" else value.sanitizeConfigValue()}")
    }

    private fun String.sanitizeConfigValue(): String {
        val oneLine = replace("\r", "\\r").replace("\n", "\\n")
        return if (oneLine.length <= MAX_CONFIG_VALUE_LENGTH) {
            oneLine
        } else {
            "${oneLine.take(MAX_CONFIG_VALUE_LENGTH)}... (${length} chars)"
        }
    }

    fun formatMessage(
        type: BatchTaskType,
        status: String,
        processed: Int,
        success: Int,
        skipped: Int,
        failure: Int
    ): String {
        return "Batch task $status: $type (processed=$processed, success=$success, skipped=$skipped, failure=$failure)"
    }
}
