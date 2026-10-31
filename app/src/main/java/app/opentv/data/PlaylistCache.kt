package app.opentv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stores the last successfully downloaded playlist in app-private storage.
 * The raw M3U is kept so startup does not need the network. It may contain
 * provider credentials, so it never leaves filesDir and backups are disabled.
 */
class PlaylistCache(context: Context) {
    private val file = File(context.filesDir, "playlist.cache")

    suspend fun read(): ByteArray? = withContext(Dispatchers.IO) {
        if (file.exists() && file.length() > 0) file.readBytes() else null
    }

    suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
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
}
