package dev.lluissunol.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The stored placeholders feed channel ids and favorite keys, so their values
 * are frozen: changing them would silently reset favorites of nameless entries.
 */
class UnnamedPlaceholderTest {
    @Test fun storedPlaceholdersAreFrozen() {
        assertEquals("Canal sin nombre", UNNAMED_CHANNEL)
        assertEquals("Sin nombre", UNNAMED_TITLE)
    }

    @Test fun seriesPlaceholderCannotCollideWithARealTitle() {
        assertNotEquals("Serie", UNNAMED_SERIES)
        assertEquals(UNNAMED_SERIES, UNNAMED_SERIES.trim())
    }

    @Test fun namelessChannelKeepsTheStoredPlaceholder() {
        val playlist = PlaylistParser.parse("#EXTM3U\n#EXTINF:-1 group-title=\"G\",\nhttp://host/live/u/p/9.m3u8\n")
        assertEquals(UNNAMED_CHANNEL, playlist.channels.single().name)
    }
}
