package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchField
import com.lonx.lyrico.data.model.BatchMatchMode
import com.lonx.lyrico.data.model.ExtraMetadataWriteRule
import com.lonx.lyrico.data.model.ExtraWriteMode
import com.lonx.lyrico.data.model.ScoredSearchResult
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.ExtraMetadataResolver
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrics.model.SearchCapability
import com.lonx.lyrics.model.SearchResultType
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MatchMetadataProcessor(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val sources: List<SearchSource>
) : BatchTaskProcessor {
    private val extraMetadataResolver = ExtraMetadataResolver()

    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val config = task.configJson?.let {
            Json.decodeFromString<MatchMetadataTaskConfig>(it)
        } ?: throw BatchTaskSkippedException("No config")

        val matchConfig = config.matchConfig
        val song = songRepository.getSongByUri(item.songUri)
            ?: throw BatchTaskSkippedException("Song not found")

        val needsProcessing = matchConfig.fields.any { (field, mode) ->
            if (mode == BatchMatchMode.OVERWRITE) return@any true
            when (field) {
                BatchMatchField.TITLE -> song.title.isNullOrBlank()
                BatchMatchField.ARTIST -> song.artist.isNullOrBlank()
                BatchMatchField.ALBUM -> song.album.isNullOrBlank()
                BatchMatchField.GENRE -> song.genre.isNullOrBlank()
                BatchMatchField.DATE -> song.date.isNullOrBlank()
                BatchMatchField.TRACK_NUMBER -> song.trackerNumber.isNullOrBlank()
                BatchMatchField.LYRICS -> song.lyrics.isNullOrBlank()
                BatchMatchField.COVER -> true
            }
        }

        val needsExtraProcessing = config.extraWriteRules.any { it.mode != ExtraWriteMode.DISABLED }
        if (!needsProcessing && !needsExtraProcessing) {
            throw BatchTaskSkippedException("No fields need processing")
        }

        val separator = config.separator
        val lyricConfig = settingsRepository.getLyricRenderConfig()
        val enabledSourceOrder = config.enabledSourceOrderIds.mapNotNull { id ->
            Source.entries.find { it.id == id }
        }

        val (parsedTitle, parsedArtist) = MusicMatchUtils.parseFileName(song.fileName)

        val queryTitle: String?
        val queryArtist: String?
        val queries: List<String>

        if (matchConfig.preferFileName) {
            queryTitle = parsedTitle?.takeIf { it.isNotBlank() }
                ?: song.title?.takeIf { it.isNotBlank() && !it.contains("未知", true) }
            queryArtist = parsedArtist?.takeIf { it.isNotBlank() }
                ?: song.artist?.takeIf { it.isNotBlank() && !it.contains("未知", true) }
            val fileNameQuery = if (!queryTitle.isNullOrBlank() && !queryArtist.isNullOrBlank()) {
                "$queryTitle $queryArtist"
            } else {
                queryTitle ?: queryArtist
            }
            queries = if (!fileNameQuery.isNullOrBlank()) {
                listOf(fileNameQuery)
            } else {
                MusicMatchUtils.buildSearchQueries(song)
            }
        } else {
            queryTitle = song.title?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedTitle
            queryArtist = song.artist?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedArtist
            queries = MusicMatchUtils.buildSearchQueries(song)
        }

        val orderedSources = sources
            .filter { source ->
                source.capabilities.contains(SearchCapability.METADATA) &&
                        (enabledSourceOrder.isEmpty() || source.sourceType in enabledSourceOrder)
            }
            .sortedBy { source ->
                enabledSourceOrder.indexOf(source.sourceType).let { if (it == -1) Int.MAX_VALUE else it }
            }

        var bestMatch: ScoredSearchResult? = null
        val allScoredResults = mutableListOf<ScoredSearchResult>()

        for (query in queries) {
            val searchTasks = orderedSources.map { source ->
                coroutineScope {
                    async(Dispatchers.IO) {
                        try {
                            val results = source.search(query, separator = separator, pageSize = 2)
                            results.filter {
                                it.availableTypes.contains(SearchResultType.METADATA) ||
                                        it.availableTypes.contains(SearchResultType.EXTRA_METADATA)
                            }.map { res ->
                                val score = MusicMatchUtils.calculateMatchScore(res, song, queryTitle, queryArtist)
                                ScoredSearchResult(res, score, source)
                            }
                        } catch (e: Exception) { emptyList() }
                    }
                }
            }
            val allResults = searchTasks.awaitAll().flatten()
            allScoredResults += allResults
            val currentBest = allResults.maxByOrNull { it.score }
            if (currentBest != null) {
                if (bestMatch == null || currentBest.score > bestMatch.score) {
                    bestMatch = currentBest
                }
                if (currentBest.score > 0.9) break
            }
        }

        val finalMatch = bestMatch ?: throw BatchTaskSkippedException("No match found")
        if (finalMatch.score < 0.35) throw BatchTaskSkippedException("Match score too low")

        val newLyrics = try {
            coroutineScope {
                val deferred = async(Dispatchers.Default) {
                    finalMatch.source?.getLyrics(finalMatch.result)?.let { result ->
                        LyricEncoder.encode(result = result, config = lyricConfig)
                    }
                }
                deferred.await()
            }
        } catch (e: Exception) { null }

        val newTitle = resolveValue(matchConfig, BatchMatchField.TITLE, song.title, finalMatch.result.title)
        val newArtist = resolveValue(matchConfig, BatchMatchField.ARTIST, song.artist, finalMatch.result.artist)
        val newAlbum = resolveValue(matchConfig, BatchMatchField.ALBUM, song.album, finalMatch.result.album)
        val newDate = resolveValue(matchConfig, BatchMatchField.DATE, song.date, finalMatch.result.date)
        val newTrack = resolveValue(matchConfig, BatchMatchField.TRACK_NUMBER, song.trackerNumber, finalMatch.result.trackerNumber)
        val newGenre = resolveValue(matchConfig, BatchMatchField.GENRE, song.genre, null)
        val newLyricsResolved = resolveValue(matchConfig, BatchMatchField.LYRICS, song.lyrics, newLyrics)

        val shouldUpdateCover = shouldUpdate(matchConfig, BatchMatchField.COVER, null)
        val picUrl = if (shouldUpdateCover) finalMatch.result.picUrl else null

        val standardTagData = AudioTagData(
            title = newTitle,
            artist = newArtist,
            album = newAlbum,
            genre = newGenre,
            date = newDate,
            trackNumber = newTrack,
            lyrics = newLyricsResolved,
            picUrl = picUrl
        )
        val extraTagData = extraMetadataResolver.resolve(
            currentSong = song,
            scoredResults = allScoredResults,
            rules = config.extraWriteRules
        )
        val tagDataToWrite = extraMetadataResolver.mergeNonNull(standardTagData, extraTagData)

        val isEffectivelyEmpty = newTitle == null && newArtist == null && newAlbum == null &&
                newGenre == null && newDate == null && newTrack == null &&
                newLyricsResolved == null && picUrl == null && extraTagData.isEmpty()

        if (isEffectivelyEmpty) {
            throw BatchTaskSkippedException("No fields to update")
        }

        val success = songRepository.patchAudioTags(song.uri, tagDataToWrite)
        if (!success) {
            throw Exception("Write failed")
        }

        return BatchTaskProcessResult()
    }

    private fun resolveValue(
        config: BatchMatchConfig,
        field: BatchMatchField,
        currentValue: String?,
        newValue: String?
    ): String? {
        if (!config.fields.containsKey(field)) return null
        val mode = config.fields[field]!!
        return if (mode == BatchMatchMode.OVERWRITE) {
            newValue
        } else {
            if (currentValue.isNullOrBlank()) newValue else null
        }
    }

    private fun shouldUpdate(
        config: BatchMatchConfig,
        field: BatchMatchField,
        currentValue: String?
    ): Boolean {
        if (!config.fields.containsKey(field)) return false
        val mode = config.fields[field]!!
        if (mode == BatchMatchMode.OVERWRITE) return true
        return currentValue.isNullOrBlank()
    }

    private fun AudioTagData.isEmpty(): Boolean {
        return title == null && artist == null && album == null && genre == null &&
                date == null && trackNumber == null && lyrics == null && picUrl == null &&
                comment == null && replayGainTrackGain == null && replayGainTrackPeak == null &&
                replayGainAlbumGain == null && replayGainAlbumPeak == null &&
                replayGainReferenceLoudness == null
    }
}

@Serializable
data class MatchMetadataTaskConfig(
    val matchConfig: BatchMatchConfig,
    val separator: String,
    val enabledSourceOrderIds: List<String>,
    val extraWriteRules: List<ExtraMetadataWriteRule> = emptyList(),
    val concurrency: Int = 3
)
