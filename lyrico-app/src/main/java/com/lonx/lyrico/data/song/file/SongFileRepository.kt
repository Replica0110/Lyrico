package com.lonx.lyrico.data.song.file

import com.lonx.lyrico.data.model.entity.SongEntity

interface SongFileRepository {
    suspend fun deleteFile(song: SongEntity): DeleteSongFileResult

    suspend fun deleteFiles(songs: List<SongEntity>): BatchSongFileOperationResult

    suspend fun renameFile(
        song: SongEntity,
        newFileName: String
    ): RenameSongFileResult
}
