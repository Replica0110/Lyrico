package com.lonx.lyrico.utils

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.lonx.lyrico.data.model.SongFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class MusicScanner(
    private val context: Context,
)
 {

    private val TAG = "MusicScanner"

     fun scanMusicFiles(): Flow<SongFile> = flow {

         val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
             MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
         } else {
             MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
         }

         val projection = arrayOf(
             MediaStore.Audio.Media._ID,
             MediaStore.Audio.Media.DISPLAY_NAME,
             MediaStore.Audio.Media.DATE_MODIFIED,
             MediaStore.Audio.Media.DATE_ADDED,
             MediaStore.Audio.Media.DATA,
             MediaStore.Audio.Media.DURATION
         )

         val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

         context.contentResolver.query(
             collection,
             projection,
             selection,
             null,
             "${MediaStore.Audio.Media.DATE_ADDED} DESC"
         )?.use { cursor ->

             val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
             val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
             val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
             val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
             val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
             val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

             while (cursor.moveToNext()) {
                 val filePath = cursor.getString(dataCol)


                 val id = cursor.getLong(idCol)

                 emit(
                     SongFile(
                         mediaId = id,
                         uri = ContentUris.withAppendedId(collection, id),
                         filePath = filePath,
                         fileName = cursor.getString(nameCol),
                         lastModified = cursor.getLong(modifiedCol) * 1000L,
                         dateAdded = cursor.getLong(addedCol) * 1000L,
                         duration = cursor.getLong(durationCol)
                     )
                 )
             }
         }
     }.flowOn(Dispatchers.IO)
}