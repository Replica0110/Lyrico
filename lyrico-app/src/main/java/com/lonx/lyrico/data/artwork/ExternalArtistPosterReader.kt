package com.lonx.lyrico.data.artwork

import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.net.toUri
import com.lonx.lyrico.utils.SafDocuments
import com.lonx.lyrico.domain.poster.ResolvedArtistPoster
import com.lonx.lyrico.domain.poster.ArtistPosterMatcher
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

private const val TAG = "ArtistPoster"

internal inline fun <T> readArtworkSafely(diagnosticContext: String = "", block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppLog.record(AppLogType.METADATA, TAG, "Artwork read failed", diagnosticContext,
        level = AppLogLevel.WARNING, error = e,
        repeatedFailureKey = "artwork:$diagnosticContext:${e.javaClass.name}")
    null
}

/**
 * 用户提供的海报文件可能损坏，读不出的文件应跳过，而不是遮住同目录中可用的那张。
 */
internal fun ByteArray.validArtwork(): ByteArray? {
    if (isEmpty()) return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    options.inJustDecodeBounds = false
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 256) {
        options.inSampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size, options) ?: return null
    bitmap.recycle()
    return this
}

/**
 * 按文件夹添加顺序查找艺术家海报；同一文件夹内，精确命名的文件优先于带后缀的变体。
 */
internal suspend fun readExternalArtistPoster(
    context: Context,
    artist: String,
    folders: List<String>,
    libraryArtists: List<String>
): ResolvedArtistPoster.File? {
    if (artist.isBlank() || folders.isEmpty()) return null
    var unreadable = false
    for (folder in folders) {
        coroutineContext.ensureActive()
        // The same SAF query the poster folder screen uses, so both always agree on the files.
        val files = SafDocuments.children(context, folder.toUri())
            ?.filterNot { it.isDirectory }
            ?.filter { ArtistPosterMatcher.ownedBySelection(it.displayName, setOf(artist), libraryArtists) }
        if (files == null) {
            unreadable = true
            continue
        }
        val matches = files
            .mapNotNull { file -> ArtistPosterMatcher.rank(file.displayName, artist)?.let { rank -> rank to file } }
            .sortedWith(compareBy({ it.first }, { it.second.displayName.lowercase() }, { it.second.displayName }))
        for ((_, file) in matches) {
            coroutineContext.ensureActive()
            val bytes = readPosterBytes(context, file.uri, file.displayName)
            if (bytes != null && bytes.isNotEmpty()) {
                return ResolvedArtistPoster.File(artist, folder, file.uri.toString(), file.displayName, bytes)
            }
            unreadable = true
        }
    }
    check(!unreadable) { "Some poster files could not be read" }
    return null
}

internal fun readPosterBytes(context: Context, uri: android.net.Uri, name: String): ByteArray? = readArtworkSafely("uri=$uri\nname=$name") {
    if (name.endsWith(".mp4", ignoreCase = true)) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getScaledFrameAtTime(
                -1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512
            ) ?: run {
                AppLog.record(AppLogType.METADATA, TAG, "Poster video has no decodable frame", "uri=$uri\nname=$name",
                    relatedId = uri.toString(), level = AppLogLevel.WARNING, repeatedFailureKey = "poster-frame:$uri")
                return@readArtworkSafely null
            }
            try {
                ByteArrayOutputStream().use {
                    frame.compress(Bitmap.CompressFormat.PNG, 100, it)
                    it.toByteArray()
                }
            } finally {
                frame.recycle()
            }
        } finally {
            retriever.release()
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().validArtwork() }.also { bytes ->
            if (bytes == null) AppLog.record(AppLogType.METADATA, TAG, "Poster image unavailable or undecodable",
                "uri=$uri\nname=$name", relatedId = uri.toString(), level = AppLogLevel.WARNING,
                repeatedFailureKey = "poster-image:$uri")
        }
    }
}
