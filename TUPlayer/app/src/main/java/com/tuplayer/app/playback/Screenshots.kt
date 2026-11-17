package com.tuplayer.app.playback

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.core.graphics.createBitmap

/** Captures the current video frame and saves it to Pictures/TU Player. */
object Screenshots {

    sealed interface Result {
        data class Saved(val name: String) : Result
        data object NoFrame : Result
        data object UnsupportedOs : Result
        data class Failed(val reason: String) : Result
    }

    /** Copies the frame on the main thread, then saves it. [onResult] runs on the main thread. */
    fun capture(context: Context, view: SurfaceView?, onResult: (Result) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onResult(Result.UnsupportedOs)
            return
        }
        if (view == null || view.width <= 0 || view.height <= 0) {
            onResult(Result.NoFrame)
            return
        }
        val bitmap = createBitmap(view.width, view.height)
        PixelCopy.request(view, bitmap, { copyResult ->
            if (copyResult != PixelCopy.SUCCESS) {
                bitmap.recycle()
                onResult(Result.NoFrame)
                return@request
            }
            val name = "TUPlayer_${System.currentTimeMillis()}.png"
            val result = runCatching { save(context, bitmap, name) }.fold(
                onSuccess = { if (it) Result.Saved(name) else Result.Failed("MediaStore refused the file") },
                onFailure = { Result.Failed(it.message ?: "unknown error") }
            )
            bitmap.recycle()
            onResult(result)
        }, Handler(Looper.getMainLooper()))
    }

    private fun save(context: Context, bitmap: Bitmap, name: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TU Player")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val stream = context.contentResolver.openOutputStream(uri) ?: return false
        return stream.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
