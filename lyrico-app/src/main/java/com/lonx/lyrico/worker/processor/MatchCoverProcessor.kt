package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.MetadataWriteMode
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.domain.song.usecase.PatchSongTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.MusicMatchUtils
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

class MatchCoverProcessor(
    private val audioTagRepository: AudioTagRepository,
    private val patchSongTagsUseCase: PatchSongTagsUseCase,
    private val songLibraryRepository: SongLibraryRepository,
    private val searchSourceProvider: SearchSourceProvider
) : BatchTaskProcessor {
    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val config = task.configJson?.let { Json.decodeFromString<MatchMetadataTaskConfig>(it) }
            ?: throw BatchTaskSkippedException("No config")
        val mode = config.matchConfig.targetModes[MetadataFieldTarget.COVER]
            ?: MetadataWriteMode.DISABLED
        if (mode == MetadataWriteMode.DISABLED) {
            throw BatchTaskSkippedException("Cover matching is disabled")
        }

        val song = songLibraryRepository.getSongByUri(item.songUri)
            ?: throw BatchTaskSkippedException("Song not found")
        val currentTag = audioTagRepository.read(song.uri)
        if (
            mode == MetadataWriteMode.SUPPLEMENT &&
            (!currentTag.picUrl.isNullOrBlank() || currentTag.pictures.isNotEmpty())
        ) {
            throw BatchTaskSkippedException("Cover already exists")
        }

        val order = config.enabledSourceOrderIds
        val sources = searchSourceProvider.getSources(PluginSourceType.COVER)
            .filter { order.isEmpty() || it.id in order }
            .sortedBy { source -> order.indexOf(source.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        if (sources.isEmpty()) throw BatchTaskSkippedException("No enabled cover source")
        sources.forEach { source ->
            source.applyConfig(SourceRuntimeConfig(config.sourceSettings[source.id].orEmpty()))
        }

        val requestSong = SongSearchResult(
            id = song.uri,
            pluginId = "",
            pluginName = "",
            title = song.title.orEmpty(),
            artist = song.artist.orEmpty(),
            album = song.album.orEmpty(),
            duration = song.durationMilliseconds.toLong(),
            date = song.date.orEmpty()
        )
        var best: Pair<SongSearchResult, Double>? = null
        sources.forEachIndexed { sourceIndex, source ->
            try {
                source.searchCovers(requestSong, pageSize = 5)
                    .asSequence()
                    .filter { it.picUrl.isNotBlank() }
                    .mapIndexed { index, result ->
                        result to MusicMatchUtils.calculateMatchScore(
                            result = result,
                            song = song,
                            preferFileName = config.matchConfig.preferFileName,
                            rankIndex = index
                        )
                    }
                    .maxByOrNull { it.second }
                    ?.let { candidate ->
                        if (candidate.second > (best?.second ?: Double.NEGATIVE_INFINITY)) {
                            best = candidate
                        }
                    }
            } catch (throwable: Exception) {
                if (throwable is CancellationException) throw throwable
            }
            onProgress(0.1f + 0.7f * (sourceIndex + 1) / sources.size)
        }

        val selected = best?.takeIf { it.second >= MIN_MATCH_SCORE }?.first
            ?: throw BatchTaskSkippedException("No reliable cover match")
        onProgress(0.9f)
        val result = patchSongTagsUseCase(song.uri, AudioTagData(picUrl = selected.picUrl))
        if (result !is SaveAudioTagsResult.Success) throw Exception("Write failed")
        onProgress(1f)
        return BatchTaskProcessResult()
    }

    private companion object {
        const val MIN_MATCH_SCORE = 0.65
    }
}
