package dev.lluissunol.iptv.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers which artwork hosts refuse connections, so the image loader stops
 * hammering them and the UI can say *why* the posters are missing.
 *
 * Real Xtream panels often point `cover`/`stream_icon` at a private image
 * server (a Plex `:32400` transcode URL is common) that is firewalled from the
 * public internet. Every poster then fails with a connection error, and a
 * 3-column grid scrolling through thousands of series would open a new socket
 * per cell. After the first connection failure the host is marked unreachable
 * for [retryAfterMillis]; requests to it fail immediately, without network,
 * until then. The first request after the window probes again, and a success
 * clears the mark.
 *
 * Only connection-level failures count (refused, no route, connect timeout).
 * HTTP errors (404 for one missing image) and DNS failures (typical when the
 * phone itself is offline) never mark a host: they say nothing about the host
 * being dead for everybody.
 *
 * No URL is ever logged: artwork URLs can embed provider tokens.
 */
class ImageHostHealth(
    private val retryAfterMillis: Long = DEFAULT_RETRY_AFTER_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) : Interceptor {

    private val deadUntil = ConcurrentHashMap<String, Long>()
    private val _unreachableHosts = MutableStateFlow<Set<String>>(emptySet())

    /** Host keys ([artworkHostKey]) whose last connection attempt failed. */
    val unreachableHosts: StateFlow<Set<String>> = _unreachableHosts.asStateFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        val key = hostKey(chain.request().url)
        val until = deadUntil[key]
        if (until != null && clock() < until) throw UnreachableImageHostException()
        val response = try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            if (isConnectionFailure(e)) markDead(key)
            throw e
        }
        if (deadUntil.remove(key) != null) publish()
        return response
    }

    private fun markDead(key: String) {
        deadUntil[key] = clock() + retryAfterMillis
        publish()
    }

    private fun publish() {
        _unreachableHosts.value = deadUntil.keys.toSet()
    }

    companion object {
        const val DEFAULT_RETRY_AFTER_MILLIS = 5 * 60_000L

        internal fun hostKey(url: HttpUrl): String = "${url.host}:${url.port}"

        /** The key [unreachableHosts] uses for [url], or `null` when it is not http(s). */
        fun artworkHostKey(url: String?): String? =
            url?.toHttpUrlOrNull()?.let(::hostKey)

        internal fun isConnectionFailure(e: IOException): Boolean {
            val message = e.message.orEmpty()
            // The phone itself has no network: not the host's fault.
            if (message.contains("ENETUNREACH") || message.contains("Network is unreachable")) {
                return false
            }
            return when (e) {
                is ConnectException, is NoRouteToHostException -> true
                is SocketTimeoutException -> message.contains("connect", ignoreCase = true)
                else -> false
            }
        }
    }
}

/** Thrown without touching the network for a host known to refuse connections. */
class UnreachableImageHostException : IOException("Image host unreachable (recently refused)")

/**
 * How many [posterUrls] point at an [unreachable] host. Pure, so the notice can
 * be tested on the JVM; the host is parsed once per distinct URL prefix-free
 * string, which is cheap even for tens of thousands of titles.
 */
fun countUnreachablePosters(posterUrls: Sequence<String?>, unreachable: Set<String>): Int {
    if (unreachable.isEmpty()) return 0
    var count = 0
    for (url in posterUrls) {
        val key = ImageHostHealth.artworkHostKey(url) ?: continue
        if (key in unreachable) count++
    }
    return count
}
