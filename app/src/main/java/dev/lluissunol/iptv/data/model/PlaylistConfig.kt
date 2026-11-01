package dev.lluissunol.iptv.data.model

import java.util.UUID

/** How the user configured a playlist. */
enum class PlaylistKind {
    /** A ready-made M3U/M3U8 (or Xtream `get.php`) URL pasted by the user. */
    URL,

    /** Xtream credentials: server + username + password. */
    XTREAM
}

/**
 * One configured playlist. The credential fields are persisted on-device only
 * (app-private DataStore, backups disabled) and must never be logged or shown in
 * the UI: use [displayHost] for anything user-visible.
 *
 * [lastLoadedAt] is the moment the last successful load finished; it is kept
 * next to the config so each list can show its own "actualizada ..." stamp.
 */
data class PlaylistConfig(
    val id: String,
    val name: String,
    val kind: PlaylistKind,
    val url: String? = null,
    val server: String? = null,
    val username: String? = null,
    val password: String? = null,
    val lastLoadedAt: Long? = null
) {
    /** The URL actually fetched for this list, or null when the config is incomplete. */
    val effectiveUrl: String?
        get() = when (kind) {
            PlaylistKind.URL -> url?.trim()?.takeIf { it.isNotEmpty() }
            PlaylistKind.XTREAM -> buildXtreamUrl(server, username, password)
        }

    /** Host (and port) only, with userinfo stripped. Safe to render/log. */
    val displayHost: String?
        get() {
            val raw = when (kind) {
                PlaylistKind.URL -> url
                PlaylistKind.XTREAM -> server
            }?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val host = raw.substringAfter("://", raw)
                .substringBefore('/')
                .substringAfterLast('@')
            return host.takeIf { it.isNotBlank() }
        }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Xtream URL construction.
 *
 * Template: `<server>/get.php?username=U&password=P&type=m3u_plus&output=m3u8`
 *
 * - The scheme is kept when the user typed one, otherwise `http://` is added
 *   (cleartext is intentional: user-supplied IPTV providers commonly only serve
 *   plain HTTP, see `usesCleartextTraffic`).
 * - Only the username and password are percent-encoded; the server path is
 *   passed through untouched so `http://host:8080/portal` keeps working.
 * - `output=m3u8` asks Xtream for HLS channel URLs.
 */
fun buildXtreamUrl(server: String?, username: String?, password: String?): String? {
    val base = xtreamBaseUrl(server) ?: return null
    val user = encodeCredential(username.orEmpty())
    val pass = encodeCredential(password.orEmpty())
    return "$base/get.php?username=$user&password=$pass&type=m3u_plus&output=m3u8"
}

/** `server` normalized to `scheme://host[:port][/path]` without a trailing slash. */
fun xtreamBaseUrl(server: String?): String? {
    val raw = server?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val withScheme = if (raw.startsWith("http://", ignoreCase = true) ||
        raw.startsWith("https://", ignoreCase = true)
    ) {
        raw
    } else {
        "http://$raw"
    }
    val base = withScheme.trimEnd('/')
    val host = base.substringAfter("://", "").substringBefore('/')
    return base.takeIf { host.isNotBlank() }
}

/** True when the server field was filled with a full `get.php` URL by mistake. */
fun looksLikeFullXtreamUrl(server: String?): Boolean =
    server?.contains("get.php", ignoreCase = true) == true

private const val HEX = "0123456789ABCDEF"

/** RFC 3986 percent-encoding of a single query value (no `+` for spaces). */
private fun encodeCredential(value: String): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val builder = StringBuilder(bytes.size)
    for (byte in bytes) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        val unreserved = (char in 'a'..'z') || (char in 'A'..'Z') || (char in '0'..'9') ||
            char == '-' || char == '.' || char == '_' || char == '~'
        if (unreserved) {
            builder.append(char)
        } else {
            builder.append('%')
                .append(HEX[(code shr 4) and 0xF])
                .append(HEX[code and 0xF])
        }
    }
    return builder.toString()
}
