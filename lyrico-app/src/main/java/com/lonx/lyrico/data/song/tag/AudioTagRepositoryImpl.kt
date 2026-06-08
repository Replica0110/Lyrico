package com.lonx.lyrico.data.song.tag

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.net.toUri
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.audiotag.rw.AudioTagWriter
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.song.file.AudioFileAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioTagRepositoryImpl(
    private val context: Context,
    private val fileAccess: AudioFileAccess,
    private val mutationResolver: AudioTagMutationResolver,
    private val appLogRepository: AppLogRepository
) : AudioTagRepository {

    override suspend fun read(uri: String): AudioTagData = readLenient(uri)

    private suspend fun readLenient(uri: String): AudioTagData = withContext(Dispatchers.IO) {
        val displayName = fileAccess.getDisplayName(uri)
        try {
            readFromUri(uri, displayName, strict = false)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to read audio tags: $uri", e)
            logMetadataException("Failed to read audio tags", e, uri)
            AudioTagData(fileName = displayName)
        }
    }

    private suspend fun readStrict(uri: String): AudioTagData = withContext(Dispatchers.IO) {
        val displayName = fileAccess.getDisplayName(uri)
        readFromUri(uri, displayName, strict = true)
    }

    override suspend fun overwrite(
        uri: String,
        mutation: AudioTagMutation
    ): AudioTagWriteResult {
        return write(uri, mutation.copy(mode = AudioTagMutationMode.Overwrite))
    }

    override suspend fun patch(
        uri: String,
        mutation: AudioTagMutation
    ): AudioTagWriteResult {
        return write(uri, mutation.copy(mode = AudioTagMutationMode.Patch))
    }

    private suspend fun write(uri: String, mutation: AudioTagMutation): AudioTagWriteResult {
        return try {
            val current = readStrict(uri)
            val resolved = mutationResolver.resolve(uri, current, mutation)

            val writableDescriptor = fileAccess.openWritableDescriptor(uri)
                ?: return fileAccess.createWritePermissionIntentSender(uri)?.let { intentSender ->
                    AudioTagWriteResult.PermissionRequired(intentSender)
                } ?: AudioTagWriteResult.Failed(
                    IllegalStateException("Unable to open writable descriptor")
                )

            writableDescriptor.use { descriptor ->
                if (resolved.tags.isNotEmpty()) {
                    val tagsWritten = AudioTagWriter.writeTags(
                        pfd = descriptor,
                        updates = resolved.tags,
                        preserveOldTags = true
                    )
                    if (!tagsWritten) {
                        return AudioTagWriteResult.Failed(IllegalStateException("Audio tag write failed"))
                    }
                }

                when (val pictureCommand = resolved.pictures) {
                    PictureWriteCommand.Unchanged -> Unit
                    is PictureWriteCommand.ReplaceAll -> {
                        val picturesWritten = AudioTagWriter.writePictures(
                            pfd = descriptor,
                            pictures = pictureCommand.pictures
                        )
                        if (!picturesWritten) {
                            return AudioTagWriteResult.Failed(
                                IllegalStateException("Audio picture write failed")
                            )
                        }
                    }
                }
            }

            AudioTagWriteResult.Success(readStrict(uri))
        } catch (e: Exception) {
            fileAccess.writePermissionFromThrowable(uri, e)?.let { intentSender ->
                return AudioTagWriteResult.PermissionRequired(intentSender)
            }
            Log.e(TAG, "Failed to write audio tags: $uri", e)
            logMetadataException("Failed to write audio tags", e, uri)
            AudioTagWriteResult.Failed(e)
        }
    }

    private suspend fun readFromUri(
        uri: String,
        displayName: String,
        strict: Boolean
    ): AudioTagData {
        fileAccess.openReadableDescriptor(uri)?.use { descriptor ->
            return AudioTagReader.read(descriptor, readPictures = true).copy(fileName = displayName)
        }
        return readFromStreamCache(uri, displayName, strict)
    }

    private suspend fun readFromStreamCache(
        uriString: String,
        displayName: String,
        strict: Boolean
    ): AudioTagData {
        val uri = uriString.toUri()
        if (uri.scheme != "content") {
            if (strict) throw IllegalStateException("Unable to open readable descriptor: $uriString")
            return AudioTagData(fileName = displayName)
        }

        val cacheDir = File(context.cacheDir, "external-audio-read-cache").apply { mkdirs() }
        val tempFile = withContext(Dispatchers.IO) {
            File.createTempFile("audio-", ".tmp", cacheDir)
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                if (strict) throw IllegalStateException("Unable to open input stream: $uriString")
                return AudioTagData(fileName = displayName)
            }

            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                return AudioTagReader.read(descriptor, readPictures = true).copy(fileName = displayName)
            }
        } finally {
            if (!tempFile.delete()) {
                Log.w(TAG, "Unable to delete temp audio cache: ${tempFile.absolutePath}")
            }
        }
    }

    private suspend fun logMetadataException(
        message: String,
        throwable: Throwable,
        relatedId: String? = null
    ) {
        try {
            appLogRepository.logException(
                type = AppLogType.METADATA,
                tag = TAG,
                message = message,
                throwable = throwable,
                relatedId = relatedId
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write metadata exception log", e)
        }
    }

    private companion object {
        const val TAG = "AudioTagRepository"
    }
}
