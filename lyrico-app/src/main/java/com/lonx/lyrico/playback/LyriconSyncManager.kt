package com.lonx.lyrico.playback

import android.content.Context
import android.util.Log
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.utils.LyricDecoder
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsResult
import io.github.proify.lyricon.lyric.model.LyricWord as LyriconWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderMetadata

class LyriconSyncManager(context: Context) {

    private val provider: LyriconProvider = LyriconFactory.createProvider(
        context = context.applicationContext,
        playerPackageName = "com.lonx.lyrico",
        metadata = ProviderMetadata(
            mapOf(
                "lyricon.provider.name" to "Lyrico",
                "lyricon.provider.version" to "1.0.0",
                "lyricon.provider.author" to "Lonx",
                "lyricon.provider.author.url" to "https://lonx.com",
                "lyricon.provider.license" to "Apache-2.0",
                "lyricon.provider.license.url" to "https://www.apache.org/licenses/LICENSE-2.0.txt",
            )
        ),
        )

    private var lastSongKey: String? = null
    private var lastPlaying: Boolean? = null
    private var lastPositionMs: Long? = null

    init {
        provider.autoSync = true
        runCatching {
            provider.player.setPositionUpdateInterval(POSITION_UPDATE_INTERVAL_MS)
            provider.register()
        }.onFailure { error ->
            Log.w(TAG, "Failed to register Lyricon provider", error)
        }
    }

    fun syncState(state: PlaybackState) {
        runCatching {
            val song = state.currentSong
            if (song == null || state.status == PlaybackStatus.IDLE) {
                clear()
                return
            }

            val songKey = song.lyriconKey()
            if (songKey != lastSongKey) {
                provider.player.setSong(song.toLyriconSong())
                lastSongKey = songKey
                lastPositionMs = null
            }

            syncPosition(state.positionMs)
            syncPlaybackState(state.isPlaying)
        }.onFailure { error ->
            Log.w(TAG, "Failed to sync playback state to Lyricon", error)
        }
    }

    fun syncPosition(positionMs: Long) {
        val safePosition = positionMs.coerceAtLeast(0L)
        if (lastPositionMs == safePosition) return

        runCatching {
            provider.player.setPosition(safePosition)
            lastPositionMs = safePosition
        }.onFailure { error ->
            Log.w(TAG, "Failed to sync position to Lyricon", error)
        }
    }

    fun seekTo(positionMs: Long) {
        val safePosition = positionMs.coerceAtLeast(0L)
        runCatching {
            provider.player.seekTo(safePosition)
            lastPositionMs = safePosition
        }.onFailure { error ->
            Log.w(TAG, "Failed to seek Lyricon position", error)
        }
    }

    fun clear() {
        runCatching {
            provider.player.setPlaybackState(false)
            provider.player.sendText(null)
            lastSongKey = null
            lastPlaying = false
            lastPositionMs = null
        }.onFailure { error ->
            Log.w(TAG, "Failed to clear Lyricon state", error)
        }
    }

    fun release() {
        runCatching {
            clear()
            provider.unregister()
            provider.destroy()
        }.onFailure { error ->
            Log.w(TAG, "Failed to release Lyricon provider", error)
        }
    }

    private fun syncPlaybackState(isPlaying: Boolean) {
        if (lastPlaying == isPlaying) return

        provider.player.setPlaybackState(isPlaying)
        lastPlaying = isPlaying
    }

    private fun SongEntity.toLyriconSong(): Song {
        val lyricsResult = lyrics
            ?.takeIf { it.isNotBlank() }
            ?.let { LyricDecoder.decode(it) }

        val lines = lyricsResult?.toLyriconLines().orEmpty()

        provider.player.setDisplayTranslation(lyricsResult?.translated?.isNotEmpty() == true)
        provider.player.setDisplayRoma(lyricsResult?.romanization?.isNotEmpty() == true)

        return Song(
            id = id.takeIf { it > 0 }?.toString() ?: uri.ifBlank { filePath },
            name = title.takeUnless { it.isNullOrBlank() } ?: fileName,
            artist = artist.orEmpty(),
            duration = durationMilliseconds.toLong().coerceAtLeast(0L),
            lyrics = lines
        )
    }

    private fun LyricsResult.toLyriconLines(): List<RichLyricLine> {
        return original.map { line ->
            val translationLine = translated.findMatchingLine(line)
            val romaLine = romanization.findMatchingLine(line)

            RichLyricLine(
                begin = line.start,
                end = line.end,
                text = line.text,
                words = line.words.map { word ->
                    LyriconWord(
                        begin = word.start,
                        end = word.end,
                        text = word.text
                    )
                },
                translation = translationLine?.text.orEmpty(),
                roma = romaLine?.text.orEmpty()
            )
        }
    }

    private fun List<LyricsLine>?.findMatchingLine(line: LyricsLine): LyricsLine? {
        if (this == null) return null
        return minByOrNull { kotlin.math.abs(it.start - line.start) }
            ?.takeIf { kotlin.math.abs(it.start - line.start) <= LINE_MATCH_TOLERANCE_MS }
    }

    private val LyricsLine.text: String
        get() = words.joinToString(separator = "") { it.text }

    private fun SongEntity.lyriconKey(): String {
        return buildString {
            append(id)
            append('|')
            append(uri)
            append('|')
            append(title)
            append('|')
            append(artist)
            append('|')
            append(durationMilliseconds)
            append('|')
            append(lyrics?.hashCode() ?: 0)
        }
    }

    private companion object {
        const val TAG = "LyriconSyncManager"
        const val POSITION_UPDATE_INTERVAL_MS = 250
        const val LINE_MATCH_TOLERANCE_MS = 10L
    }
}
