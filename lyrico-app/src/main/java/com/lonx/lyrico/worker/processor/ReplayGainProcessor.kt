package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.domain.song.usecase.EditSongTagsResult
import com.lonx.lyrico.domain.song.usecase.PatchSongTagsUseCase
import com.lonx.lyrico.utils.ReplayGainCalculateState
import com.lonx.lyrico.utils.ReplayGainScanner

class ReplayGainProcessor(
    private val songLibraryRepository: SongLibraryRepository,
    private val patchSongTagsUseCase: PatchSongTagsUseCase,
    private val replayGainScanner: ReplayGainScanner
) : BatchTaskProcessor {

    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val song = songLibraryRepository.getSongByUri(item.songUri)
            ?: throw BatchTaskSkippedException("Song not found")

        val hasExisting = !song.replayGainTrackGain.isNullOrBlank() ||
                !song.replayGainTrackPeak.isNullOrBlank() ||
                !song.replayGainAlbumGain.isNullOrBlank() ||
                !song.replayGainAlbumPeak.isNullOrBlank() ||
                !song.replayGainReferenceLoudness.isNullOrBlank()
        if (hasExisting) {
            throw BatchTaskSkippedException("ReplayGain already exists")
        }

        var analysisSuccess = false
        var analysisResult: com.lonx.lyrico.utils.ReplayGainAnalysis? = null

        replayGainScanner.analyze(item.songUri).collect { state ->
            when (state) {
                is ReplayGainCalculateState.Success -> {
                    analysisResult = state.analysis
                    analysisSuccess = true
                }
                is ReplayGainCalculateState.Cancelled,
                is ReplayGainCalculateState.Failed -> {
                    analysisSuccess = false
                }
                is ReplayGainCalculateState.Progress -> {
                    onProgress(state.percent)
                }
            }
        }

        if (!analysisSuccess || analysisResult == null) {
            throw Exception("ReplayGain analysis failed")
        }

        val tagData = AudioTagData(
            replayGainTrackGain = replayGainScanner.formatGain(analysisResult),
            replayGainTrackPeak = replayGainScanner.formatPeak(analysisResult.peak),
            replayGainReferenceLoudness = "-18 LUFS"
        )

        val result = patchSongTagsUseCase(item.songUri, tagData)
        if (result !is EditSongTagsResult.Success) {
            throw Exception("Write failed")
        }

        return BatchTaskProcessResult()
    }
}
