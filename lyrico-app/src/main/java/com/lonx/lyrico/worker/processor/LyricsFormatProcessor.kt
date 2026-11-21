package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.db.entity.BatchTaskEntity
import com.lonx.lyrico.data.db.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.lyrics.LyricsFormatConfig
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.domain.lyrics.LyricsProcessingResult
import com.lonx.lyrico.domain.lyrics.LyricsProcessor
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsUseCase
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

        val columnEdit = if (config.legacyColumnEdits.isEmpty()) null else {
            config.legacyColumnEdits[item.songUri]
                ?: throw BatchTaskSkippedException("No column mapping for this song")
        }
        val convertedLyrics = when (val processed = LyricsProcessor.process(
            raw = lyrics,
            options = config.processingOptions(),
            requireWordLevelTiming = true,
            columnEdit = columnEdit
        )) {
            is LyricsProcessingResult.Changed -> processed.text
            LyricsProcessingResult.Unchanged -> throw BatchTaskSkippedException("Converted lyrics are unchanged")
            LyricsProcessingResult.Empty -> throw BatchTaskSkippedException("Converted lyrics are empty")
            is LyricsProcessingResult.Unsupported -> throw BatchTaskSkippedException(
                if (processed.reason == LyricsProcessingResult.Reason.SOURCE_CHANGED) {
                    "Lyrics changed since the task was created"
                } else "Cannot process lyrics: ${processed.reason}"
            )
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
