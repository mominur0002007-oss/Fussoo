package com.tuplayer.app.vault

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** A video kept in the vault. The file lives in app-private storage and is not shown in the gallery. */
data class VaultItem(
    val id: String,
    val name: String,
    val fileName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val addedAt: Long
)

/**
 * The private file locker. Videos are copied into the app's private storage (`filesDir/vault`), which
 * other apps cannot read. The PIN is stored only as a salted PBKDF2 hash. Five wrong PINs in a row
 * lock the vault for 30 seconds, and the lock grows with each further failure.
 *
 * Files are not encrypted on disk. Their protection is Android's per-app sandbox plus the PIN gate.
 * Backups are excluded (see data_extraction_rules.xml).
 */
class VaultStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("tu_vault", Context.MODE_PRIVATE)
    private val dir = File(context.applicationContext.filesDir, "vault").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin)) { "PIN must be 4 to 8 digits" }
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        prefs.edit {
            putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            putInt(KEY_FAILS, 0)
            putLong(KEY_LOCKED_UNTIL, 0L)
        }
    }

    /** Milliseconds until another PIN attempt is allowed. Zero when attempts are open. */
    fun lockoutRemainingMs(now: Long = System.currentTimeMillis()): Long =
        (prefs.getLong(KEY_LOCKED_UNTIL, 0L) - now).coerceAtLeast(0L)

    /** Wrong PINs left before the next lockout. */
    fun attemptsLeft(): Int = (MAX_FAILS - prefs.getInt(KEY_FAILS, 0)).coerceAtLeast(0)

    /** Checks a PIN. Wrong PINs count toward the lockout; a right one clears the count. */
    fun checkPin(pin: String, now: Long = System.currentTimeMillis()): Boolean {
        if (lockoutRemainingMs(now) > 0L) return false
        val salt = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val expected = prefs.getString(KEY_HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val ok = PinHasher.matches(pin, salt, expected)
        prefs.edit {
            if (ok) {
                putInt(KEY_FAILS, 0)
                putLong(KEY_LOCKED_UNTIL, 0L)
            } else {
                val fails = prefs.getInt(KEY_FAILS, 0) + 1
                putInt(KEY_FAILS, fails)
                if (fails >= MAX_FAILS) {
                    // 30 s for the first lockout, doubling for each extra failure, capped at 10 minutes.
                    val step = fails - MAX_FAILS
                    val seconds = (30L shl step.coerceAtMost(5)).coerceAtMost(600L)
                    putLong(KEY_LOCKED_UNTIL, now + seconds * 1000L)
                }
            }
        }
        return ok
    }

    fun list(): List<VaultItem> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val array = JSONArray(indexFile.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                VaultItem(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    fileName = o.getString("file"),
                    sizeBytes = o.getLong("size"),
                    durationMs = o.optLong("duration"),
                    addedAt = o.getLong("added")
                )
            }.filter { File(dir, it.fileName).exists() }.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun fileOf(item: VaultItem): File = File(dir, item.fileName)

    /**
     * Copies a video into the vault. A partial copy is deleted if the copy fails, so the vault never
     * lists a broken file. Blocking I/O: call it off the main thread.
     */
    fun importFrom(resolver: ContentResolver, source: Uri, displayName: String, durationMs: Long): VaultItem {
        val safeName = displayName.ifBlank { "Video" }
        val extension = safeName.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(5)
        val id = UUID.randomUUID().toString()
        val fileName = if (extension.isEmpty()) id else "$id.$extension"
        val target = File(dir, fileName)
        val input = resolver.openInputStream(source) ?: throw IOException("Could not open the video")
        try {
            input.use { src -> target.outputStream().use { dst -> src.copyTo(dst, BUFFER_BYTES) } }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        val item = VaultItem(
            id = id,
            name = safeName,
            fileName = fileName,
            sizeBytes = target.length(),
            durationMs = durationMs,
            addedAt = System.currentTimeMillis()
        )
        writeIndex(list() + item)
        return item
    }

    fun delete(item: VaultItem) {
        File(dir, item.fileName).delete()
        writeIndex(list().filterNot { it.id == item.id })
    }

    /** Forgotten PIN: removes every vault file and the PIN. Nothing can recover them afterwards. */
    fun erase() {
        dir.listFiles()?.forEach { it.delete() }
        dir.mkdirs()
        prefs.edit { clear() }
    }

    private fun writeIndex(items: List<VaultItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("file", item.fileName)
                    .put("size", item.sizeBytes)
                    .put("duration", item.durationMs)
                    .put("added", item.addedAt)
            )
        }
        val temp = File(dir, "index.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(indexFile)) {
            indexFile.writeText(array.toString())
            temp.delete()
        }
    }

    private companion object {
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_FAILS = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_until"
        const val MAX_FAILS = 5
        const val BUFFER_BYTES = 256 * 1024
    }
}
