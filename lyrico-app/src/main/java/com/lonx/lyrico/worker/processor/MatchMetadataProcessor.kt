package com.lonx.lyrico.worker.processor

import android.util.Log
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.ScoredSearchResult
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import com.lonx.lyrico.data.model.metadata.MetadataApplyPolicy
import com.lonx.lyrico.data.model.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.model.plugin.PluginFieldProcessConfig
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.MetadataWriteMode
import com.lonx.lyrico.data.model.metadata.SearchResultApplier
import com.lonx.lyrico.data.model.plugin.defaultPluginFieldProcessConfig
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.domain.song.usecase.PatchSongTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.MatchScoreDetail
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrico.utils.PluginFieldPostProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MatchMetadataProcessor(
    private val audioTagRepository: AudioTagRepository,
    private val patchSongTagsUseCase: PatchSongTagsUseCase,
    private val songLibraryRepository: SongLibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val searchSourceProvider: SearchSourceProvider
) : BatchTaskProcessor {
    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val config = task.configJson?.let {
            Json.decodeFromString<MatchMetadataTaskConfig>(it)
        } ?: throw BatchTaskSkippedException("No config")

        val matchConfig = config.matchConfig
        val sources = searchSourceProvider.getAllSources()

        sources.forEach { source ->
            val values = config.sourceSettings[source.id].orEmpty()
            source.applyConfig(SourceRuntimeConfig(values))
        }

        val song = songLibraryRepository.getSongByUri(item.songUri)
            ?: throw BatchTaskSkippedException("Song not found")

        val plan = buildPlan(
            matchConfig = matchConfig
        )

        if (!plan.requiresSearch) {
            throw BatchTaskSkippedException("No fields need processing")
        }

        onProgress(0.05f)

        val separator = config.separator
        val currentTag = audioTagRepository.read(song.uri)

        val shouldWriteLyrics = when (plan.targetModes[MetadataFieldTarget.LYRICS]) {
            MetadataWriteMode.OVERWRITE -> true
            MetadataWriteMode.SUPPLEMENT -> currentTag.lyrics.isNullOrBlank()
            MetadataWriteMode.DISABLED,
            null -> false
        }

        val lyricConfig = if (shouldWriteLyrics) {
            config.lyricRenderConfig ?: settingsRepository.getLyricRenderConfig()
        } else {
            null
        }

        val fieldProcessor = PluginFieldPostProcessor(
            GlobalFieldProcessSettings(
                scriptConversion = lyricConfig?.conversionMode
                    ?: settingsRepository.conversionMode.first(),
                removeEmptyLines = lyricConfig?.removeEmptyLines
                    ?: settingsRepository.removeEmptyLines.first()
            )
        )

        val enabledSourceOrder = config.enabledSourceOrderIds
        val queries = MusicMatchUtils.buildSearchQueries(
            song = song,
            preferFileName = matchConfig.preferFileName
        )

        val orderedSources = sources
            .filter { source ->
                enabledSourceOrder.isEmpty() || source.id in enabledSourceOrder
            }
            .sortedBy { source ->
                enabledSourceOrder.indexOf(source.id).let { index ->
                    if (index == -1) Int.MAX_VALUE else index
                }
            }

        var bestMatch: ScoredSearchResult? = null
        var bestMatchDetail: MatchScoreDetail? = null
        val allScoredResults = mutableListOf<ScoredSearchResult>()

        searchLoop@ for ((queryIndex, query) in queries.withIndex()) {
            var queryBest: ScoredSearchResult? = null
            var queryBestDetail: MatchScoreDetail? = null

            for ((sourceIndex, source) in orderedSources.withIndex()) {
                val sourceResults = try {
                    source.searchSongs(
                        keyword = query,
                        separator = separator,
                        pageSize = 2
                    ).mapIndexed { index, result ->
                        val detail = MusicMatchUtils.calculateMatchScoreDetail(
                            result = result,
                            song = song,
                            preferFileName = matchConfig.preferFileName,
                            rankIndex = index
                        )

                        ScoredSearchResult(
                            result = result,
                            score = detail.finalScore,
                            source = source
                        ) to detail
                    }
                } catch (throwable: Exception) {
                    Log.w(
                        TAG,
                        "Search source failed. songUri=${song.uri}, source=${source.id}, query=$query",
                        throwable
                    )
                    emptyList()
                }

                Log.d(
                    TAG,
                    "Search source results. songUri=${song.uri}, source=${source.id}, " +
                            "query=$query, count=${sourceResults.size}, " +
                            "top=${sourceResults.take(3).map { (scored, detail) ->
                                "${scored.result.id}:${scored.result.title}|score=${detail.finalScore}" +
                                        "|text=${detail.textScore}|keys=${scored.result.normalizedFields().keys}" +
                                        "|commentBlank=${scored.result.normalizedFields()["comment"].isNullOrBlank()}"
                            }}"
                )

                allScoredResults += sourceResults.map { (scoredResult, _) ->
                    scoredResult
                }

                val sourceBest = sourceResults.maxByOrNull { (_, detail) ->
                    detail.finalScore
                }

                if (sourceBest != null) {
                    val currentScoredResult = sourceBest.first
                    val currentDetail = sourceBest.second

                    if (
                        queryBest == null ||
                        currentDetail.finalScore > (queryBestDetail?.finalScore ?: 0.0)
                    ) {
                        queryBest = currentScoredResult
                        queryBestDetail = currentDetail
                    }

                    if (
                        bestMatch == null ||
                        currentDetail.finalScore > (bestMatchDetail?.finalScore ?: 0.0)
                    ) {
                        bestMatch = currentScoredResult
                        bestMatchDetail = currentDetail
                    }

                    if (
                        currentDetail.finalScore >= 0.92 &&
                        currentDetail.textScore >= 0.86
                    ) {
                        bestMatch = currentScoredResult
                        bestMatchDetail = currentDetail
                        break@searchLoop
                    }
                }

                val sourceCount = orderedSources.size.coerceAtLeast(1)
                val totalSteps = queries.size.coerceAtLeast(1) * sourceCount
                val currentStep = queryIndex * sourceCount + sourceIndex + 1
                onProgress(0.05f + 0.45f * currentStep / totalSteps.toFloat())
            }

            if (
                queryBest != null &&
                queryBestDetail != null &&
                (
                        bestMatch == null ||
                                queryBestDetail.finalScore > (bestMatchDetail?.finalScore ?: 0.0)
                        )
            ) {
                bestMatch = queryBest
                bestMatchDetail = queryBestDetail
            }
        }

        val finalMatch = bestMatch ?: throw BatchTaskSkippedException("No match found")
        val finalDetail = bestMatchDetail ?: throw BatchTaskSkippedException("No match detail found")

        Log.d(
            TAG,
            "Selected match. songUri=${song.uri}, source=${finalMatch.source?.id}, " +
                    "result=${finalMatch.result.id}:${finalMatch.result.title}, " +
                    "score=${finalDetail.finalScore}, textScore=${finalDetail.textScore}, " +
                    "normalizedKeys=${finalMatch.result.normalizedFields().keys}, " +
                    "commentBlank=${finalMatch.result.normalizedFields()["comment"].isNullOrBlank()}, " +
                    "targetModes=${plan.targetModes}"
        )

        if (finalDetail.finalScore < 0.76 || finalDetail.textScore < 0.72) {
            throw BatchTaskSkippedException(
                "Match score too low: final=${finalDetail.finalScore}, text=${finalDetail.textScore}"
            )
        }

        onProgress(0.55f)

        val newLyrics = if (shouldWriteLyrics && lyricConfig != null) {
            try {
                coroutineScope {
                    val deferred = async(Dispatchers.Default) {
                        finalMatch.source?.getLyrics(finalMatch.result)?.let { result ->
                            val sourceId = finalMatch.source.id
                            val processConfig = config.pluginFieldProcessConfigs[sourceId]
                                ?: defaultPluginFieldProcessConfig(sourceId)

                            val processed = fieldProcessor.processLyrics(
                                lyrics = result,
                                config = processConfig
                            )

                            LyricEncoder.encode(
                                result = processed,
                                config = lyricConfig.copy(
                                    conversionMode = com.lonx.lyrico.data.model.ConversionMode.NONE
                                )
                            )
                        }
                    }
                    deferred.await()
                }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        onProgress(0.75f)

        val sourceId = finalMatch.source?.id.orEmpty()
        val processConfig = config.pluginFieldProcessConfigs[sourceId]
            ?: defaultPluginFieldProcessConfig(sourceId)
        val candidateFields = fieldProcessor.processFields(
            pluginId = sourceId,
            fields = finalMatch.result.normalizedFields() +
                    newLyrics?.takeIf { it.isNotBlank() }?.let { mapOf("lyrics" to it) }.orEmpty(),
            config = processConfig,
            fieldDefinitions = emptyList(),
            writeRules = emptyList()
        )

        val tagDataToWrite = SearchResultApplier.buildPatch(
            current = currentTag,
            fields = candidateFields,
            policy = MetadataApplyPolicy(plan.targetModes)
        )

        if (tagDataToWrite.isEmpty()) {
            Log.w(
                TAG,
                "Skipping match metadata with empty patch. " +
                        "songUri=${song.uri}, source=$sourceId, " +
                        "targetModes=${plan.targetModes}, " +
                        "normalizedKeys=${finalMatch.result.normalizedFields().keys}, " +
                        "candidateKeys=${candidateFields.keys}, " +
                        "candidateCommentBlank=${candidateFields["comment"].isNullOrBlank()}, " +
                        "currentCommentBlank=${currentTag.comment.isNullOrBlank()}, " +
                        "databaseCommentBlank=${song.comment.isNullOrBlank()}, " +
                        "score=${finalDetail.finalScore}, textScore=${finalDetail.textScore}"
            )
            throw BatchTaskSkippedException("No fields to update")
        }

        onProgress(0.9f)

        val result = patchSongTagsUseCase(song.uri, tagDataToWrite)
        if (result !is SaveAudioTagsResult.Success) {
            throw Exception("Write failed")
        }

        onProgress(1f)

        return BatchTaskProcessResult()
    }

    private fun buildPlan(matchConfig: BatchMatchConfig): MatchMetadataPlan {
        val enabledTargetModes = matchConfig.targetModes
            .filterValues { mode ->
                mode != MetadataWriteMode.DISABLED
            }

        return MatchMetadataPlan(
            targetModes = enabledTargetModes
        )
    }
    private fun AudioTagData.isEmpty(): Boolean {
        return title == null &&
                artist == null &&
                album == null &&
                genre == null &&
                albumArtist == null &&
                date == null &&
                trackNumber == null &&
                discNumber == null &&
                composer == null &&
                lyricist == null &&
                lyrics == null &&
                picUrl == null &&
                comment == null &&
                replayGainTrackGain == null &&
                replayGainTrackPeak == null &&
                replayGainAlbumGain == null &&
                replayGainAlbumPeak == null &&
                replayGainReferenceLoudness == null
    }

    companion object {
        private const val TAG = "MatchMetadataProcessor"
    }
}

private data class MatchMetadataPlan(
    val targetModes: Map<MetadataFieldTarget, MetadataWriteMode>
) {
    val requiresSearch: Boolean
        get() = targetModes.isNotEmpty()
}

@Serializable
data class MatchMetadataTaskConfig(
    val matchConfig: BatchMatchConfig,
    val separator: String,
    val enabledSourceOrderIds: List<String>,
    val sourceSettings: Map<String, Map<String, String>> = emptyMap(),
    val pluginFieldProcessConfigs: Map<String, PluginFieldProcessConfig> = emptyMap(),
    val lyricRenderConfig: LyricRenderConfig? = null,
    val concurrency: Int = 3
)
