package com.lonx.lyrico.utils

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.lonx.lyrico.data.model.SongFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * 音乐文件扫描器 - 使用 MediaStore API 进行高效扫描
 */
class MusicScanner(private val context: Context) {

    private val TAG = "MusicScanner"

    /**
     * 使用MediaStore扫描设备上的所有音乐文件，并将其以Flow形式发送
     */
    fun scanMusicFiles(): Flow<SongFile> = flow {

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        // Get only music files
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        Log.d(TAG, "Querying MediaStore for audio files...")

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dateModifiedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    // MediaStore.Audio.Media.DATE_MODIFIED is in seconds, convert to milliseconds
                    val lastModified = cursor.getLong(dateModifiedColumn) * 1000L
//                    Log.d(TAG, "最后修改时间: $lastModified")
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    emit(SongFile(contentUri.toString(), name, lastModified))
                }
                Log.d(TAG, "MediaStore scan finished.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query MediaStore", e)
            // Error is logged, flow will just complete.
        }
    }.flowOn(Dispatchers.IO)

}