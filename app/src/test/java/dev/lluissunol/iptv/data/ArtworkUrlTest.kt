package dev.lluissunol.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Artwork shapes seen on real Xtream panels (hosts here are fake). */
class ArtworkUrlTest {
    private val server = "http://panel.example:8080/sub"

    @Test
    fun absoluteUrlIsKept() {
        val url = "http://img.example:32400/photo/:/transcode?width=300&url=/library/1/thumb"
        assertEquals(url, normalizeArtworkUrl("  $url ", server))
        assertEquals("https://cdn.example/t/p/w600/a.jpg", normalizeArtworkUrl("https://cdn.example/t/p/w600/a.jpg", server))
    }

    @Test
    fun phpSerializedRelativePathResolvesAgainstThePanelOrigin() {
        // Real shape: `s:308:/images/<token>.jpeg` (no quotes).
        assertEquals(
            "http://panel.example:8080/images/abc_DEF-1.jpeg",
            normalizeArtworkUrl("s:308:/images/abc_DEF-1.jpeg", server)
        )
        // Canonical PHP form with quotes and terminator.
        assertEquals(
            "http://panel.example:8080/images/x.jpg",
            normalizeArtworkUrl("s:13:\"/images/x.jpg\";", server)
        )
        assertEquals("https://cdn.example/a.jpg", normalizeArtworkUrl("s:25:\"https://cdn.example/a.jpg\";", server))
    }

    @Test
    fun rootRelativeAndProtocolRelativePaths() {
        assertEquals("http://panel.example:8080/images/a.png", normalizeArtworkUrl("/images/a.png", server))
        assertEquals("http://panel.example/i.png", normalizeArtworkUrl("/i.png", "panel.example"))
        assertEquals("http://cdn.example/a.png", normalizeArtworkUrl("//cdn.example/a.png", server))
    }

    @Test
    fun junkBecomesNull() {
        assertNull(normalizeArtworkUrl(null, server))
        assertNull(normalizeArtworkUrl("", server))
        assertNull(normalizeArtworkUrl("   ", server))
        assertNull(normalizeArtworkUrl("[]", server))
        assertNull(normalizeArtworkUrl("s:0:\"\";", server))
        assertNull(normalizeArtworkUrl("images/a.png", server))
        assertNull(normalizeArtworkUrl("/images/a.png", null))
    }
}
