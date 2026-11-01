package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistParserTest {

    @Test
    fun parsesAttributesCommasAndClassification() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="id1" tvg-name="LaLiga" tvg-logo="http://logo/x.png" group-title="ES - FUTBOL",M+ LaLiga, FHD
            http://host/live/u/p/1.m3u8
            #EXTINF:-1 tvg-id="id2" group-title="ESPAÑA - Drama",Película, con coma
            http://host/movie/u/p/2.mp4
            #EXTINF:-1 group-title="Series",Serie
            http://host/series/u/p/3.mkv
        """.trimIndent()

        val playlist = PlaylistParser.parse(m3u)

        assertEquals(3, playlist.channels.size)
        assertEquals(SourceType.LIVE, playlist.channels[0].sourceType)
        assertEquals("M+ LaLiga, FHD", playlist.channels[0].name)
        assertEquals("ES - FUTBOL", playlist.channels[0].group)
        assertEquals(SourceType.MOVIE, playlist.channels[1].sourceType)
        assertEquals(SourceType.SERIES, playlist.channels[2].sourceType)
        assertEquals("tvg:id1", playlist.channels[0].favoriteKey)
        assertEquals("tvg:id2", playlist.channels[1].favoriteKey)
        assertTrue(playlist.channels[2].favoriteKey.startsWith("url:"))
        assertEquals(1, playlist.count(SourceType.LIVE))
        assertEquals(1, playlist.count(SourceType.MOVIE))
        assertEquals(1, playlist.count(SourceType.SERIES))
    }

    @Test
    fun resolvesRelativeUrlsAgainstPlaylistUrl() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Canal
            canal.m3u8
        """.trimIndent()

        val playlist = PlaylistParser.parse(m3u, baseUrl = "http://host/lists/list.m3u")

        assertEquals("http://host/lists/canal.m3u8", playlist.channels.single().url)
    }

    @Test
    fun usesExtGrpAsFallbackGroup() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Deportes
            #EXTGRP:ES - DEPORTES
            http://host/stream/1
        """.trimIndent()

        val playlist = PlaylistParser.parse(m3u)
        val channel = playlist.channels.single()

        assertEquals("ES - DEPORTES", channel.group)
        assertEquals(SourceType.LIVE, channel.sourceType)
    }

    @Test
    fun handlesBomAndCrlf() {
        val m3u = "\uFEFF#EXTM3U\r\n#EXTINF:-1,Canal\r\nhttp://host/live/u/p/1.m3u8\r\n"

        val playlist = PlaylistParser.parse(m3u)

        assertEquals(1, playlist.channels.size)
        assertEquals("Canal", playlist.channels.single().name)
    }

    @Test
    fun keepsCommasInsideQuotedAttributes() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="A, B" tvg-logo="http://logo/a,b.png",Canal
            http://host/live/u/p/1.m3u8
        """.trimIndent()

        val playlist = PlaylistParser.parse(m3u)
        val channel = playlist.channels.single()

        assertEquals("A, B", channel.group)
        assertEquals("Canal", channel.name)
        assertEquals("http://logo/a,b.png", channel.logoUrl)
    }

    @Test
    fun variantsWithSameTvgIdShareFavoriteKey() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="laliga" group-title="ES - FUTBOL",M+ LaLiga FHD
            http://host/live/u/p/1.m3u8
            #EXTINF:-1 tvg-id="laliga" group-title="ES - FUTBOL",M+ LaLiga HEVC
            http://host/live/u/p/2.m3u8
        """.trimIndent()

        val playlist = PlaylistParser.parse(m3u)

        assertEquals(playlist.channels[0].favoriteKey, playlist.channels[1].favoriteKey)
    }

    @Test
    fun normalizesSearchText() {
        assertEquals("futbol", SearchNormalizer.key("Fútbol"))
        assertEquals("m laliga tv", SearchNormalizer.key("M+ LaLiga TV"))
        assertEquals("accion", SearchNormalizer.query("  ACCIÓN  "))
    }

    @Test
    fun ignoresCommentsAndUrlsWithoutExtInf() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Canal
            http://host/live/u/p/1.m3u8
            #EXTVLCOPT:http-user-agent=Test
            http://host/no-extinf
        """.trimIndent()

        val playlist = PlaylistParser.parse(m3u)

        assertEquals(1, playlist.channels.size)
        assertTrue(playlist.warnings.isNotEmpty())
    }
}
