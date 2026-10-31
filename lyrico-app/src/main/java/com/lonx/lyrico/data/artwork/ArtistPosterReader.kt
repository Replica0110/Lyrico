package com.lonx.lyrico.data.artwork

import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import android.content.Context
import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.utils.ArtistNameSplitter
import com.lonx.lyrico.domain.poster.ArtistPosterGrouping
import com.lonx.lyrico.domain.poster.ResolvedArtistPoster
import com.lonx.lyrico.data.model.artwork.CoverRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Shared source resolution for Coil, poster actions and embedded-poster export. */
class ArtistPosterReader(
    private val context: Context,
    private val tags: AudioTagRepository,
    private val settings: SettingsRepository,
    private val library: LibraryIndexRepository
) {
    suspend fun resolve(request: CoverRequest, strict: Boolean = false): ResolvedArtistPoster? =
        withContext(Dispatchers.IO) {
            val artistName = request.artistName?.takeIf { it.isNotBlank() } ?: return@withContext null
            var readError: Exception? = null
            if (!request.skipEmbeddedPictures) {
                val candidates = request.candidates.takeIf { it.isNotEmpty() }
                    ?.map { it.uri.toString() } ?: listOf(request.uri.toString())
                try {
                    findEmbeddedPoster(artistName, candidates, settings.artistSplitConfigFlow.first())
                        ?.let { return@withContext it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    readError = e
                }
            }
            if (request.artistPosterFolders.isNotEmpty()) {
                try {
                    readExternalArtistPoster(
                        context, artistName, request.artistPosterFolders,
                        library.observeArtists().first().map { it.name }
                    )?.let { return@withContext it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    readError = e
                }
            }
            if (strict && readError != null) throw readError
            null
        }

    suspend fun findEmbeddedPoster(
        artistName: String,
        songUris: List<String>,
        config: ArtistSplitConfig
    ): ResolvedArtistPoster.Embedded? = withContext(Dispatchers.IO) {
        val key = ArtistPosterGrouping.ownerKeyOf(artistName)
        var readError: Exception? = null
        for (uri in songUris.distinct()) {
            coroutineContext.ensureActive()
            if (uri.isBlank() || uri == "0") continue
            try {
                val tag = tags.read(uri, AudioTagReadOptions(strict = true))
                val entries = ArtistPosterGrouping.entries(
                    tag.pictures, ArtistNameSplitter.splitArtists(tag.artist, config)
                )
                for (entry in entries.filter { it.artist != null && it.ownerKey == key }) {
                    if (entry.picture.data.validArtwork() != null) {
                        return@withContext ResolvedArtistPoster.Embedded(artistName, uri, entry.picture)
                    }
                    readError = IllegalStateException("Cannot decode artist artwork: $uri")
                    AppLog.record(AppLogType.METADATA, "ArtistPosterReader", "Embedded poster is undecodable",
                        "uri=$uri\nartist=$artistName\nbytes=${entry.picture.data.size}", relatedId = uri,
                        level = AppLogLevel.WARNING, repeatedFailureKey = "embedded-poster:$uri:$key")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                readError = e
            }
        }
        if (readError != null) throw readError
        null
    }
}
