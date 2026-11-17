package com.tuplayer.app.library

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuplayer.app.settings.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Library state for the whole app. Device videos and folder videos live in separate flows, so a
 * device scan can never overwrite a folder scan result (fixes the race in the original code).
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)

    private val device = MutableStateFlow<List<VideoEntry>>(emptyList())
    private val folder = MutableStateFlow<List<VideoEntry>>(emptyList())
    private val activeScans = MutableStateFlow(0)
    private val _status = MutableStateFlow("Ready.")
    private val _folderUri = MutableStateFlow(prefs.libraryTreeUri?.toUri())

    val videos: StateFlow<List<VideoEntry>> = combine(device, folder) { d, f ->
        (d + f)
            .distinctBy { it.uri.toString() }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val scanning: StateFlow<Boolean> = activeScans
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val status: StateFlow<String> = _status.asStateFlow()
    val folderUri: StateFlow<Uri?> = _folderUri.asStateFlow()

    init {
        _folderUri.value?.let { loadFolder(it) }
    }

    fun scanDevice() = runScan {
        try {
            device.value = withContext(Dispatchers.IO) { queryDeviceVideos(getApplication()) }
            _status.value = "Library updated · ${device.value.size} videos found on this device"
        } catch (e: SecurityException) {
            _status.value = "Media access revoked. Grant it to scan."
        }
    }

    fun setFolder(uri: Uri) {
        persistReadPermission(getApplication(), uri)
        prefs.libraryTreeUri = uri.toString()
        _folderUri.value = uri
        loadFolder(uri)
    }

    fun setStatus(text: String) {
        _status.value = text
    }

    private fun loadFolder(uri: Uri) = runScan {
        val found = withContext(Dispatchers.IO) { scanTree(getApplication(), uri) }
        folder.value = found
        _status.value = "Folder scan complete · ${found.size} videos found"
    }

    private fun runScan(block: suspend () -> Unit) {
        viewModelScope.launch {
            activeScans.update { it + 1 }
            try {
                block()
            } finally {
                activeScans.update { it - 1 }
            }
        }
    }
}
