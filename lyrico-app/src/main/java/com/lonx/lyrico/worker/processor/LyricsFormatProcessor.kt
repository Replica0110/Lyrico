package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.lyrics.LyricFormat
import com.lonx.lyrico.data.db.entity.BatchTaskEntity
import com.lonx.lyrico.data.db.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.domain.lyrics.LyricDecoder
import com.lonx.lyrico.domain.lyrics.LyricsColumnSorter
import com.lonx.lyrico.domain.lyrics.LyricsFormatConverter
import com.lonx.lyrico.domain.lyrics.LyricsTextCleanup
import com.lonx.lyrico.data.lyrics.LyricsFormatConfig
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
                ?: throw BatchTaskSkippedException("Unknown lyrics format")
            val tagLineKeywords = if (config.removeTagLines) config.tagLineKeywords else emptyList()

            if (config.targetFormat == null && currentFormat != LyricFormat.TTML) {
                LyricsTextCleanup.process(lyrics, config.removeEmptyLines, tagLineKeywords)
            } else {
                val targetFormat = config.targetFormat ?: currentFormat
                val hasTextOperations = config.removeEmptyLines || config.removeTagLines
                if (currentFormat == targetFormat && !hasTextOperations) {
                    throw BatchTaskSkippedException("Already target format")
                }
                LyricsFormatConverter.convert(
                    raw = lyrics,
                    targetFormat = targetFormat,
                    removeEmptyLines = config.removeEmptyLines,
                    removeTagLineKeywords = tagLineKeywords,
                    requireWordLevelTiming = true,
                ) ?: throw BatchTaskSkippedException("Cannot convert lyrics to $targetFormat")
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
}
