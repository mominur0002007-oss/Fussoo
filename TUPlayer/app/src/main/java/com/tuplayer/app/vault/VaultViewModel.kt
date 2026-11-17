package com.tuplayer.app.vault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Vault state for the whole app. The vault locks again whenever the app leaves the screen. */
class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val store = VaultStore(application)

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val _hasPin = MutableStateFlow(store.hasPin())
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setPin(pin: String) {
        store.setPin(pin)
        _hasPin.value = true
        _unlocked.value = true
        refresh()
    }

    /** Null when the PIN was right; otherwise the message to show the user. */
    fun unlock(pin: String): String? {
        val wait = store.lockoutRemainingMs()
        if (wait > 0L) return lockoutText(wait)
        if (store.checkPin(pin)) {
            _unlocked.value = true
            refresh()
            return null
        }
        val after = store.lockoutRemainingMs()
        return if (after > 0L) lockoutText(after) else "Wrong PIN. ${store.attemptsLeft()} attempts left before a lockout."
    }

    fun lock() {
        _unlocked.value = false
        _items.value = emptyList()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { _items.value = store.list() }
    }

    /** Copies a video into the vault. Returns the new item, or null if the copy failed. */
    suspend fun importVideo(source: Uri, name: String, durationMs: Long): VaultItem? {
        if (!_unlocked.value) return null
        _busy.value = true
        return try {
            val item = withContext(Dispatchers.IO) {
                store.importFrom(getApplication<Application>().contentResolver, source, name, durationMs)
            }
            _items.value = store.list()
            item
        } catch (e: Exception) {
            _message.value = "Could not copy the video: ${e.message ?: "unknown error"}"
            null
        } finally {
            _busy.value = false
        }
    }

    fun delete(item: VaultItem) {
        viewModelScope.launch(Dispatchers.IO) {
            store.delete(item)
            _items.value = store.list()
        }
    }

    fun eraseAll() {
        viewModelScope.launch(Dispatchers.IO) {
            store.erase()
            _hasPin.value = false
            _unlocked.value = false
            _items.value = emptyList()
        }
    }

    fun fileUri(item: VaultItem): Uri = Uri.fromFile(store.fileOf(item))

    fun clearMessage() {
        _message.value = null
    }

    fun setMessage(text: String) {
        _message.value = text
    }

    private fun lockoutText(ms: Long): String =
        "Too many wrong PINs. Try again in ${(ms + 999L) / 1000L} s."
}
