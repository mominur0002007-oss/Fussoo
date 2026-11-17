package com.tuplayer.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.core.graphics.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val THUMB_WIDTH_PX = 320

/** In-memory cache so scrolling a long list does not decode the same frame twice. Capped at 6 MB. */
object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

/** Decodes one frame near the start of the video, scaled down for the list. Returns null if it can't. */
private suspend fun decodeThumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    ThumbnailCache.get(uri.toString())?.let { return@withContext it }
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: return@withContext null
        if (frame.width <= 0 || frame.height <= 0) return@withContext null
        val height = (frame.height.toLong() * THUMB_WIDTH_PX / frame.width).toInt().coerceAtLeast(1)
        val scaled = frame.scale(THUMB_WIDTH_PX, height)
        if (scaled !== frame) frame.recycle()
        ThumbnailCache.put(uri.toString(), scaled)
        scaled
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}

/** A list thumbnail. Shows a play glyph until the frame is ready, or permanently if the file has no frame. */
@Composable
fun Thumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = ThumbnailCache.get(uri.toString()), key1 = uri) {
        value = decodeThumbnail(context, uri)
    }
    Box(modifier.background(SurfaceB), contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("▶", color = Muted, fontSize = 16.sp)
        }
    }
}
