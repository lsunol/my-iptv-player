package dev.lluissunol.iptv.i18n

import dev.lluissunol.iptv.R
import dev.lluissunol.iptv.data.PlaylistException
import dev.lluissunol.iptv.data.PlaylistRepository
import dev.lluissunol.iptv.data.userMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Failures map to resource ids plus numeric args only: never a URL or credential. */
class UiTextMessagesTest {
    @Test fun httpStatusesMapToTheirResource() {
        assertEquals(UiText.of(R.string.err_http_auth, 401), PlaylistException.Http(401).userMessage())
        assertEquals(UiText.of(R.string.err_http_auth, 403), PlaylistException.Http(403).userMessage())
        assertEquals(UiText.of(R.string.err_http_not_found), PlaylistException.Http(404).userMessage())
        assertEquals(UiText.of(R.string.err_http_server, 503), PlaylistException.Http(503).userMessage())
        assertEquals(UiText.of(R.string.err_http_other, 418), PlaylistException.Http(418).userMessage())
    }

    @Test fun networkCausesMapToTheirResource() {
        assertEquals(
            UiText.of(R.string.err_timeout),
            PlaylistException.Network(SocketTimeoutException("x")).userMessage()
        )
        assertEquals(
            UiText.of(R.string.err_unknown_host),
            PlaylistException.Network(UnknownHostException("secret.example")).userMessage()
        )
        assertEquals(UiText.of(R.string.err_connect), PlaylistException.Network(IOException("x")).userMessage())
    }

    @Test fun unknownNeverExposesTheCauseMessage() {
        val text = PlaylistException.Unknown(IOException("http://host/u/p/1.m3u8")).userMessage()
        assertEquals(UiText.of(R.string.err_load_failed), text)
    }

    @Test fun tooLargeCarriesOnlySizes() {
        val limit = PlaylistRepository.MAX_BYTES / 1_000_000
        assertEquals(
            UiText.of(R.string.err_too_large, 250L, limit),
            PlaylistException.TooLarge(250_000_000L).userMessage()
        )
    }

    @Test fun simpleKinds() {
        assertEquals(UiText.of(R.string.err_invalid_url), PlaylistException.InvalidUrl().userMessage())
        assertEquals(UiText.of(R.string.err_html), PlaylistException.Html().userMessage())
        assertEquals(UiText.of(R.string.err_empty), PlaylistException.Empty().userMessage())
        assertEquals(UiText.of(R.string.err_malformed), PlaylistException.Malformed().userMessage())
        assertEquals(UiText.of(R.string.err_out_of_memory), PlaylistException.OutOfMemory().userMessage())
    }
}
