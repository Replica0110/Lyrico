package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.utils.lyrics.document.LyricsDocumentPipeline
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.utils.LyricDecoder
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.lyrics.LyricsColumnSorter
import com.lonx.lyrico.utils.lyrics.LyricsTextCleanup
import com.lonx.lyrico.data.model.lyrics.LyricsFormatConfig
import kotlinx.serialization.json.Json

class LyricsFormatProcessor(
    private val songLibraryRepository: SongLibraryRepository,
    private val saveAudioTagsUseCase: SaveAudioTagsUseCase,
    private val audioTagRepository: AudioTagRepository
) : BatchTaskProcessor {

    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val config = task.configJson?.let {
            Json.decodeFromString<LyricsFormatConfig>(it)
        } ?: throw BatchTaskSkippedException("No config")

        val song = songLibraryRepository.getSongByUri(item.songUri)
            ?: throw BatchTaskSkippedException("Song not found")

        val lyrics = audioTagRepository.read(song.uri, AudioTagReadOptions(strict = true)).lyrics
        if (lyrics.isNullOrBlank()) {
            throw BatchTaskSkippedException("No lyrics")
        }

        val convertedLyrics = if (config.legacyColumnEdits.isNotEmpty()) {
            val edit = config.legacyColumnEdits[item.songUri]
                ?: throw BatchTaskSkippedException("No column mapping for this song")
            if (LyricsColumnSorter.fingerprint(lyrics) != edit.sourceHash) {
                throw BatchTaskSkippedException("Lyrics changed since the task was created")
            }
            LyricsColumnSorter.apply(lyrics, edit.mapping)
        } else if (config.formatLineOrder) {
            LyricsColumnSorter.apply(lyrics, config.twoColumnMapping, config.threeColumnMapping)
        } else {
            val currentFormat = LyricDecoder.detectFormat(lyrics)
            if (config.targetFormat == null && !config.formatLineOrder && currentFormat != LyricFormat.TTML) {
                LyricsTextCleanup.process(lyrics, config.removeEmptyLines,
                    if (config.removeTagLines) config.tagLineKeywords else emptyList())
            } else {
                val format = currentFormat ?: throw BatchTaskSkippedException("Unknown lyrics format")
                val targetFormat = config.targetFormat ?: format
                val hasTextOperations = config.formatLineOrder || config.removeEmptyLines || config.removeTagLines
                if (format == targetFormat && !hasTextOperations) throw BatchTaskSkippedException("Already target format")
                convertLyricsFormat(lyrics, format, targetFormat, config)
            }
        }
        if (convertedLyrics == lyrics) {
            throw BatchTaskSkippedException("Converted lyrics are unchanged")
        }

        val result = saveAudioTagsUseCase.patch(
            item.songUri,
            AudioTagData(lyrics = convertedLyrics)
        )

        if (result !is SaveAudioTagsResult.Success) {
            throw Exception("Write failed")
        }

        return BatchTaskProcessResult()
    }

    private fun convertLyricsFormat(
        lyrics: String,
        currentFormat: LyricFormat,
        targetFormat: LyricFormat,
        config: LyricsFormatConfig
    ): String {
        val tagLineKeywords = if (config.removeTagLines) config.tagLineKeywords else emptyList()
        if (config.targetFormat == null && !config.formatLineOrder && currentFormat != LyricFormat.TTML) {
            return LyricsTextCleanup.process(
                raw = lyrics,
                removeEmptyLines = config.removeEmptyLines,
                tagLineKeywords = tagLineKeywords
            )
        }

        LyricsDocumentPipeline.process(
            raw = lyrics,
            sourceFormat = currentFormat,
            targetFormat = targetFormat,
            removeEmptyLines = config.removeEmptyLines,
            removeTagLineKeywords = tagLineKeywords
        )?.let { return it }

        val lyricsResult = try {
            LyricDecoder.decode(lyrics)
        } catch (e: Exception) {
            throw Exception("Decode failed: ${e.message ?: e::class.java.simpleName}", e)
        } ?: throw BatchTaskSkippedException("Unknown lyrics format")

        if (lyricsResult.original.isEmpty()) {
            throw BatchTaskSkippedException("No original lyric lines")
        }

        if (targetFormat.requiresWordLevelTiming() && !lyricsResult.isWordByWord) {
            throw BatchTaskSkippedException(
                "Cannot convert $currentFormat to $targetFormat: source lyrics have no word-level timing"
            )
        }

        val config = LyricRenderConfig(
            format = targetFormat,
            conversionMode = ConversionMode.NONE,
            showTranslation = lyricsResult.translated != null,
            showRomanization = lyricsResult.romanization != null,
            removeEmptyLines = config.removeEmptyLines,
            onlyTranslationIfAvailable = false
        )
        val converted = LyricEncoder.encode(lyricsResult, config)
        if (converted.isBlank()) {
            throw Exception("Encode failed: empty result")
        }
        return converted
    }

    private fun LyricFormat.requiresWordLevelTiming(): Boolean {
        return this == LyricFormat.VERBATIM_LRC || this == LyricFormat.ENHANCED_LRC
    }
}
