package dev.lluissunol.iptv.player

import androidx.media3.common.PlaybackException
import dev.lluissunol.iptv.R
import dev.lluissunol.iptv.i18n.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorMapperTest {
    private fun msg(code: Int, name: String = "NAME", http: Int? = null) = playbackMessage(code, name, http)

    @Test fun httpStatusesMapToResources() {
        assertEquals(UiText.of(R.string.player_err_auth, 403), msg(0, http = 403))
        assertEquals(UiText.of(R.string.player_err_not_found), msg(0, http = 404))
        assertEquals(UiText.of(R.string.player_err_server, 502), msg(0, http = 502))
        assertEquals(UiText.of(R.string.player_err_http_other, 418), msg(0, http = 418))
    }

    @Test fun errorCodesMapToResources() {
        assertEquals(
            UiText.of(R.string.player_err_timeout),
            msg(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        )
        assertEquals(
            UiText.of(R.string.player_err_no_connection),
            msg(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        )
        assertEquals(
            UiText.of(R.string.player_err_no_video),
            msg(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
        )
        assertEquals(UiText.of(R.string.player_err_codec), msg(PlaybackException.ERROR_CODE_DECODING_FAILED))
    }

    @Test fun unknownCodeCarriesOnlyTheCodeName() {
        assertEquals(
            UiText.of(R.string.player_err_generic, "ERROR_CODE_UNSPECIFIED"),
            msg(PlaybackException.ERROR_CODE_UNSPECIFIED, "ERROR_CODE_UNSPECIFIED")
        )
    }
}
