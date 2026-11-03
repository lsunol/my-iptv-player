package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.CatalogWarning
import dev.lluissunol.iptv.data.model.Channel
import dev.lluissunol.iptv.data.model.Playlist
import dev.lluissunol.iptv.data.model.PlaylistConfig
import dev.lluissunol.iptv.data.model.PlaylistKind
import dev.lluissunol.iptv.data.model.SeriesInfo
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.buildVodCatalog
import dev.lluissunol.iptv.data.model.buildXtreamStreamUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException

sealed class PlaylistException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidUrl : PlaylistException("URL no válida")
    class Http(val code: Int) : PlaylistException("HTTP $code")
    class Html : PlaylistException("HTML en lugar de una lista")
    class Empty : PlaylistException("Lista vacía")
    class Malformed(cause: Throwable? = null) : PlaylistException("Respuesta no válida", cause)
    class TooLarge(val bytes: Long) : PlaylistException("Lista demasiado grande")
    class OutOfMemory : PlaylistException("Sin memoria al procesar la lista")
    class Network(cause: Throwable) : PlaylistException("Error de red", cause)
    class Unknown(cause: Throwable) : PlaylistException("Error desconocido", cause)
}

fun PlaylistException.userMessage(): String = when (this) {
    is PlaylistException.InvalidUrl ->
        "La URL no es válida. Debe empezar por http:// o https://."
    is PlaylistException.Http -> when (code) {
        401, 403 -> "El proveedor rechazó la lista (HTTP $code). Comprueba usuario/contraseña o que la suscripción esté activa."
        404 -> "La URL no existe (HTTP 404). Revisa que sea la dirección completa de la lista."
        in 500..599 -> "El servidor del proveedor falló (HTTP $code). Inténtalo más tarde."
        else -> "El proveedor respondió HTTP $code."
    }
    is PlaylistException.Html ->
        "La URL devolvió una página web, no una lista M3U. Comprueba el enlace y que la suscripción esté activa."
    is PlaylistException.Empty ->
        "La lista se descargó pero no contiene canales."
    is PlaylistException.Malformed ->
        "El proveedor devolvió datos no válidos. Prueba a actualizar la lista."
    is PlaylistException.TooLarge ->
        "La lista ocupa ${bytes / 1_000_000} MB y el limite es " +
            "${PlaylistRepository.MAX_BYTES / 1_000_000} MB."
    is PlaylistException.OutOfMemory ->
        "El dispositivo se quedo sin memoria al procesar la lista. " +
            "Cierra otras aplicaciones e intentalo de nuevo."
    is PlaylistException.Network -> when (val cause = cause) {
        is SocketTimeoutException -> "La lista tardó demasiado en responder. Comprueba tu conexión o la URL."
        is UnknownHostException -> "No se pudo encontrar el servidor del proveedor. Comprueba la URL y tu conexión."
        else -> "No se pudo conectar con el proveedor. Comprueba tu conexión."
    }
    // Never project an arbitrary exception message: the URL (and its
    // credentials) may be part of it. The cause stays on the exception for logs.
    is PlaylistException.Unknown -> "No se pudo cargar la lista."
}

/**
 * Destination of the raw playlist downloaded by [PlaylistRepository.load]:
 * implemented by [PlaylistCache.stage]. The repository streams the download
 * into the staged temp file and either commits it atomically (after a fully
 * successful parse) or discards it (failure or cancellation), so a failed
 * refresh never destroys the working cache entry. Nothing here may log the
 * downloaded content: it contains provider credentials.
 */
interface CacheTarget {
    /** Returns a fresh temp file the download can stream into. */
    suspend fun stageTempFile(): File

    /**
     * Installs the temp file as the new cached playlist. Returns false when it
     * could not be installed; the load still succeeds (the cache is
     * best-effort, as it always was).
     */
    suspend fun commit(tempFile: File): Boolean

    /** Deletes the temp file after a failure or cancellation. */
    suspend fun discard(tempFile: File)
}

/**
 * One successful load. The raw document is never carried back to the caller:
 * it was streamed to a temp file and committed to the cache (when a
 * [CacheTarget] was given). [catalog] is only non-null for Xtream lists whose
 * four `player_api.php` calls succeeded after the M3U download;
 * [catalogWarning] describes the catalogue problem shown on that screen: the
 * degradation when the calls failed but the M3U is usable, or the first API
 * decode warning (for example skipped catalogue items) when the catalogue
 * loaded imperfectly. It is typed, never text; the UI owns the wording.
 */
data class LoadedPlaylist(
    val playlist: Playlist,
    val catalog: VodCatalog? = null,
    val catalogWarning: CatalogWarning? = null
)

class PlaylistRepository(
    private val client: OkHttpClient,
    /** Directory for the download temp file when no [CacheTarget] is given. */
    private val tempDir: File? = null
) {

    private val xtreamClient = XtreamClient(client)

    /**
     * Stream-id matchers per list id, rebuilt on every successful M3U load of
     * an Xtream list. They let the lazy `get_series_info` calls resolve episode
     * URLs from the M3U even when the catalogue itself came from disk cache.
     */
    private val matchers = HashMap<String, XtreamStreamMatcher>()

    /**
     * Lazy per-series detail cache (`get_series_info` is fetched at most once
     * per series per session; a second open of the same series is instant).
     */
    private val seriesInfoCache = object : LinkedHashMap<String, SeriesInfo>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SeriesInfo>): Boolean =
            size > MAX_CACHED_SERIES_INFO
    }

    /**
     * Parses a playlist previously cached on disk (see [PlaylistCache])
     * without materialising it in memory: the file is consumed line by line
     * through a small reader. [Playlist.bytes] is the file size. Blocking;
     * callers run it off the main thread.
     */
    fun parseCached(file: File, baseUrl: String): Playlist =
        parseFile(file, baseUrl, contentType = null)

    /**
     * Downloads the playlist into a temp file -- never into a byte array --
     * and then parses it from the file. Peak memory is the parsed channel
     * model, not the document size. When [cacheTarget] is given the temp file
     * *is* the staged cache entry: it is committed atomically after a fully
     * successful parse and discarded on any failure or cancellation, so the
     * working cache survives a bad refresh.
     */
    suspend fun load(
        url: String,
        onProgress: (bytesRead: Long, totalBytes: Long, parsedEntries: Int) -> Unit,
        cacheTarget: CacheTarget? = null
    ): LoadedPlaylist = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            throw PlaylistException.InvalidUrl()
        }

        val request = Request.Builder()
            .url(trimmed)
            .header("User-Agent", USER_AGENT)
            .header("Accept", ACCEPT)
            .build()

        val temp = cacheTarget?.stageTempFile()
            ?: File.createTempFile("playlist", ".tmp", tempDir)
        var committed = false
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PlaylistException.Http(response.code)

                val contentType = response.header("Content-Type")
                if (contentType?.contains("text/html", ignoreCase = true) == true) {
                    throw PlaylistException.Html()
                }

                val body = response.body
                    ?: throw PlaylistException.Unknown(IOException("Respuesta sin cuerpo"))
                val total = body.contentLength()
                var size = 0L
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count.toLong()
                            if (size > MAX_BYTES) {
                                throw PlaylistException.TooLarge(size)
                            }
                            output.write(buffer, 0, count)
                            onProgress(size, total, 0)
                        }
                    }
                }

                if (looksLikeHtml(temp)) throw PlaylistException.Html()

                val finalUrl = response.request.url.toString()
                val playlist = parseFile(temp, finalUrl, contentType) { entries ->
                    onProgress(size, size, entries)
                }

                if (playlist.channels.isEmpty()) throw PlaylistException.Empty()
                if (cacheTarget != null && coroutineContext.isActive) {
                    // Commit only while this load is still wanted: a load
                    // cancelled by a list switch, an edit or screen exit must
                    // not install a cache entry for a list the user may since
                    // have deleted or re-pointed. There is no suspension point
                    // between this check and the commit's return, so a load
                    // that passes it always finishes committing; a cancelled
                    // one skips it and its staged temp is discarded below.
                    // (Deliberate decision: a cancelled load discards; the
                    // next open of the list re-downloads.)
                    committed = runCatching { cacheTarget.commit(temp) }.getOrDefault(false)
                }
                LoadedPlaylist(playlist)
            }
        } catch (e: CancellationException) {
            // A cancel (list switch, screen exit) must surface as such, never
            // as an error; the finally block discards the temp file.
            throw e
        } catch (e: PlaylistException) {
            throw e
        } catch (e: IOException) {
            throw PlaylistException.Network(e)
        } catch (e: OutOfMemoryError) {
            // A legal but very large playlist can exceed the heap. The channel
            // model is the real ceiling now that the document lives on disk;
            // report it as a readable failure instead of letting the process die.
            throw PlaylistException.OutOfMemory()
        } catch (e: Exception) {
            throw PlaylistException.Unknown(e)
        } finally {
            if (!committed) {
                runCatching {
                    if (cacheTarget != null) cacheTarget.discard(temp) else temp.delete()
                }
            }
        }
    }

    // ------------------------------------------------------ file parsing

    /**
     * Parses a downloaded or cached playlist file with the full charset policy:
     * BOM first, then a declared `Content-Type` charset, then a strict UTF-8
     * probe of the first [PROBE_BYTES] bytes. The real parse also decodes
     * strictly; if a malformed sequence shows up beyond the probe window (a
     * Latin-1 list whose first 256 KB is pure ASCII, accents only in later
     * VOD groups), the whole file is re-read as ISO-8859-1 instead of emitting
     * U+FFFD replacement characters.
     */
    private fun parseFile(
        file: File,
        baseUrl: String,
        contentType: String?,
        onProgress: ((parsedEntries: Int) -> Unit)? = null
    ): Playlist {
        val bytes = file.length()
        val charset = decideCharset(file, contentType)
        val progress = onProgress ?: {}
        return try {
            PlaylistParser.parse(readerFor(file, charset), baseUrl, bytes, progress)
        } catch (e: CharacterCodingException) {
            if (charset != Charsets.UTF_8) throw PlaylistException.Malformed(e)
            // Known behaviour delta (documented, not "fixed"): the second pass
            // re-emits onProgress from the parsed-entry count it reaches, so
            // for a misdetected encoding the UI's entry count can climb, drop
            // back and climb again. Cosmetic: the tick after the parse loop is
            // the authoritative count.
            PlaylistParser.parse(readerFor(file, Charsets.ISO_8859_1), baseUrl, bytes, progress)
        }
    }

    /**
     * A reader over the whole file that decodes [charset] strictly: malformed
     * input throws [CharacterCodingException] out of `readLine()` instead of
     * silently producing U+FFFD characters in channel names and groups.
     */
    private fun readerFor(file: File, charset: Charset): Reader =
        InputStreamReader(
            BufferedInputStream(FileInputStream(file), 32 * 1024),
            strictDecoder(charset)
        )

    private fun strictDecoder(charset: Charset): CharsetDecoder =
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

    /**
     * BOM > declared Content-Type charset > strict UTF-8 probe of the first
     * [PROBE_BYTES] bytes. The UTF-8 BOM decodes to a U+FEFF the parser strips
     * from the first line; UTF-16 BOMs select their endianness directly.
     */
    private fun decideCharset(file: File, contentType: String?): Charset {
        val head = ByteArray(4)
        val seen = readPrefix(file, head)
        if (seen >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE
        }
        if (seen >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE
        }
        if (seen >= 3 && head[0] == 0xEF.toByte() &&
            head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()
        ) {
            return Charsets.UTF_8
        }
        val declared = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"')
        if (!declared.isNullOrBlank()) {
            return try {
                Charset.forName(declared)
            } catch (_: Exception) {
                Charsets.UTF_8
            }
        }
        return if (isUtf8Prefix(file)) Charsets.UTF_8 else Charsets.ISO_8859_1
    }

    /**
     * Strictly decodes the first [PROBE_BYTES] of the file: a malformed
     * sequence inside the window means the file is not UTF-8. A multi-byte
     * character cut exactly at the window edge is tolerated (up to 3 trailing
     * bytes) because the real parse decodes strictly and falls back if the
     * character turns out malformed after all -- being lenient at the edge
     * costs at most one extra pass, never a wrong decode.
     */
    private fun isUtf8Prefix(file: File): Boolean = try {
        FileInputStream(file).use { input ->
            val prefix = ByteArray(PROBE_BYTES)
            val total = readFully(input, prefix)
            val decoder = strictDecoder(Charsets.UTF_8)
            val bytes = ByteBuffer.wrap(prefix, 0, total)
            val result = decoder.decode(bytes, CharBuffer.allocate(total + 1), true)
            // decode(ByteBuffer, CharBuffer, boolean) stops with the buffer's
            // position on the offending byte, so the trailing distance is
            // public API: (total - bytes.position()).
            result.isUnderflow ||
                (result.isMalformed && total == PROBE_BYTES &&
                    (total - bytes.position()) <= 3)
        }
    } catch (_: IOException) {
        false
    }

    private fun readPrefix(file: File, buffer: ByteArray): Int = try {
        FileInputStream(file).use { input -> readFully(input, buffer) }
    } catch (_: IOException) {
        0
    }

    private fun readFully(input: FileInputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    /**
     * A 200 HTML page is not a playlist: sniff the start of the file. The head
     * is decoded with the same charset policy the parse uses ([decideCharset]):
     * a UTF-8 BOM is EF BB BF on the wire, which decodes as three Latin-1
     * characters instead of U+FEFF when read naively as ISO-8859-1, so a sniff
     * that only strips U+FEFF would let a BOM-prefixed HTML error page with
     * a non-`text/html` Content-Type slip through and surface as a misleading
     * `Empty` error.
     */
    private fun looksLikeHtml(file: File): Boolean = try {
        FileInputStream(file).use { input ->
            val head = ByteArray(HTML_SNIFF_BYTES)
            val total = readFully(input, head)
            val text = String(head, 0, total, decideCharset(file, contentType = null))
                .removePrefix("\uFEFF")
                .trimStart()
                .take(64)
                .lowercase(java.util.Locale.ROOT)
            text.startsWith("<!doctype") || text.startsWith("<html")
        }
    } catch (_: IOException) {
        false
    }

    // ------------------------------------------------------ Xtream catalogue

    /**
     * Loads the M3U (exactly like [load]) and, for [PlaylistKind.XTREAM] lists,
     * enriches it with the `player_api.php` catalogue. The M3U remains the
     * source of playback URLs; the API contributes metadata only.
     *
     * Degradation is explicit and never fatal: if the API calls fail (network,
     * HTTP error, malformed data, or out of memory) while the M3U succeeded,
     * the result carries [LoadedPlaylist.catalogWarning] and no catalog, and
     * the caller shows today's flat list with a one-line notice.
     */
    suspend fun loadWithCatalog(
        config: PlaylistConfig,
        onProgress: (bytesRead: Long, totalBytes: Long, parsedEntries: Int) -> Unit,
        cacheTarget: CacheTarget? = null
    ): LoadedPlaylist {
        val url = config.effectiveUrl ?: throw PlaylistException.InvalidUrl()
        val loaded = load(url, onProgress, cacheTarget)
        if (config.kind != PlaylistKind.XTREAM) return loaded

        val matcher = withContext(Dispatchers.Default) {
            XtreamStreamMatcher(loaded.playlist.channels).also {
                synchronized(matchers) { matchers[config.id] = it }
            }
        }
        val fallback: (String, String) -> String? = { type, id ->
            buildXtreamStreamUrl(config.server, config.username, config.password, type, id)
        }

        // The API's decode warnings (skipped items, a non-array answer, ...)
        // are collected by XtreamClient but never thrown: keep them so the load
        // can show the first one instead of silently swallowing them.
        var apiWarnings: List<CatalogWarning> = emptyList()
        val catalog = try {
            val api = xtreamClient.fetchCatalog(config)
            apiWarnings = api.warnings
            // The merge normalizes a search key and detects a quality tag per
            // catalogue item, which costs hundreds of milliseconds on a
            // 50k-title account: it must never run on the caller's (Main)
            // dispatcher.
            withContext(Dispatchers.Default) {
                val merged = buildVodCatalog(api, matcher, fallback)
                // An API that answers with empty catalogues while the M3U clearly
                // has /movie/ or /series/ entries did not work (several forks send
                // `[]` or a user_info object on failure): degrade instead of
                // showing a dishonest empty grid.
                if (merged.movies.isEmpty() && merged.series.isEmpty() &&
                    (loaded.playlist.count(SourceType.MOVIE) > 0 ||
                        loaded.playlist.count(SourceType.SERIES) > 0)
                ) {
                    null
                } else {
                    merged
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            // A huge account can exceed the heap on top of the parsed M3U;
            // the documented degradation path beats a crash.
            null
        } catch (e: Exception) {
            null
        }

        return if (catalog != null) {
            // The catalogue loaded: the first decode warning rides the same
            // one-line notice the degradation path uses, so parse problems
            // stay visible (they are not cached, only reported per load).
            loaded.copy(catalog = catalog, catalogWarning = apiWarnings.firstOrNull())
        } else {
            loaded.copy(catalogWarning = CatalogWarning.Unavailable)
        }
    }

    /**
     * Builds (once per list) the stream-id matcher from a playlist that was
     * parsed from the disk cache, so lazily loaded series episodes still
     * resolve to the M3U URLs at startup without a network request.
     */
    suspend fun registerChannels(listId: String, channels: List<Channel>) {
        val known = synchronized(matchers) { listId in matchers }
        if (!known) {
            val matcher = withContext(Dispatchers.Default) { XtreamStreamMatcher(channels) }
            synchronized(matchers) { matchers.putIfAbsent(listId, matcher) }
        }
    }

    /**
     * The seasons and episodes of one series, fetched lazily on first open and
     * cached in memory for the session. Episode playback URLs resolve from the
     * M3U matcher; unmatched ids fall back to the provider's own
     * `.m3u8` pattern. Throws [PlaylistException] so the caller can show the
     * standard actionable error.
     */
    suspend fun getSeriesInfo(config: PlaylistConfig, seriesId: Long): SeriesInfo {
        val cacheKey = "${config.id}:$seriesId"
        synchronized(seriesInfoCache) { seriesInfoCache[cacheKey] }?.let { return it }

        val dto = xtreamClient.fetchSeriesInfo(config, seriesId)
        val fallback: (String, String) -> String? = { type, id ->
            buildXtreamStreamUrl(config.server, config.username, config.password, type, id)
        }
        val matcher = synchronized(matchers) { matchers[config.id] }
        // Same reason as the catalogue merge: the grouping and the per-episode
        // URL resolution are JSON work proportional to the episode count, so
        // they run off the caller's (Main) dispatcher.
        val info = withContext(Dispatchers.Default) {
            dto.toSeriesInfo(
                seriesId = seriesId,
                defaultName = "Serie $seriesId",
                matcher = matcher,
                fallbackUrl = fallback
            )
        }
        synchronized(seriesInfoCache) { seriesInfoCache[cacheKey] = info }
        return info
    }

    /** Drops the in-memory matcher and series cache of one list (URL edit/delete). */
    fun forget(listId: String) {
        synchronized(matchers) { matchers.remove(listId) }
        synchronized(seriesInfoCache) { seriesInfoCache.keys.removeIf { it.startsWith("$listId:") } }
    }

    companion object {
        const val USER_AGENT = "OpenIPTV/0.1 (Android)"
        const val ACCEPT = "application/x-mpegURL, application/vnd.apple.mpegurl, text/plain, */*"

        /**
         * Upper bound on a downloaded playlist, in bytes written to the temp
         * file. Since the download streams straight to disk this is no longer
         * a heap guard; it exists to stop a hostile or broken URL feeding an
         * endless (or enormous) stream into device storage. Real provider
         * dumps reach ~35 MB and it stays deliberately far above them. It is
         * not a substitute for the heap ceiling either -- a list whose channel
         * model cannot fit fails with [PlaylistException.OutOfMemory] instead
         * of killing the process.
         */
        const val MAX_BYTES = 128L * 1024 * 1024

        /** Bytes the strict UTF-8 probe inspects before committing to UTF-8. */
        private const val PROBE_BYTES = 256 * 1024

        /** Download copy buffer. */
        private const val DOWNLOAD_BUFFER_BYTES = 16 * 1024

        /** Bytes inspected by the HTML sniff. */
        private const val HTML_SNIFF_BYTES = 256

        /** Series detail trees kept in memory (see [seriesInfoCache]). */
        private const val MAX_CACHED_SERIES_INFO = 32
    }
}
