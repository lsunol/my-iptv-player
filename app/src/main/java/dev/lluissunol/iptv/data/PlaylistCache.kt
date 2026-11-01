package dev.lluissunol.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stores the last successfully downloaded playlist of each configured list in
 * app-private storage, keyed by list id. The raw M3U is kept so opening a list
 * does not need the network. It may contain provider credentials, so it never
 * leaves filesDir and backups are disabled.
 */
class PlaylistCache(context: Context) {
    private val dir: File = context.filesDir

    suspend fun read(listId: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(listId)
        if (file.exists() && file.length() > 0) file.readBytes() else null
    }

    suspend fun write(listId: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = fileFor(listId)
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) {
                file.writeBytes(bytes)
                temp.delete()
            }
        }
        Unit
    }

    suspend fun delete(listId: String) = withContext(Dispatchers.IO) {
        fileFor(listId).delete()
        Unit
    }

    /**
     * v1 kept a single cache file. Move it to the migrated list's file the first
     * time, so the upgrade does not require a network request.
     *
     * @return true when the per-list cache file holds data afterwards.
     */
    suspend fun migrateLegacy(listId: String): Boolean = withContext(Dispatchers.IO) {
        val legacy = File(dir, LEGACY_FILE_NAME)
        val target = fileFor(listId)
        if (!legacy.exists() || legacy.length() == 0L) return@withContext false
        if (target.exists() && target.length() > 0) {
            legacy.delete()
            return@withContext true
        }
        if (!legacy.renameTo(target)) {
            val copied = runCatching { target.writeBytes(legacy.readBytes()) }.isSuccess
            if (copied) legacy.delete()
        }
        target.exists() && target.length() > 0
    }

    private fun fileFor(listId: String): File {
        val safe = listId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifEmpty { "list" }
        return File(dir, "playlist-$safe.cache")
    }

    companion object {
        const val LEGACY_FILE_NAME = "playlist.cache"
    }
}
