package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.domain.song.usecase.PatchSongTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.utils.LyricDecoder
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.viewmodel.LyricsFormatConfig
import kotlinx.serialization.json.Json

class LyricsFormatProcessor(
    private val songLibraryRepository: SongLibraryRepository,
    private val patchSongTagsUseCase: PatchSongTagsUseCase
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

        val lyrics = song.lyrics
        if (lyrics.isNullOrBlank()) {
            throw BatchTaskSkippedException("No lyrics")
        }

        val currentFormat = LyricDecoder.detectFormat(lyrics)
            ?: throw BatchTaskSkippedException("Unknown lyrics format")
        if (currentFormat == config.targetFormat) {
            throw BatchTaskSkippedException("Already target format")
        }

        val convertedLyrics = convertLyricsFormat(lyrics, currentFormat, config.targetFormat)
        if (convertedLyrics == lyrics) {
            throw BatchTaskSkippedException("Converted lyrics are unchanged")
        }

        val result = patchSongTagsUseCase(
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
        targetFormat: LyricFormat
    ): String {
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
            removeEmptyLines = true,
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
