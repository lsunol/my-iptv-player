package dev.lluissunol.iptv.ui.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The masking runs inside a text field, so it must hide every credential variant
 * and keep the text length identical (the offset mapping is the identity).
 */
class CredentialMaskTest {

    private fun assertMasked(original: String, expected: String) {
        val masked = maskCredentials(original)
        assertEquals(expected, masked)
        assertEquals(
            "masking must not change the text length",
            original.length,
            masked.length
        )
    }

    @Test
    fun masksXtreamQueryParameters() {
        assertMasked(
            "http://proveedor.example/get.php?username=ana&password=secreta&type=m3u_plus&output=m3u8",
            "http://proveedor.example/get.php?username=•••&password=•••••••&type=m3u_plus&output=m3u8"
        )
    }

    @Test
    fun masksShortAndAlternativeParameterNames() {
        assertMasked("http://h/l.m3u?user=ana", "http://h/l.m3u?user=•••")
        assertMasked("http://h/l.m3u?pass=secreta", "http://h/l.m3u?pass=•••••••")
        assertMasked("http://h/l.m3u?u=ana", "http://h/l.m3u?u=•••")
        assertMasked("http://h/l.m3u?p=secreta", "http://h/l.m3u?p=•••••••")
        assertMasked("http://h/l.m3u?token=abc123", "http://h/l.m3u?token=••••••")
        assertMasked("http://h/l.m3u?key=abc123", "http://h/l.m3u?key=••••••")
        assertMasked("http://h/l.m3u?auth=abc123", "http://h/l.m3u?auth=••••••")
    }

    @Test
    fun masksCredentialsInUserInfo() {
        assertMasked(
            "http://ana:secreta@proveedor.example:8080/live/lista.m3u",
            "http://•••:•••••••@proveedor.example:8080/live/lista.m3u"
        )
    }

    @Test
    fun masksBothUserInfoAndQueryParameters() {
        assertMasked(
            "http://ana:secreta@h/lista.m3u?username=ana&password=secreta",
            "http://•••:•••••••@h/lista.m3u?username=•••&password=•••••••"
        )
    }

    @Test
    fun leavesParameterlessUrlsUntouched() {
        assertEquals(
            "http://proveedor.example/live/lista.m3u?type=m3u_plus&output=m3u8",
            maskCredentials("http://proveedor.example/live/lista.m3u?type=m3u_plus&output=m3u8")
        )
        assertEquals(
            "http://proveedor.example:8080/get.php",
            maskCredentials("http://proveedor.example:8080/get.php")
        )
    }

    @Test
    fun neverLeavesTheCredentialValueVisible() {
        val masked = maskCredentials("http://h/l.m3u?user=ana&pass=secreta")
        assertFalse(masked.contains("ana"))
        assertFalse(masked.contains("secreta"))
        assertTrue(masked.contains("user=•••"))
        assertTrue(masked.contains("pass=•••••••"))
    }
}
