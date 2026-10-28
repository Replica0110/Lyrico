package com.lonx.lyrico.data.song.file

import android.app.RecoverableSecurityException
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.recordOperation
import com.lonx.lyrico.data.model.log.withLogOperation
import com.lonx.lyrico.data.model.log.newLogOperation
import com.lonx.lyrico.data.repository.AppLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class SongFileRepositoryImpl(
    private val context: Context,
    private val fileAccess: AudioFileAccess,
    private val appLogRepository: AppLogRepository
) : SongFileRepository {

    override suspend fun deleteFile(song: SongEntity): DeleteSongFileResult = withLogOperation("delete-file") {
        val result = if (song.source == "SAF") {
            deleteSafSongFile(song)
        } else {
            deleteMediaStoreSongFile(song)
        }
        val outcome = when (result) {
            DeleteSongFileResult.Deleted -> "deleted"
            DeleteSongFileResult.AlreadyMissing -> "already-missing"
            is DeleteSongFileResult.PermissionRequired -> "permission-required"
            is DeleteSongFileResult.Failed -> "failed"
        }
        appLogRepository.recordOperation(AppLogType.APP, TAG, "File deletion $outcome",
            "uri=${song.uri}\npath=${song.filePath}\nsource=${song.source}\n" +
                ((result as? DeleteSongFileResult.Failed)?.throwable?.stackTraceToString() ?: ""), song.uri,
            when (result) {
                is DeleteSongFileResult.Failed -> AppLogLevel.ERROR
                is DeleteSongFileResult.PermissionRequired -> AppLogLevel.WARNING
                else -> AppLogLevel.INFO
            })
        result
    }

    override suspend fun deleteFiles(songs: List<SongEntity>): BatchSongFileOperationResult {
        return BatchSongFileOperationResult(
            items = songs.map { song ->
                BatchSongFileOperationItem(song, deleteFile(song))
            }
        )
    }

    override suspend fun renameFile(
        song: SongEntity,
        newFileName: String
    ): RenameSongFileResult = withContext(Dispatchers.IO + newLogOperation("rename-file")) {
        val result = if (song.source == "SAF") {
            renameSafSong(song, newFileName)
        } else {
            renameFilePathSong(song, newFileName)
        }
        val outcome = when (result) {
            is RenameSongFileResult.Success -> "renamed"
            is RenameSongFileResult.NameConflict -> "name-conflict"
            is RenameSongFileResult.PermissionRequired -> "permission-required"
            is RenameSongFileResult.Failed -> "failed"
        }
        appLogRepository.recordOperation(AppLogType.APP, TAG, "File rename $outcome",
            "uri=${song.uri}\nsource=${song.source}\noldPath=${song.filePath}\nrequestedName=$newFileName\n" +
                "newUri=${(result as? RenameSongFileResult.Success)?.newUri}\n" +
                ((result as? RenameSongFileResult.Failed)?.throwable?.stackTraceToString() ?: ""), song.uri,
            when (result) {
                is RenameSongFileResult.Failed -> AppLogLevel.ERROR
                is RenameSongFileResult.Success -> AppLogLevel.INFO
                else -> AppLogLevel.WARNING
            })
        result
    }

    private suspend fun deleteSafSongFile(song: SongEntity): DeleteSongFileResult {
        return try {
            val uri = song.uri.toUri()
            val deleted = fileAccess.deleteDocument(uri)
            when {
                deleted -> DeleteSongFileResult.Deleted
                fileAccess.isUriMissing(uri) -> DeleteSongFileResult.AlreadyMissing
                else -> DeleteSongFileResult.Failed(IllegalStateException("SAF provider refused delete"))
            }
        } catch (e: FileNotFoundException) {
            DeleteSongFileResult.AlreadyMissing
        } catch (e: Exception) {
            fileAccess.writePermissionFromThrowable(song.uri, e)?.let {
                return DeleteSongFileResult.PermissionRequired(it)
            }
            if (fileAccess.isUriMissing(song.uri.toUri())) {
                DeleteSongFileResult.AlreadyMissing
            } else {
                Log.e(TAG, "Failed to delete SAF song: ${song.uri}", e)
                DeleteSongFileResult.Failed(e)
            }
        }
    }

    private suspend fun deleteMediaStoreSongFile(song: SongEntity): DeleteSongFileResult {
        return try {
            val rowsDeleted = context.contentResolver.delete(song.uri.toUri(), null, null)
            if (rowsDeleted > 0) {
                DeleteSongFileResult.Deleted
            } else {
                DeleteSongFileResult.AlreadyMissing
            }
        } catch (e: FileNotFoundException) {
            DeleteSongFileResult.AlreadyMissing
        } catch (e: RecoverableSecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                DeleteSongFileResult.PermissionRequired(e.userAction.actionIntent.intentSender)
            } else {
                DeleteSongFileResult.Failed(e)
            }
        } catch (e: Exception) {
            fileAccess.writePermissionFromThrowable(song.uri, e)?.let {
                return DeleteSongFileResult.PermissionRequired(it)
            }
            Log.e(TAG, "Failed to delete song: ${song.title}", e)
            DeleteSongFileResult.Failed(e)
        }
    }

    private suspend fun renameSafSong(
        song: SongEntity,
        newFileName: String
    ): RenameSongFileResult {
        return try {
            val renamedUri = fileAccess.renameDocument(song.uri.toUri(), newFileName)
                ?: return RenameSongFileResult.Failed(IllegalStateException("SAF provider returned null"))

            val parentPath = song.filePath.substringBeforeLast("/", missingDelimiterValue = "")
            val newPath = if (parentPath.isBlank()) newFileName else "$parentPath/$newFileName"

            RenameSongFileResult.Success(
                oldUri = song.uri,
                newUri = renamedUri.toString(),
                newFilePath = newPath,
                newFileName = newFileName
            )
        } catch (e: Exception) {
            fileAccess.writePermissionFromThrowable(song.uri, e)?.let {
                return RenameSongFileResult.PermissionRequired(it)
            }
            Log.e(TAG, "Failed to rename SAF song: ${song.uri}", e)
            RenameSongFileResult.Failed(e)
        }
    }

    private fun renameFilePathSong(
        song: SongEntity,
        newFileName: String
    ): RenameSongFileResult {
        return try {
            val oldFile = File(song.filePath)
            if (!oldFile.exists()) {
                return RenameSongFileResult.Failed(FileNotFoundException(song.filePath))
            }

            val newFile = File(oldFile.parent, newFileName)
            if (newFile.exists()) {
                return RenameSongFileResult.NameConflict(newFileName)
            }

            if (!oldFile.renameTo(newFile)) {
                return RenameSongFileResult.Failed(IllegalStateException("File rename returned false"))
            }

            RenameSongFileResult.Success(
                oldUri = song.uri,
                newUri = newFile.toURI().toString(),
                newFilePath = newFile.absolutePath,
                newFileName = newFileName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename song: ${song.fileName}", e)
            RenameSongFileResult.Failed(e)
        }
    }

    private companion object {
        const val TAG = "SongFileRepository"
    }
}
