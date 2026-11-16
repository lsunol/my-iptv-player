package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.xtreamBaseUrl
import java.net.URI

/**
 * Normalizes an artwork URL (`cover`, `stream_icon`, `movie_image`) taken from
 * an Xtream `player_api.php` answer into something an image loader can fetch,
 * or `null` when there is nothing fetchable.
 *
 * Panels send artwork in several broken shapes, all seen on real accounts:
 * - a plain absolute `http(s)://` URL (kept as is);
 * - a PHP-serialized string leaked into the JSON, `s:308:/images/abc.jpeg`
 *   (or `s:12:"/images/x.jpg";`): the `s:<n>:` envelope is stripped;
 * - a root-relative path (`/images/abc.jpeg`), resolved against the panel's
 *   origin ([serverBase] is the list's server, e.g. `http://host:8080/path`);
 * - a protocol-relative `//cdn/x.jpg`, given `http:`;
 * - empty strings, `null`, `"[]"` or other junk, mapped to `null` so the UI
 *   shows its generated placeholder instead of firing a request that cannot
 *   succeed.
 *
 * Pure and Android-free so it is unit-tested on the JVM.
 */
fun normalizeArtworkUrl(raw: String?, serverBase: String?): String? {
    var value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null

    PHP_SERIALIZED.matchEntire(value)?.let { match ->
        value = match.groupValues[1].trim()
    }
    if (value.isEmpty()) return null

    return when {
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("//") -> "http:$value"
        value.startsWith("/") -> {
            val base = xtreamBaseUrl(serverBase) ?: return null
            runCatching { URI(base + "/").resolve(value).toString() }.getOrNull()
        }
        else -> null
    }
}

/** `s:<len>:<payload>` or `s:<len>:"<payload>";` — a PHP-serialized string. */
private val PHP_SERIALIZED = Regex("""^s:\d+:"?(.*?)"?;?$""", RegexOption.DOT_MATCHES_ALL)
