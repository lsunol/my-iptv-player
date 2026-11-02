package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.Channel
import java.net.URI

/**
 * Indexes the `/movie/` and `/series/` entries of the *parsed M3U* by their
 * Xtream stream id, so `player_api.php` catalogue items can resolve their
 * playback URL to the exact URL the provider's own M3U hands out (which is
 * already HLS when the list was fetched with `output=m3u8`).
 *
 * This is the conservative half of the hybrid pipeline: the API never dictates
 * playback URLs, it only contributes metadata. A `container_extension` URL
 * (`.mkv`, `.avi`, ...) is never produced here.
 */
class XtreamStreamMatcher(channels: List<Channel>) {

    private val moviesById: Map<String, Channel>
    private val seriesEpisodesById: Map<String, Channel>

    init {
        val movies = HashMap<String, Channel>()
        val series = HashMap<String, Channel>()
        for (channel in channels) {
            val (type, id) = extractStreamId(channel.url) ?: continue
            val target = if (type == TYPE_MOVIE) movies else series
            // First entry wins for quality variants that share one id; the M3U
            // is ordered by the provider and keeping the first variant is
            // deterministic across refreshes.
            target.putIfAbsent(id, channel)
        }
        moviesById = movies
        seriesEpisodesById = series
    }

    /** The M3U channel of one movie stream id, or null when it is not in the M3U. */
    fun movie(streamId: Long): Channel? = moviesById[streamId.toString()]

    /** The M3U channel of one series *episode* id (digits only, no extension). */
    fun seriesEpisode(idDigits: String): Channel? = seriesEpisodesById[idDigits]

    val movieCount: Int get() = moviesById.size
    val seriesEpisodeCount: Int get() = seriesEpisodesById.size

    companion object {
        const val TYPE_MOVIE = "movie"
        const val TYPE_SERIES = "series"

        /**
         * Extracts `(type, id)` from an Xtream stream URL such as
         * `http://host/movie/USER/PASS/123.m3u8`. The id is the path segment
         * after the credentials, without its extension; ids are normalized to
         * their digits so `"ep-123"` and `"123"` join with the same entry.
         */
        internal fun extractStreamId(url: String): Pair<String, String>? {
            val segments = try {
                URI(url).path.orEmpty()
            } catch (_: Exception) {
                return null
            }
                .split('/')
                .filter { it.isNotEmpty() }
            val index = segments.indexOfFirst {
                it.equals(TYPE_MOVIE, ignoreCase = true) || it.equals(TYPE_SERIES, ignoreCase = true)
            }
            if (index < 0 || index + 3 >= segments.size) return null
            val rawId = segments[index + 3].substringBefore('.')
            val id = digitsOnly(rawId).ifEmpty { return null }
            return (if (segments[index].equals(TYPE_MOVIE, true)) TYPE_MOVIE else TYPE_SERIES) to id
        }

        /** `"ep-123"` -> `"123"`; keeps only the digits of an id. */
        internal fun digitsOnly(raw: String): String {
            val builder = StringBuilder(raw.length)
            for (char in raw) if (char.isDigit()) builder.append(char)
            return builder.toString()
        }
    }
}
