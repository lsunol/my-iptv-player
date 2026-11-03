package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader

/**
 * The reader entry point of [PlaylistParser]: a playlist must come out
 * identical whether it arrives as an in-memory String or as a character
 * stream chopped into tiny reads, so lines split across buffer boundaries
 * survive.
 */
class PlaylistParserReaderTest {

    @Test
    fun `same output from a String and from a Reader delivering chopped chunks`() {
        val m3u = buildString {
            append("#EXTM3U\r\n")
            append("#EXTINF:-1 tvg-id=\"id1\" tvg-name=\"LaLiga\" tvg-logo=\"http://logo/x.png\" group-title=\"ES - FUTBOL\",M+ LaLiga, FHD\r\n")
            append("http://host/live/u/p/1.m3u8\r\n")
            append("\r\n")
            append("#EXTINF:-1 tvg-id=\"id2\" group-title=\"ESPAÑA - Drama\",Película, con coma\r\n")
            append("http://host/movie/u/p/2.mp4\r\n")
            append("#EXTINF:-1 group-title=\"Series\",Serie\r\n")
            append("#EXTGRP:Series - ES\r\n")
            append("http://host/series/u/p/3.mkv\r\n")
            append("#EXTVLCOPT:http-user-agent=Test\r\n")
            append("#EXT-X-UNKNOWN-PROPERTY\r\n")
            append("#EXTINF:-1,Reemplazado\r\n")
            append("#EXTINF:-1,Canal relativo\r\n")
            append("canal.m3u8\r\n")
            append("http://host/no-extinf\r\n")
        }

        val fromText = PlaylistParser.parse(
            m3u,
            baseUrl = "http://host/lists/list.m3u",
            bytes = m3u.toByteArray(Charsets.UTF_8).size.toLong()
        )
        val fromReader = PlaylistParser.parse(
            chunkedUtf8Reader(m3u.toByteArray(Charsets.UTF_8)),
            baseUrl = "http://host/lists/list.m3u",
            bytes = m3u.toByteArray(Charsets.UTF_8).size.toLong()
        )

        assertEquals(fromText, fromReader)
        // Spot checks so the test does not pass merely because both paths are
        // equally wrong:
        assertEquals(4, fromReader.channels.size)
        assertEquals("M+ LaLiga, FHD", fromReader.channels[0].name)
        assertEquals("ES - FUTBOL", fromReader.channels[0].group)
        assertEquals(SourceType.LIVE, fromReader.channels[0].sourceType)
        assertEquals("Película, con coma", fromReader.channels[1].name)
        assertEquals(SourceType.MOVIE, fromReader.channels[1].sourceType)
        assertEquals("Series - ES", fromReader.channels[2].group)
        assertEquals(SourceType.SERIES, fromReader.channels[2].sourceType)
        // The second #EXTINF before a URL replaced the pending entry.
        assertEquals("Canal relativo", fromReader.channels[3].name)
        assertEquals("http://host/lists/canal.m3u8", fromReader.channels[3].url)
        // Warnings keep their line numbers through the reader path.
        assertEquals(1, fromReader.warnings.size)
        assertEquals("Línea 15: URL sin #EXTINF, ignorada.", fromReader.warnings.single())
    }

    @Test
    fun `handles CRLF and a UTF-8 BOM in the reader path`() {
        val bytes = "\uFEFF#EXTM3U\r\n#EXTINF:-1,Canal\r\nhttp://host/live/u/p/1.m3u8\r\n"
            .toByteArray(Charsets.UTF_8)

        val playlist = PlaylistParser.parse(
            InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8),
            bytes = bytes.size.toLong()
        )

        assertEquals(1, playlist.channels.size)
        assertEquals("Canal", playlist.channels.single().name)
        assertEquals(bytes.size.toLong(), playlist.bytes)
        assertEquals(0, playlist.warnings.size)
    }

    @Test
    fun `reports progress every thousand lines and once at the end`() {
        val m3u = StringBuilder("#EXTM3U\r\n").apply {
            for (i in 1..1500) {
                append("#EXTINF:-1,Canal $i\r\nhttp://host/live/u/p/$i.m3u8\r\n")
            }
        }.toString()

        val progress = mutableListOf<Int>()
        PlaylistParser.parse(m3u) { entries -> progress += entries }

        assertEquals(1500, progress.last())
        // 3001 lines: ticks at 1000, 2000 and 3000, plus the final call.
        assertEquals(4, progress.size)
        // Line 1000 is the #EXTINF of channel 500, whose URL is line 1001.
        assertEquals(499, progress[0])
        assertEquals(999, progress[1])
        assertEquals(1499, progress[2])
    }

    /**
     * A reader whose underlying byte stream returns at most 1, 3, 7, ... bytes
     * per read, so every line is split across several buffer refills.
     */
    private fun chunkedUtf8Reader(bytes: ByteArray): InputStreamReader =
        InputStreamReader(ChunkedInputStream(ByteArrayInputStream(bytes)), Charsets.UTF_8)

    private class ChunkedInputStream(private val source: InputStream) : InputStream() {
        private val chunkSizes = intArrayOf(1, 3, 7, 2, 11, 5)
        private var served = 0

        override fun read(): Int = source.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val limit = chunkSizes[served % chunkSizes.size]
            val count = source.read(buffer, offset, minOf(length, limit))
            if (count > 0) served++
            return count
        }
    }
}
