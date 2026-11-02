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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
 * One successful load. [catalog] is only non-null for Xtream lists whose four
 * `player_api.php` calls succeeded after the M3U download; [catalogWarning]
 * describes the catalogue problem shown on that screen: the degradation when
 * the calls failed but the M3U is usable, or the first API decode warning (for
 * example skipped catalogue items) when the catalogue loaded imperfectly. It is
 * typed, never text; the UI owns the wording.
 */
data class LoadedPlaylist(
    val playlist: Playlist,
    val rawBytes: ByteArray,
    val catalog: VodCatalog? = null,
    val catalogWarning: CatalogWarning? = null
)

class PlaylistRepository(private val client: OkHttpClient) {

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

    fun parseCached(bytes: ByteArray, baseUrl: String): Playlist {
        val text = decode(bytes, null).removePrefix("\uFEFF")
        return PlaylistParser.parse(text, baseUrl, bytes.size.toLong())
    }

    suspend fun load(
        url: String,
        onProgress: (bytesRead: Long, totalBytes: Long, parsedEntries: Int) -> Unit
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
                // Sized from the declared length so the common case allocates
                // once and never grows.
                val sink = ByteSink(
                    if (total in 1..MAX_BYTES) total.toInt() else MIN_BUFFER
                )
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (sink.size + count > MAX_BYTES) {
                            throw PlaylistException.TooLarge((sink.size + count).toLong())
                        }
                        sink.write(buffer, 0, count)
                        onProgress(sink.size.toLong(), total, 0)
                    }
                }

                val bytes = sink.take()
                val text = decode(bytes, contentType).removePrefix("\uFEFF")
                val head = text.trimStart().take(64).lowercase()
                if (head.startsWith("<!doctype") || head.startsWith("<html")) {
                    throw PlaylistException.Html()
                }

                val finalUrl = response.request.url.toString()
                val playlist = PlaylistParser.parse(
                    text = text,
                    baseUrl = finalUrl,
                    bytes = bytes.size.toLong()
                ) { entries -> onProgress(bytes.size.toLong(), bytes.size.toLong(), entries) }

                if (playlist.channels.isEmpty()) throw PlaylistException.Empty()
                LoadedPlaylist(playlist, bytes)
            }
        } catch (e: PlaylistException) {
            throw e
        } catch (e: IOException) {
            throw PlaylistException.Network(e)
        } catch (e: OutOfMemoryError) {
            // A legal but very large playlist can exceed the heap. Report it as a
            // readable failure instead of letting the process die.
            throw PlaylistException.OutOfMemory()
        } catch (e: Exception) {
            throw PlaylistException.Unknown(e)
        }
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
        onProgress: (bytesRead: Long, totalBytes: Long, parsedEntries: Int) -> Unit
    ): LoadedPlaylist {
        val url = config.effectiveUrl ?: throw PlaylistException.InvalidUrl()
        val loaded = load(url, onProgress)
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
     * cached in memory for the session. Episode playback URLs resolve from
     * the M3U matcher; unmatched ids fall back to the provider's own
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

    private fun decode(bytes: ByteArray, contentType: String?): String {
        val charset = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"')
        if (!charset.isNullOrBlank()) {
            return try {
                String(bytes, charset(charset))
            } catch (_: Exception) {
                String(bytes, Charsets.UTF_8)
            }
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) String(bytes, Charsets.ISO_8859_1) else utf8
    }

    private fun charset(name: String): java.nio.charset.Charset =
        java.nio.charset.Charset.forName(name)

    companion object {
        const val USER_AGENT = "OpenIPTV/0.1 (Android)"
        const val ACCEPT = "application/x-mpegURL, application/vnd.apple.mpegurl, text/plain, */*"

        /**
         * Upper bound on a downloaded playlist. It exists to stop a hostile or
         * broken URL feeding an unbounded stream into memory, not to size the
         * app's appetite: real provider dumps reach ~35 MB. It is deliberately
         * far above those. It is not a substitute for the heap ceiling either --
         * a list that cannot fit fails with [PlaylistException.OutOfMemory]
         * instead of killing the process.
         */
        const val MAX_BYTES = 128L * 1024 * 1024

        /** Series detail trees kept in memory (see [seriesInfoCache]). */
        private const val MAX_CACHED_SERIES_INFO = 32

        private const val MIN_BUFFER = 1 shl 20
    }
}

/**
 * Growable byte sink that hands its array over without a final copy. Building a
 * multi-megabyte playlist through a [java.io.ByteArrayOutputStream] allocates the
 * document twice: once in the builder and again in the array it returns.
 */
internal class ByteSink(initialCapacity: Int) {
    private var array = ByteArray(initialCapacity.coerceAtLeast(MIN_CAPACITY))

    var size = 0
        private set

    fun write(source: ByteArray, offset: Int, length: Int) {
        val required = size + length
        if (required > array.size) {
            var next = array.size
            while (next < required) next = next shl 1
            array = array.copyOf(next)
        }
        System.arraycopy(source, offset, array, size, length)
        size = required
    }

    /** The buffer, copied only when it was over-allocated. */
    fun take(): ByteArray = if (size == array.size) array else array.copyOf(size)

    private companion object {
        const val MIN_CAPACITY = 1 shl 16
    }
}
