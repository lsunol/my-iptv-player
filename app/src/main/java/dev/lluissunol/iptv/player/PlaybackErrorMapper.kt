package dev.lluissunol.iptv.player

import androidx.media3.common.PlaybackException
import dev.lluissunol.iptv.R
import dev.lluissunol.iptv.i18n.UiText
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource

@androidx.annotation.OptIn(UnstableApi::class)
fun PlaybackException.userMessage(): UiText =
    playbackMessage(errorCode, errorCodeName, findHttpCause(this)?.responseCode)

/** Pure mapping, kept apart from [PlaybackException] so it runs in JVM unit tests. */
internal fun playbackMessage(errorCode: Int, errorCodeName: String, httpCode: Int?): UiText {
    if (httpCode != null) {
        val code = httpCode
        return when (code) {
            401, 403 -> UiText.of(R.string.player_err_auth, code)
            404 -> UiText.of(R.string.player_err_not_found)
            in 500..599 -> UiText.of(R.string.player_err_server, code)
            else -> UiText.of(R.string.player_err_http_other, code)
        }
    }

    return when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            UiText.of(R.string.player_err_timeout)
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
            UiText.of(R.string.player_err_no_connection)
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
            UiText.of(R.string.player_err_format)
        // Seen on a real panel: the VOD redirect answers an HTML "Server
        // Error" page (with a 2xx status), so the extractor finds no video.
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            UiText.of(R.string.player_err_no_video)
        PlaybackException.ERROR_CODE_DECODING_FAILED ->
            UiText.of(R.string.player_err_codec)
        else ->
            UiText.of(R.string.player_err_generic, errorCodeName)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun findHttpCause(error: Throwable?): HttpDataSource.InvalidResponseCodeException? {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 6) {
        if (current is HttpDataSource.InvalidResponseCodeException) return current
        current = current.cause
        depth++
    }
    return null
}
