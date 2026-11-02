package dev.lluissunol.iptv.data

import android.content.Context
import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.decodeVodCatalog
import dev.lluissunol.iptv.data.model.encodeVodCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Caches the merged Xtream catalogue (movies, series, categories) of one list
 * in app-private storage, so opening a list shows the poster grid without any
 * network request. It contains the same provider metadata the M3U cache does
 * (posters, names, ids), no credentials, and it never leaves filesDir.
 *
 * Written atomically and only after **all four** `player_api.php` calls
 * succeeded ([PlaylistRepository.loadWithCatalog] guarantees that), mirroring
 * the Actualizar semantics of [PlaylistCache]: a failed refresh never destroys
 * the working catalogue.
 */
class XtreamCache(context: Context) {
    private val dir: File = context.filesDir

    suspend fun read(listId: String): VodCatalog? = withContext(Dispatchers.IO) {
        val file = fileFor(listId)
        if (!file.exists() || file.length() == 0L) return@withContext null
        runCatching { decodeVodCatalog(file.readText()) }.getOrNull()
    }

    suspend fun write(listId: String, catalog: VodCatalog) = withContext(Dispatchers.IO) {
        val file = fileFor(listId)
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(encodeVodCatalog(catalog))
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) {
                file.writeText(encodeVodCatalog(catalog))
                temp.delete()
            }
        }
        Unit
    }

    suspend fun delete(listId: String) = withContext(Dispatchers.IO) {
        fileFor(listId).delete()
        Unit
    }

    private fun fileFor(listId: String): File {
        val safe = listId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifEmpty { "list" }
        return File(dir, "xtream-$safe.json")
    }
}
