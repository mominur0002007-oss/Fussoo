package com.tuplayer.app.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** A song on the device. [uri] is the MediaStore content URI that Media3 plays. */
data class AudioEntry(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri
)

/** Songs the MediaStore marks as music (ringtones, notifications and voice notes are left out). */
fun queryDeviceMusic(context: Context): List<AudioEntry> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION
    )
    val results = mutableListOf<AudioEntry>()
    context.contentResolver.query(
        collection,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            results += AudioEntry(
                id = id,
                title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() } ?: "Unknown artist",
                album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown album",
                durationMs = cursor.getLong(durationCol),
                uri = ContentUris.withAppendedId(collection, id)
            )
        }
    }
    return results
}
