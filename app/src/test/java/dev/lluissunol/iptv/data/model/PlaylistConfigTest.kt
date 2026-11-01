package dev.lluissunol.iptv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistConfigTest {

    @Test
    fun buildsXtreamUrlWithDefaultCleartextScheme() {
        assertEquals(
            "http://proveedor.com/get.php?username=usuario&password=clave&type=m3u_plus&output=m3u8",
            buildXtreamUrl("proveedor.com", "usuario", "clave")
        )
    }

    @Test
    fun keepsExplicitSchemeAndPortAndDropsTrailingSlash() {
        assertEquals(
            "https://proveedor.com:8443/get.php?username=u&password=p&type=m3u_plus&output=m3u8",
            buildXtreamUrl("https://proveedor.com:8443/", "u", "p")
        )
        assertEquals(
            "http://192.168.1.50:8080/get.php?username=u&password=p&type=m3u_plus&output=m3u8",
            buildXtreamUrl("  http://192.168.1.50:8080  ", "u", "p")
        )
    }

    @Test
    fun keepsServerPathUntouched() {
        assertEquals(
            "http://proveedor.com/portal/get.php?username=u&password=p&type=m3u_plus&output=m3u8",
            buildXtreamUrl("proveedor.com/portal", "u", "p")
        )
    }

    @Test
    fun encodesOnlyUsernameAndPassword() {
        assertEquals(
            "http://proveedor.com/get.php?username=a%20b&password=p%26w%3Dd&type=m3u_plus&output=m3u8",
            buildXtreamUrl("proveedor.com", "a b", "p&w=d")
        )
        assertEquals(
            "http://proveedor.com/get.php?username=se%C3%B1al&password=a%2Bb%23c&type=m3u_plus&output=m3u8",
            buildXtreamUrl("proveedor.com", "señal", "a+b#c")
        )
    }

    @Test
    fun returnsNullWhenServerIsUnusable() {
        assertNull(buildXtreamUrl(null, "u", "p"))
        assertNull(buildXtreamUrl("   ", "u", "p"))
        assertNull(buildXtreamUrl("http://", "u", "p"))
        assertNull(buildXtreamUrl("https:///", "u", "p"))
    }

    @Test
    fun detectsAFullGetPhpUrlPastedInTheServerField() {
        assertTrue(looksLikeFullXtreamUrl("http://proveedor.com/get.php?username=u&password=p"))
        assertFalse(looksLikeFullXtreamUrl("http://proveedor.com:8080"))
        assertFalse(looksLikeFullXtreamUrl("proveedor.com"))
    }

    @Test
    fun effectiveUrlFollowsTheConfiguredKind() {
        val byUrl = PlaylistConfig(
            id = "1",
            name = "Lista",
            kind = PlaylistKind.URL,
            url = "http://proveedor.com/list.m3u"
        )
        assertEquals("http://proveedor.com/list.m3u", byUrl.effectiveUrl)

        val byXtream = PlaylistConfig(
            id = "2",
            name = "Xtream",
            kind = PlaylistKind.XTREAM,
            server = "proveedor.com",
            username = "u",
            password = "p"
        )
        assertEquals(
            "http://proveedor.com/get.php?username=u&password=p&type=m3u_plus&output=m3u8",
            byXtream.effectiveUrl
        )
    }

    @Test
    fun displayHostNeverContainsCredentials() {
        val xtream = PlaylistConfig(
            id = "1",
            name = "X",
            kind = PlaylistKind.XTREAM,
            server = "http://proveedor.com:8080",
            username = "u",
            password = "p"
        )
        assertEquals("proveedor.com:8080", xtream.displayHost)

        val url = PlaylistConfig(
            id = "2",
            name = "U",
            kind = PlaylistKind.URL,
            url = "http://user:pass@proveedor.com/list.m3u?username=u&password=p"
        )
        assertEquals("proveedor.com", url.displayHost)
    }
}
