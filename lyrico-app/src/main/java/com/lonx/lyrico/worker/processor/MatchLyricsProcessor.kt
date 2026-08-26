package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.model.lyrics.LyricsCandidateResult
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.MetadataWriteMode
import com.lonx.lyrico.data.model.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.model.plugin.defaultPluginFieldProcessConfig
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.domain.song.usecase.PatchSongTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrico.utils.PluginFieldPostProcessor
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

class MatchLyricsProcessor(
    private val audioTagRepository: AudioTagRepository,
    private val patchSongTagsUseCase: PatchSongTagsUseCase,
    private val songLibraryRepository: SongLibraryRepository,
    private val searchSourceProvider: SearchSourceProvider,
    private val settingsRepository: SettingsRepository
) : BatchTaskProcessor {
    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val config = task.configJson?.let { Json.decodeFromString<MatchMetadataTaskConfig>(it) }
            ?: throw BatchTaskSkippedException("No config")
        val mode = config.matchConfig.targetModes[MetadataFieldTarget.LYRICS]
            ?: MetadataWriteMode.DISABLED
        if (mode == MetadataWriteMode.DISABLED) {
            throw BatchTaskSkippedException("Lyrics matching is disabled")
        }

        val song = songLibraryRepository.getSongByUri(item.songUri)
            ?: throw BatchTaskSkippedException("Song not found")
        val currentTag = audioTagRepository.read(song.uri)
        if (mode == MetadataWriteMode.SUPPLEMENT && !currentTag.lyrics.isNullOrBlank()) {
            throw BatchTaskSkippedException("Lyrics already exist")
        }

        val sources = orderedSources(
            searchSourceProvider.getSources(PluginSourceType.LYRICS),
            config.enabledSourceOrderIds
        )
        if (sources.isEmpty()) throw BatchTaskSkippedException("No enabled lyrics source")
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
        val queries = MusicMatchUtils.buildSearchQueries(song, config.matchConfig.preferFileName)
        var best: ScoredLyricsCandidate? = null

        sources.forEachIndexed { sourceIndex, source ->
            val candidate = try {
                if (PluginCapability.SEARCH_SONGS in source.capabilities) {
                    val searchResults = mutableListOf<SongSearchResult>()
                    for (query in queries) {
                        searchResults += source.searchSongs(
                            keyword = query,
                            separator = config.separator,
                            pageSize = 3
                        )
                    }
                    val songCandidate = searchResults
                        .mapIndexed { index, result ->
                            result to MusicMatchUtils.calculateMatchScore(
                                result = result,
                                song = song,
                                preferFileName = config.matchConfig.preferFileName,
                                rankIndex = index
                            )
                        }
                        .maxByOrNull { it.second }
                    songCandidate?.let { (result, score) ->
                        source.getLyricsCandidates(result).firstOrNull()?.let { loaded ->
                            ScoredLyricsCandidate(loaded, source.id, score)
                        }
                    }
                } else {
                    source.getLyricsCandidates(requestSong, pageSize = 5)
                        .mapIndexed { index, candidate ->
                            ScoredLyricsCandidate(
                                candidate = candidate,
                                sourceId = source.id,
                                score = MusicMatchUtils.calculateMatchScore(
                                    result = candidate.song,
                                    song = song,
                                    preferFileName = config.matchConfig.preferFileName,
                                    rankIndex = index
                                )
                            )
                        }
                        .maxByOrNull { it.score }
                }
            } catch (throwable: Exception) {
                if (throwable is CancellationException) throw throwable
                null
            }
            if (candidate != null && candidate.score > (best?.score ?: Double.NEGATIVE_INFINITY)) {
                best = candidate
            }
            onProgress(0.1f + 0.6f * (sourceIndex + 1) / sources.size)
        }

        val selected = best?.takeIf { it.score >= MIN_MATCH_SCORE }
            ?: throw BatchTaskSkippedException("No reliable lyrics match")
        val renderConfig = config.lyricRenderConfig ?: settingsRepository.getLyricRenderConfig()
        val processor = PluginFieldPostProcessor(
            GlobalFieldProcessSettings(
                scriptConversion = renderConfig.conversionMode,
                removeEmptyLines = renderConfig.removeEmptyLines
            )
        )
        val processed = processor.processLyrics(
            lyrics = selected.candidate.lyrics,
            config = defaultPluginFieldProcessConfig(selected.sourceId)
        )
        val encoded = LyricEncoder.encode(
            processed,
            renderConfig.copy(
                conversionMode = com.lonx.lyrico.data.model.ConversionMode.NONE
            )
        )
        if (encoded.isBlank()) throw BatchTaskSkippedException("Lyrics are empty")

        onProgress(0.85f)
        val result = patchSongTagsUseCase(song.uri, AudioTagData(lyrics = encoded))
        if (result !is SaveAudioTagsResult.Success) throw Exception("Write failed")
        onProgress(1f)
        return BatchTaskProcessResult()
    }

    private fun orderedSources(
        sources: List<com.lonx.lyrico.data.model.lyrics.SearchSource>,
        order: List<String>
    ) = sources
        .filter { order.isEmpty() || it.id in order }
        .sortedBy { source -> order.indexOf(source.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }

    private data class ScoredLyricsCandidate(
        val candidate: LyricsCandidateResult,
        val sourceId: String,
        val score: Double
    )

    private companion object {
        const val MIN_MATCH_SCORE = 0.72
    }
}
