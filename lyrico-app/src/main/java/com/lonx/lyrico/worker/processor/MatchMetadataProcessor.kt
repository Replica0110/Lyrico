package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchField
import com.lonx.lyrico.data.model.BatchMatchMode
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldWriteRule
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldWriteRuleFactory
import com.lonx.lyrico.data.model.ScoredSearchResult
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.model.plugin.PluginFieldProcessConfig
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldTarget
import com.lonx.lyrico.data.model.plugin.PluginMetadataWriteMode
import com.lonx.lyrico.data.model.plugin.defaultPluginFieldProcessConfig
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.domain.song.usecase.EditSongTagsResult
import com.lonx.lyrico.domain.song.usecase.PatchSongTagsUseCase
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.MetadataFieldResolver
import com.lonx.lyrico.utils.MatchScoreDetail
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrico.utils.PluginFieldPostProcessor
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
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
    private val metadataFieldResolver = MetadataFieldResolver()

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

        val plan = buildPlan(matchConfig, config.metadataFieldWriteRules, song, sources)
        if (!plan.requiresSearch) {
            throw BatchTaskSkippedException("No fields need processing")
        }
        onProgress(0.05f)

        val separator = config.separator
        val lyricConfig = if (plan.shouldFetchLyrics) {
            config.lyricRenderConfig ?: settingsRepository.getLyricRenderConfig()
        } else {
            null
        }
        val fieldProcessor = PluginFieldPostProcessor(
            GlobalFieldProcessSettings(
                scriptConversion = lyricConfig?.conversionMode ?: settingsRepository.conversionMode.first(),
                removeEmptyLines = lyricConfig?.removeEmptyLines ?: settingsRepository.removeEmptyLines.first()
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
                enabledSourceOrder.indexOf(source.id).let { if (it == -1) Int.MAX_VALUE else it }
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
                    ).mapIndexed { index, res ->
                        val detail = MusicMatchUtils.calculateMatchScoreDetail(
                            result = res,
                            song = song,
                            preferFileName = matchConfig.preferFileName,
                            rankIndex = index
                        )

                        ScoredSearchResult(
                            result = res,
                            score = detail.finalScore,
                            source = source
                        ) to detail
                    }
                } catch (e: Exception) {
                    emptyList()
                }

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

                    // 高优先级源已经足够可信，直接采用
                    if (
                        currentDetail.finalScore >= 0.92 &&
                        currentDetail.textScore >= 0.86
                    ) {
                        bestMatch = currentScoredResult
                        bestMatchDetail = currentDetail
                        break@searchLoop
                    }
                }

                val totalSteps = queries.size.coerceAtLeast(1) * orderedSources.size.coerceAtLeast(1)
                val currentStep = queryIndex * orderedSources.size.coerceAtLeast(1) + sourceIndex + 1
                onProgress(0.05f + 0.45f * currentStep / totalSteps.toFloat())
            }

            // 当前 query 搜完后，把当前 query 的最佳结果同步到全局最佳
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

        if (finalDetail.finalScore < 0.76 || finalDetail.textScore < 0.72) {
            throw BatchTaskSkippedException(
                "Match score too low: final=${finalDetail.finalScore}, text=${finalDetail.textScore}"
            )
        }
        onProgress(0.55f)

        val newLyrics = if (plan.shouldFetchLyrics && lyricConfig != null) try {
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
                            config = lyricConfig.copy(conversionMode = com.lonx.lyrico.data.model.ConversionMode.NONE)
                        )
                    }
                }
                deferred.await()
            }
        } catch (e: Exception) {
            null
        } else {
            null
        }
        onProgress(0.75f)
        val processedFields = finalMatch.source?.let { source ->
            val processConfig = config.pluginFieldProcessConfigs[source.id]
                ?: defaultPluginFieldProcessConfig(source.id)
            fieldProcessor.processFields(
                pluginId = source.id,
                fields = finalMatch.result.normalizedFields(),
                config = processConfig,
                fieldDefinitions = source.metadataFields,
                writeRules = plan.metadataRules
            )
        }.orEmpty()
        val newTitle = resolveValue(plan, BatchMatchField.TITLE, processedFields["title"] ?: finalMatch.result.title)
        val newArtist = resolveValue(plan, BatchMatchField.ARTIST, processedFields["artist"] ?: finalMatch.result.artist)
        val newAlbum = resolveValue(plan, BatchMatchField.ALBUM, processedFields["album"] ?: finalMatch.result.album)
        val newDate = resolveValue(plan, BatchMatchField.DATE, processedFields["date"] ?: finalMatch.result.date)
        val newTrack = resolveValue(plan, BatchMatchField.TRACK_NUMBER, processedFields["track_number"] ?: finalMatch.result.trackNumber)
        val newGenre = resolveValue(plan, BatchMatchField.GENRE, null)
        val newLyricsResolved = resolveValue(plan, BatchMatchField.LYRICS, newLyrics)
        val newComment = resolveValue(plan, BatchMatchField.COMMENT,
            processedFields["subtitle"] ?: finalMatch.result.normalizedFields()["subtitle"]
        )
        val picUrl = if (plan.shouldUpdateCover) finalMatch.result.picUrl else null

        val standardTagData = AudioTagData(
            title = newTitle,
            artist = newArtist,
            album = newAlbum,
            genre = newGenre,
            date = newDate,
            trackNumber = newTrack,
            lyrics = newLyricsResolved,
            picUrl = picUrl,
            comment = newComment,
        )
        val metadataTagData = metadataFieldResolver.resolve(
            currentSong = song,
            scoredResults = allScoredResults.map { scoredResult ->
                val source = scoredResult.source ?: return@map scoredResult
                val processConfig = config.pluginFieldProcessConfigs[source.id]
                    ?: defaultPluginFieldProcessConfig(source.id)
                scoredResult.copy(
                    result = scoredResult.result.copy(
                        fields = fieldProcessor.processFields(
                            pluginId = source.id,
                            fields = scoredResult.result.normalizedFields(),
                            config = processConfig,
                            fieldDefinitions = source.metadataFields,
                            writeRules = plan.metadataRules
                        )
                    )
                )
            },
            rules = plan.metadataRules
        )
        val tagDataToWrite = metadataFieldResolver.mergeNonNull(standardTagData, metadataTagData)

        val isEffectivelyEmpty = newTitle == null && newArtist == null && newAlbum == null &&
                newGenre == null && newDate == null && newTrack == null &&
                newLyricsResolved == null && picUrl == null && newComment == null && metadataTagData.isEmpty()

        if (isEffectivelyEmpty) {
            throw BatchTaskSkippedException("No fields to update")
        }

        onProgress(0.9f)
        val result = patchSongTagsUseCase(song.uri, tagDataToWrite)
        if (result !is EditSongTagsResult.Success) {
            throw Exception("Write failed")
        }
        onProgress(1f)

        return BatchTaskProcessResult()
    }

    private suspend fun buildPlan(
        matchConfig: BatchMatchConfig,
        metadataRules: List<PluginMetadataFieldWriteRule>,
        song: SongEntity,
        sources: List<SearchSource>
    ): MatchMetadataPlan {
        val standardFields = matchConfig.fields.mapNotNull { (field, mode) ->
            if (shouldUpdateField(field, mode, song)) field else null
        }.toSet()
        val applicableMetadataRules = PluginMetadataFieldWriteRuleFactory.mergeWithDeclaredFields(metadataRules, sources)
            .filter { shouldApplyMetadataRule(it, song) }

        return MatchMetadataPlan(
            standardFields = standardFields,
            metadataRules = applicableMetadataRules
        )
    }

    private suspend fun shouldUpdateField(
        field: BatchMatchField,
        mode: BatchMatchMode,
        song: SongEntity
    ): Boolean {
        if (mode == BatchMatchMode.OVERWRITE) return true
        return when (field) {
            BatchMatchField.TITLE -> song.title.isNullOrBlank()
            BatchMatchField.ARTIST -> song.artist.isNullOrBlank()
            BatchMatchField.ALBUM -> song.album.isNullOrBlank()
            BatchMatchField.GENRE -> song.genre.isNullOrBlank()
            BatchMatchField.DATE -> song.date.isNullOrBlank()
            BatchMatchField.TRACK_NUMBER -> song.trackerNumber.isNullOrBlank()
            BatchMatchField.LYRICS -> song.lyrics.isNullOrBlank()
            BatchMatchField.COMMENT -> song.comment.isNullOrBlank()
            BatchMatchField.COVER -> !hasEmbeddedCover(song)
        }
    }

    private suspend fun hasEmbeddedCover(song: SongEntity): Boolean {
        return runCatching {
            audioTagRepository.read(song.uri).pictures.isNotEmpty()
        }.getOrDefault(false)
    }

    private suspend fun shouldApplyMetadataRule(
        rule: PluginMetadataFieldWriteRule,
        song: SongEntity
    ): Boolean {
        if (rule.mode == PluginMetadataWriteMode.DISABLED) return false
        if (rule.mode == PluginMetadataWriteMode.OVERWRITE) return true

        return when (rule.target) {
            PluginMetadataFieldTarget.TITLE -> song.title.isNullOrBlank()
            PluginMetadataFieldTarget.ARTIST -> song.artist.isNullOrBlank()
            PluginMetadataFieldTarget.ALBUM -> song.album.isNullOrBlank()
            PluginMetadataFieldTarget.ALBUM_ARTIST -> song.albumArtist.isNullOrBlank()
            PluginMetadataFieldTarget.GENRE -> song.genre.isNullOrBlank()
            PluginMetadataFieldTarget.DATE -> song.date.isNullOrBlank()
            PluginMetadataFieldTarget.TRACK_NUMBER -> song.trackerNumber.isNullOrBlank()
            PluginMetadataFieldTarget.DISC_NUMBER -> song.discNumber == null
            PluginMetadataFieldTarget.COMPOSER -> song.composer.isNullOrBlank()
            PluginMetadataFieldTarget.LYRICIST -> song.lyricist.isNullOrBlank()
            PluginMetadataFieldTarget.COMMENT -> song.comment.isNullOrBlank()
            PluginMetadataFieldTarget.LYRICS -> song.lyrics.isNullOrBlank()
            PluginMetadataFieldTarget.COVER -> !hasEmbeddedCover(song)
            PluginMetadataFieldTarget.LANGUAGE -> true
            PluginMetadataFieldTarget.COPYRIGHT -> true
            PluginMetadataFieldTarget.RATING -> true
            PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN -> song.replayGainTrackGain.isNullOrBlank()
            PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK -> song.replayGainTrackPeak.isNullOrBlank()
            PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN -> true
            PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK -> true
            PluginMetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS -> song.replayGainReferenceLoudness.isNullOrBlank()
            PluginMetadataFieldTarget.CUSTOM -> true
        }
    }

    private fun resolveValue(
        plan: MatchMetadataPlan,
        field: BatchMatchField,
        newValue: String?
    ): String? {
        return if (field in plan.standardFields) newValue else null
    }

    private fun AudioTagData.isEmpty(): Boolean {
        return title == null && artist == null && album == null && genre == null &&
                albumArtist == null && date == null && trackNumber == null &&
                discNumber == null && composer == null && lyricist == null &&
                lyrics == null && picUrl == null && comment == null &&
                replayGainTrackGain == null && replayGainTrackPeak == null &&
                replayGainAlbumGain == null && replayGainAlbumPeak == null &&
                replayGainReferenceLoudness == null
    }

}

private data class MatchMetadataPlan(
    val standardFields: Set<BatchMatchField>,
    val metadataRules: List<PluginMetadataFieldWriteRule>
) {
    val requiresSearch: Boolean
        get() = standardFields.isNotEmpty() || metadataRules.isNotEmpty()

    val shouldFetchLyrics: Boolean
        get() = BatchMatchField.LYRICS in standardFields

    val shouldUpdateCover: Boolean
        get() = BatchMatchField.COVER in standardFields
}

@Serializable
data class MatchMetadataTaskConfig(
    val matchConfig: BatchMatchConfig,
    val separator: String,
    val enabledSourceOrderIds: List<String>,
    val metadataFieldWriteRules: List<PluginMetadataFieldWriteRule> = emptyList(),
    val sourceSettings: Map<String, Map<String, String>> = emptyMap(),
    val pluginFieldProcessConfigs: Map<String, PluginFieldProcessConfig> = emptyMap(),
    val lyricRenderConfig: LyricRenderConfig? = null,
    val concurrency: Int = 3
)
