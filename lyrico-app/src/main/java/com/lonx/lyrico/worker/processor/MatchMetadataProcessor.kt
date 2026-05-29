package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.ScoredSearchResult
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import com.lonx.lyrico.data.model.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.model.plugin.PluginFieldProcessConfig
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldTarget
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldWriteRule
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldWriteRuleFactory
import com.lonx.lyrico.data.model.plugin.PluginMetadataWriteMode
import com.lonx.lyrico.data.model.plugin.defaultPluginFieldProcessConfig
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.domain.song.usecase.PatchSongTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.MatchScoreDetail
import com.lonx.lyrico.utils.MetadataFieldResolver
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

        val plan = buildPlan(
            matchConfig = matchConfig,
            metadataRules = config.metadataFieldWriteRules,
            sources = sources
        )

        if (!plan.requiresSearch) {
            throw BatchTaskSkippedException("No fields need processing")
        }

        onProgress(0.05f)

        val separator = config.separator

        val shouldWriteLyrics = when (plan.targetModes[PluginMetadataFieldTarget.LYRICS]) {
            PluginMetadataWriteMode.OVERWRITE -> true
            PluginMetadataWriteMode.SUPPLEMENT -> song.lyrics.isNullOrBlank()
            PluginMetadataWriteMode.DISABLED,
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
                } catch (_: Exception) {
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

        val standardCandidate = buildCandidateData(
            plan = plan,
            finalMatch = finalMatch,
            newLyrics = newLyrics
        )

        val metadataCandidate = metadataFieldResolver.resolve(
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

        val mergedCandidate = metadataFieldResolver.mergeNonNull(
            metadataCandidate,
            standardCandidate
        )

        val tagDataToWrite = filterByBatchWriteMode(
            candidate = mergedCandidate,
            song = song,
            targetModes = plan.targetModes
        )

        if (tagDataToWrite.isEmpty()) {
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

    private fun buildPlan(
        matchConfig: BatchMatchConfig,
        metadataRules: List<PluginMetadataFieldWriteRule>,
        sources: List<SearchSource>
    ): MatchMetadataPlan {
        val enabledTargetModes = matchConfig.targetModes
            .filterValues { mode ->
                mode != PluginMetadataWriteMode.DISABLED
            }

        val applicableMetadataRules = PluginMetadataFieldWriteRuleFactory
            .mergeWithDeclaredFields(
                savedRules = metadataRules,
                searchSources = sources
            )
            .filter { rule ->
                rule.mode != PluginMetadataWriteMode.DISABLED
            }
            .mapNotNull { rule ->
                val batchMode = enabledTargetModes[rule.target]
                    ?: return@mapNotNull null

                rule.copy(mode = batchMode)
            }

        return MatchMetadataPlan(
            targetModes = enabledTargetModes,
            metadataRules = applicableMetadataRules
        )
    }

    private fun filterByBatchWriteMode(
        candidate: AudioTagData,
        song: SongEntity,
        targetModes: Map<PluginMetadataFieldTarget, PluginMetadataWriteMode>
    ): AudioTagData {
        fun canWrite(target: PluginMetadataFieldTarget): Boolean {
            return when (targetModes[target]) {
                PluginMetadataWriteMode.OVERWRITE -> true
                PluginMetadataWriteMode.SUPPLEMENT -> isTargetBlank(target, song)
                PluginMetadataWriteMode.DISABLED,
                null -> false
            }
        }

        return AudioTagData(
            title = if (canWrite(PluginMetadataFieldTarget.TITLE)) candidate.title else null,
            artist = if (canWrite(PluginMetadataFieldTarget.ARTIST)) candidate.artist else null,
            album = if (canWrite(PluginMetadataFieldTarget.ALBUM)) candidate.album else null,
            albumArtist = if (canWrite(PluginMetadataFieldTarget.ALBUM_ARTIST)) candidate.albumArtist else null,
            genre = if (canWrite(PluginMetadataFieldTarget.GENRE)) candidate.genre else null,
            date = if (canWrite(PluginMetadataFieldTarget.DATE)) candidate.date else null,
            language = if (canWrite(PluginMetadataFieldTarget.LANGUAGE)) candidate.language else null,
            trackNumber = if (canWrite(PluginMetadataFieldTarget.TRACK_NUMBER)) candidate.trackNumber else null,
            discNumber = if (canWrite(PluginMetadataFieldTarget.DISC_NUMBER)) candidate.discNumber else null,
            composer = if (canWrite(PluginMetadataFieldTarget.COMPOSER)) candidate.composer else null,
            lyricist = if (canWrite(PluginMetadataFieldTarget.LYRICIST)) candidate.lyricist else null,
            comment = if (canWrite(PluginMetadataFieldTarget.COMMENT)) candidate.comment else null,
            lyrics = if (canWrite(PluginMetadataFieldTarget.LYRICS)) candidate.lyrics else null,
            copyright = if (canWrite(PluginMetadataFieldTarget.COPYRIGHT)) candidate.copyright else null,
            rating = if (canWrite(PluginMetadataFieldTarget.RATING)) candidate.rating else null,
            replayGainTrackGain = if (canWrite(PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN)) {
                candidate.replayGainTrackGain
            } else {
                null
            },
            replayGainTrackPeak = if (canWrite(PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK)) {
                candidate.replayGainTrackPeak
            } else {
                null
            },
            replayGainAlbumGain = if (canWrite(PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN)) {
                candidate.replayGainAlbumGain
            } else {
                null
            },
            replayGainAlbumPeak = if (canWrite(PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK)) {
                candidate.replayGainAlbumPeak
            } else {
                null
            },
            replayGainReferenceLoudness = if (canWrite(PluginMetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS)) {
                candidate.replayGainReferenceLoudness
            } else {
                null
            },
            picUrl = if (canWrite(PluginMetadataFieldTarget.COVER)) candidate.picUrl else null,
            customFields = if (canWrite(PluginMetadataFieldTarget.CUSTOM)) {
                candidate.customFields
            } else {
                emptyList()
            }
        )
    }
    private fun isTargetBlank(
        target: PluginMetadataFieldTarget,
        song: SongEntity
    ): Boolean {
        return when (target) {
            PluginMetadataFieldTarget.TITLE ->
                song.title.isNullOrBlank()

            PluginMetadataFieldTarget.ARTIST ->
                song.artist.isNullOrBlank()

            PluginMetadataFieldTarget.ALBUM ->
                song.album.isNullOrBlank()

            PluginMetadataFieldTarget.ALBUM_ARTIST ->
                song.albumArtist.isNullOrBlank()

            PluginMetadataFieldTarget.GENRE ->
                song.genre.isNullOrBlank()

            PluginMetadataFieldTarget.DATE ->
                song.date.isNullOrBlank()

            PluginMetadataFieldTarget.TRACK_NUMBER ->
                song.trackerNumber.isNullOrBlank()

            PluginMetadataFieldTarget.DISC_NUMBER ->
                song.discNumber == null

            PluginMetadataFieldTarget.COMPOSER ->
                song.composer.isNullOrBlank()

            PluginMetadataFieldTarget.LYRICIST ->
                song.lyricist.isNullOrBlank()

            PluginMetadataFieldTarget.COMMENT ->
                song.comment.isNullOrBlank()

            PluginMetadataFieldTarget.LYRICS ->
                song.lyrics.isNullOrBlank()

            PluginMetadataFieldTarget.LANGUAGE ->
                song.language.isNullOrBlank()

            PluginMetadataFieldTarget.COPYRIGHT ->
                song.copyright.isNullOrBlank()

            PluginMetadataFieldTarget.RATING ->
                song.rating == null

            PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN ->
                song.replayGainTrackGain.isNullOrBlank()

            PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK ->
                song.replayGainTrackPeak.isNullOrBlank()

            PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN ->
                song.replayGainAlbumGain.isNullOrBlank()

            PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK ->
                song.replayGainAlbumPeak.isNullOrBlank()

            PluginMetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS ->
                song.replayGainReferenceLoudness.isNullOrBlank()

            PluginMetadataFieldTarget.COVER ->
                false

            PluginMetadataFieldTarget.CUSTOM ->
                true
        }
    }

    private fun buildCandidateData(
        plan: MatchMetadataPlan,
        finalMatch: ScoredSearchResult,
        newLyrics: String?
    ): AudioTagData {
        fun enabled(target: PluginMetadataFieldTarget): Boolean {
            return plan.targetModes[target] != null &&
                    plan.targetModes[target] != PluginMetadataWriteMode.DISABLED
        }

        return AudioTagData(
            title = if (enabled(PluginMetadataFieldTarget.TITLE)) {
                finalMatch.result.title.takeIf { it.isNotBlank() }
            } else {
                null
            },
            artist = if (enabled(PluginMetadataFieldTarget.ARTIST)) {
                finalMatch.result.artist.takeIf { it.isNotBlank() }
            } else {
                null
            },
            album = if (enabled(PluginMetadataFieldTarget.ALBUM)) {
                finalMatch.result.album.takeIf { it.isNotBlank() }
            } else {
                null
            },
            date = if (enabled(PluginMetadataFieldTarget.DATE)) {
                finalMatch.result.date.takeIf { it.isNotBlank() }
            } else {
                null
            },
            trackNumber = if (enabled(PluginMetadataFieldTarget.TRACK_NUMBER)) {
                finalMatch.result.trackNumber.takeIf { it.isNotBlank() }
            } else {
                null
            },
            lyrics = if (enabled(PluginMetadataFieldTarget.LYRICS)) {
                newLyrics
            } else {
                null
            },
            picUrl = if (enabled(PluginMetadataFieldTarget.COVER)) {
                finalMatch.result.picUrl.takeIf { it.isNotBlank() }
            } else {
                null
            }
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
}

private data class MatchMetadataPlan(
    val targetModes: Map<PluginMetadataFieldTarget, PluginMetadataWriteMode>,
    val metadataRules: List<PluginMetadataFieldWriteRule>
) {
    val requiresSearch: Boolean
        get() = targetModes.isNotEmpty() || metadataRules.isNotEmpty()
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