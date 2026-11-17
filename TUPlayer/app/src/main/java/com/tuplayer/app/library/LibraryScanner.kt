package com.tuplayer.app.library

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile

/** A video in the library. [folder] is the display path used by the Folders tab. */
data class VideoEntry(
    val name: String,
    val uri: Uri,
    val durationMs: Long = 0L,
    val source: String = "Device",
    val folder: String = ""
)

private const val TAG = "TUPlayer.Library"
private const val MAX_FOLDER_DEPTH = 8
private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "mov", "webm", "avi", "m4v", "3gp", "ts", "mpeg", "mpg")

/** Videos the MediaStore knows about. Needs READ_MEDIA_VIDEO (or READ_EXTERNAL_STORAGE on API 32 and below). */
fun queryDeviceVideos(context: Context): List<VideoEntry> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME
    )
    val results = mutableListOf<VideoEntry>()
    context.contentResolver.query(
        collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            results += VideoEntry(
                name = cursor.getString(nameCol) ?: "Video",
                uri = ContentUris.withAppendedId(collection, id),
                durationMs = cursor.getLong(durationCol),
                source = "Device",
                folder = cursor.getString(bucketCol) ?: "Device"
            )
        }
    }
    return results
}

/** Recursively finds videos under a folder the user chose with the system folder picker. */
fun scanTree(context: Context, treeUri: Uri): List<VideoEntry> {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    val rootName = root.name ?: "Selected folder"
    val found = mutableListOf<VideoEntry>()

    fun visit(dir: DocumentFile, depth: Int, relative: String) {
        if (depth > MAX_FOLDER_DEPTH) return
        for (file in dir.listFiles()) {
            val fileName = file.name.orEmpty()
            when {
                file.isDirectory -> {
                    val next = if (relative.isEmpty()) fileName else "$relative/$fileName"
                    visit(file, depth + 1, next)
                }
                file.isFile && isVideo(file) -> found += VideoEntry(
                    name = fileName.ifEmpty { "Video" },
                    uri = file.uri,
                    durationMs = probeDurationMs(context, file.uri),
                    source = "Selected folder",
                    folder = if (relative.isEmpty()) rootName else "$rootName/$relative"
                )
            }
        }
    }

    visit(root, 0, "")
    return found
}

fun queryDisplayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

/**
 * Keeps read access to a document the user picked, across app restarts when Android allows it.
 * Failures are logged, not silently ignored; the grant then lasts only for this session.
 */
fun persistReadPermission(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (e: SecurityException) {
        Log.w(TAG, "No persistable grant for $uri; access lasts for this session", e)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "$uri does not support persistable grants", e)
    }
}

private fun isVideo(file: DocumentFile): Boolean =
    file.type?.startsWith("video/") == true ||
        file.name?.substringAfterLast('.', "")?.lowercase() in VIDEO_EXTENSIONS

private fun probeDurationMs(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (e: Exception) {
        0L
    } finally {
        retriever.release()
    }
}
