package com.tuplayer.app.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a record of the last crash so it can be read in Settings. Playback problems on a real
 * phone often can't be reproduced in a lab, so this is the first thing to check.
 *
 * - Java exceptions: written to filesDir/last_crash.txt by a default uncaught-exception handler.
 * - Native crashes (libmpv) and out-of-memory kills: reported by Android's exit history (API 30+).
 */
object CrashLog {
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 20_000

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val version = runCatching {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                }.getOrDefault("?")
                val text = buildString {
                    append("Time: ").append(timestamp(System.currentTimeMillis())).append('\n')
                    append("App version: ").append(version).append('\n')
                    append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                    append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                    append("ABI: ").append(Build.SUPPORTED_ABIS.firstOrNull() ?: "?").append('\n')
                    append("Thread: ").append(thread.name).append("\n\n")
                    append(Log.getStackTraceString(error))
                }
                File(app.filesDir, FILE_NAME).writeText(text.take(MAX_CHARS))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun readLastCrash(context: Context): String? =
        File(context.applicationContext.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()

    fun clearLastCrash(context: Context) {
        File(context.applicationContext.filesDir, FILE_NAME).delete()
    }

    /** Summary of the most recent exit of this app, or null when none is recorded or the API is older than 30. */
    fun lastExitSummary(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) exitSummaryApi30(context) else null

    @RequiresApi(Build.VERSION_CODES.R)
    private fun exitSummaryApi30(context: Context): String? {
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val info = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return null
        val reason = when (info.reason) {
            ApplicationExitInfo.REASON_CRASH -> "Java crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash (libmpv or decoder)"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Killed by Android: out of memory"
            ApplicationExitInfo.REASON_ANR -> "Not responding (ANR)"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "Closed by user"
            else -> "Exit code ${info.reason}"
        }
        val detail = info.description?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        return "$reason at ${timestamp(info.timestamp)}$detail"
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
}
