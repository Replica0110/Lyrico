package com.lonx.lyrico.data.song.tag

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.net.toUri
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.type
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.audiotag.rw.AudioTagWriter
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.recordOperation
import com.lonx.lyrico.data.model.log.withLogOperation
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

    override suspend fun read(
        uri: String,
        options: AudioTagReadOptions
    ): AudioTagData = if (options.strict) {
        withContext(Dispatchers.IO) {
            readFromUri(uri, fileAccess.getDisplayName(uri), strict = true, options = options)
        }
    } else {
        readLenient(uri, options)
    }

    private suspend fun readLenient(
        uri: String,
        options: AudioTagReadOptions
    ): AudioTagData = withContext(Dispatchers.IO) {
        val displayName = fileAccess.getDisplayName(uri)
        try {
            readFromUri(uri, displayName, strict = false, options = options)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e !is Exception) appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Audio tag library failed",
                "uri=$uri\nname=$displayName\n${e.stackTraceToString()}", uri, AppLogLevel.ERROR)
            Log.e(TAG, "Failed to read audio tags: $uri", e)
            AudioTagData(fileName = displayName)
        }
    }

    private suspend fun readStrict(uri: String): AudioTagData = withContext(Dispatchers.IO) {
        val displayName = fileAccess.getDisplayName(uri)
        readFromUri(uri, displayName, strict = true, options = AudioTagReadOptions())
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

    private suspend fun write(uri: String, mutation: AudioTagMutation): AudioTagWriteResult = withLogOperation("tag-write") {
        val started = System.nanoTime()
        var stage = "read-current-tags"
        val accessTrace = mutableListOf<String>()
        val trace: (String) -> Unit = { if (accessTrace.size < 16) accessTrace += it }
        val context = "uri=$uri\nmode=${mutation.mode}\nfields=${mutation.fields.mapValues { (_, value) -> when (value) {
            FieldMutation.Unchanged -> "unchanged"
            FieldMutation.Clear -> "clear"
            is FieldMutation.Set -> "set"
        } }}\ncustomMutations=${mutation.customFields.size}\npicturesChanged=${mutation.pictureUpdate != PictureUpdate.Unchanged}"
        appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Tag write started", context, uri)
        val result = try {
            run attempt@ {
                val current = readStrict(uri)
                stage = "resolve-mutation"
                val resolved = mutationResolver.resolve(uri, current, mutation)
                appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Tag mutation resolved",
                    "uri=$uri\ntagKeys=${resolved.tags.keys}\nexistingPictures=${current.pictures.map { "${it.type}:${it.mimeType}:${it.data.size}" }}\n" +
                        "pictureCommand=${when (val pictures = resolved.pictures) {
                            PictureWriteCommand.Unchanged -> "unchanged"
                            is PictureWriteCommand.ReplaceAll -> "replace-all count=${pictures.pictures.size} types=${pictures.pictures.map { it.type }}"
                        }}", uri)
                stage = "open-writable-descriptor"
                val writableDescriptor = fileAccess.openWritableDescriptor(uri, trace)
                    ?: return@attempt fileAccess.createWritePermissionIntentSender(uri, trace)?.let { intentSender ->
                        AudioTagWriteResult.PermissionRequired(intentSender)
                    } ?: AudioTagWriteResult.Failed(
                        IllegalStateException("Unable to open writable descriptor")
                    )

                writableDescriptor.use { descriptor ->
                    if (resolved.tags.isNotEmpty()) {
                        stage = "write-tags"
                        val tagsWritten = AudioTagWriter.writeTags(
                            pfd = descriptor,
                            updates = resolved.tags,
                            preserveOldTags = true
                        )
                        trace("tagsWritten=$tagsWritten")
                        if (!tagsWritten) {
                            return@attempt AudioTagWriteResult.Failed(IllegalStateException("Audio tag write failed"))
                        }
                    }

                    when (val pictureCommand = resolved.pictures) {
                        PictureWriteCommand.Unchanged -> Unit
                        is PictureWriteCommand.ReplaceAll -> {
                            stage = "write-pictures"
                            val picturesWritten = AudioTagWriter.writePictures(
                                pfd = descriptor,
                                pictures = pictureCommand.pictures
                            )
                            trace("picturesWritten=$picturesWritten")
                            if (!picturesWritten) {
                                return@attempt AudioTagWriteResult.Failed(
                                    IllegalStateException("Audio picture write failed")
                                )
                            }
                        }
                    }
                }

                stage = "read-back"
                AudioTagWriteResult.Success(readStrict(uri))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            trace("originalError=${e.stackTraceToString()}")
            fileAccess.writePermissionFromThrowable(uri, e, trace)?.let { AudioTagWriteResult.PermissionRequired(it) }
                ?: AudioTagWriteResult.Failed(e)
        }
        val outcome = when (result) {
            is AudioTagWriteResult.Success -> "success"
            is AudioTagWriteResult.PermissionRequired -> "permission-required"
            is AudioTagWriteResult.Failed -> "failed"
        }
        appLogRepository.recordOperation(
            AppLogType.METADATA, TAG, "Tag write $outcome",
            "$context\nstage=$stage\nelapsedMs=${(System.nanoTime() - started) / 1_000_000}\n" +
                accessTrace.joinToString("\n") +
                if (result is AudioTagWriteResult.Failed) "\n${result.error.stackTraceToString()}" else "",
            uri, when (result) {
                is AudioTagWriteResult.Success -> AppLogLevel.DEBUG
                is AudioTagWriteResult.PermissionRequired -> AppLogLevel.WARNING
                is AudioTagWriteResult.Failed -> AppLogLevel.ERROR
            }
        )
        result
    }

    private suspend fun readFromUri(
        uri: String,
        displayName: String,
        strict: Boolean,
        options: AudioTagReadOptions
    ): AudioTagData {
        val accessTrace = mutableListOf<String>()
        var stage = "open-readable-descriptor"
        try {
            fileAccess.openReadableDescriptor(uri) { if (accessTrace.size < 8) accessTrace += it }?.use { descriptor ->
                stage = "read-taglib"
                return AudioTagReader.read(
                    pfd = descriptor,
                    readPictures = true,
                    multiValueSeparator = options.multiValueSeparator,
                    strict = strict
                ).copy(fileName = displayName).also { data ->
                    appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Audio tags read",
                        "uri=$uri\nname=$displayName\nstrict=$strict\ndurationMs=${data.durationMilliseconds}\n" +
                            "pictures=${data.pictures.map { it.type }}\nlyricsChars=${data.lyrics?.length ?: 0}\ncustomKeys=${data.customFields.map { it.key }}", uri)
                }
            }
            stage = "read-stream-cache"
            return readFromStreamCache(uri, displayName, strict, options)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Audio tag read failed",
                "uri=$uri\nname=$displayName\nstrict=$strict\nstage=$stage\n" +
                    accessTrace.joinToString("\n") + "\n${error.stackTraceToString()}", uri, AppLogLevel.ERROR)
            throw error
        }
    }

    private suspend fun readFromStreamCache(
        uriString: String,
        displayName: String,
        strict: Boolean,
        options: AudioTagReadOptions
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
                return AudioTagReader.read(
                    pfd = descriptor,
                    readPictures = true,
                    multiValueSeparator = options.multiValueSeparator,
                    strict = strict
                ).copy(fileName = displayName)
            }
        } finally {
            if (!tempFile.delete()) {
                Log.w(TAG, "Unable to delete temp audio cache: ${tempFile.absolutePath}")
            }
        }
    }

    private companion object {
        const val TAG = "AudioTagRepository"
    }
}
