package dev.lluissunol.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Stores the last successfully downloaded playlist of each configured list in
 * app-private storage, keyed by list id. The raw M3U is kept so opening a list
 * does not need the network. It may contain provider credentials, so it never
 * leaves filesDir and backups are disabled.
 */
class PlaylistCache(private val dir: File) {

    /** The production constructor: the cache lives in app-private [Context.filesDir]. */
    constructor(context: Context) : this(context.filesDir)

    /**
     * The cached playlist file of [listId], or null when there is none. The
     * caller parses it straight from the file; the raw bytes are never loaded
     * into memory wholesale.
     */
    suspend fun read(listId: String): File? = withContext(Dispatchers.IO) {
        val file = fileFor(listId)
        if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Stages a new cache entry for [listId]. [PlaylistRepository.load] streams
     * the download into the staged temp file and installs it with
     * [StagedWrite.commit] only after a fully successful parse; any failure or
     * cancellation discards it, so a failed Actualizar never destroys the
     * working cache entry.
     */
    fun stage(listId: String): CacheTarget = StagedWrite(fileFor(listId))

    suspend fun delete(listId: String) = withContext(Dispatchers.IO) {
        val target = fileFor(listId)
        target.delete()
        // A load killed mid-flight (process death, crash) can leave its unique
        // staged temp behind, and filesDir is never reclaimed by the system.
        // Sweep every temp this list ever staged: the target's own name plus a
        // '.' prefix is unique to this list, so temps of other lists survive.
        val prefix = target.name + "."
        target.parentFile?.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix) &&
                (file.name.endsWith(".tmp") || file.name.endsWith(".copy"))
            ) {
                file.delete()
            }
        }
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

    /**
     * One in-flight cache write. Everything here is plain blocking file work
     * with no suspension point, so none of it can be aborted by coroutine
     * cancellation: cleanup always runs, even from an already-cancelled job.
     * The caller ([PlaylistRepository.load]) runs on `Dispatchers.IO`.
     *
     * It keeps the temp-plus-rename semantics the old in-memory `write()` had:
     * the temp file is renamed into place, and the previous entry is only ever
     * removed once the replacement content is fully on disk, so a failed
     * commit can only keep the old cache, never destroy it.
     */
    private class StagedWrite(private val target: File) : CacheTarget {
        /**
         * A fresh, unique temp in the target's directory: same filesystem, so
         * the commit rename stays atomic, and unique, so two loads of the same
         * list (a cancelled one still draining plus a new one) can never write
         * into or discard each other's file. [File.createTempFile] creates
         * the file; the download's `FileOutputStream` truncates it.
         */
        override suspend fun stageTempFile(): File =
            File.createTempFile(target.name + ".", ".tmp", target.parentFile)

        override suspend fun commit(tempFile: File): Boolean {
            // Same-directory rename: on Android it atomically replaces an
            // existing target, so either the old cache is fully in place or
            // the new one is fully installed.
            if (tempFile.renameTo(target)) return true
            // Rename refused (some JVMs/filesystems do not replace an existing
            // target). Copy to a fresh sibling FIRST: the working cache is not
            // touched until the replacement is fully on disk.
            val sibling = File.createTempFile(target.name + ".", ".copy", target.parentFile)
            val copied = runCatching {
                tempFile.inputStream().use { input ->
                    FileOutputStream(sibling).use { output -> input.copyTo(output) }
                }
            }.isSuccess
            if (!copied) {
                sibling.delete()
                return false
            }
            if (sibling.renameTo(target)) {
                tempFile.delete()
                return true
            }
            // The replacement is now safely on disk, so removing the old entry
            // can no longer strand the list without a cache: the rename that
            // follows installs content that already exists in full. (The old
            // code deleted the target before any copy, which is exactly what
            // this order exists to prevent.)
            target.delete()
            val installed = sibling.renameTo(target)
            if (installed) tempFile.delete()
            sibling.delete()
            return installed
        }

        override suspend fun discard(tempFile: File) {
            tempFile.delete()
        }
    }

    companion object {
        const val LEGACY_FILE_NAME = "playlist.cache"
    }
}
