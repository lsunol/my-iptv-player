package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.model.Channel
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.VodCategory
import dev.lluissunol.iptv.data.model.VodItem
import dev.lluissunol.iptv.data.model.buildVodCatalog
import dev.lluissunol.iptv.data.model.buildXtreamStreamUrl
import dev.lluissunol.iptv.data.model.toChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stream-id matching between the parsed M3U and the `player_api.php`
 * catalogue, plus the merge rules that the plan fixes explicitly:
 * - matched items keep the playlist URL and favorite key verbatim;
 * - unmatched items fall back to the provider's own `.m3u8` pattern;
 * - a `container_extension` URL is NEVER produced.
 *
 * Fixtures use fake credentials and hosts only.
 */
class XtreamStreamMatcherTest {

    private val base = "http://fake.example:8080"

    private fun channel(
        name: String,
        url: String,
        type: SourceType,
        favoriteKey: String
    ): Channel = Channel(
        id = "id-$name",
        name = name,
        url = url,
        group = null,
        logoUrl = null,
        tvgId = null,
        sourceType = type,
        searchKey = "",
        qualityTag = null,
        favoriteKey = favoriteKey
    )

    private val m3uChannels = listOf(
        channel("Canal Directo", "$base/live/user/pass/1.m3u8", SourceType.LIVE, "tvg:live"),
        channel("Película Uno FHD", "$base/movie/user/pass/123.m3u8", SourceType.MOVIE, "tvg:123"),
        channel(
            "Película Uno HEVC",
            "$base/movie/user/pass/123.m3u8",
            SourceType.MOVIE,
            "tvg:123-hevc"
        ),
        channel("Película Dos", "$base/movie/user/pass/456.mkv", SourceType.MOVIE, "tvg:456"),
        channel("Serie S01E01", "$base/series/user/pass/1001.m3u8", SourceType.SERIES, "tvg:1001"),
        // Not an Xtream URL: it must be ignored by the index.
        channel("Externa", "http://otro.example/video.mp4", SourceType.MOVIE, "tvg:ext")
    )

    // ------------------------------------------------------------ id parsing

    @Test
    fun extractsStreamIdsFromXtreamPaths() {
        assertEquals(
            "movie" to "123",
            XtreamStreamMatcher.extractStreamId("$base/movie/user/pass/123.m3u8")
        )
        assertEquals(
            "series" to "1001",
            XtreamStreamMatcher.extractStreamId("$base/series/user/pass/1001.mkv")
        )
        assertEquals(
            "movie" to "789",
            XtreamStreamMatcher.extractStreamId("$base/movie/user/pass/789")
        )
        assertNull(XtreamStreamMatcher.extractStreamId("$base/live/user/pass/1.m3u8"))
        assertNull(XtreamStreamMatcher.extractStreamId("http://otro.example/video.mp4"))
        assertNull(XtreamStreamMatcher.extractStreamId("basura"))
    }

    @Test
    fun normalizesIdsToDigits() {
        assertEquals("123", XtreamStreamMatcher.digitsOnly("ep-123"))
        assertEquals("123", XtreamStreamMatcher.digitsOnly("123"))
        assertEquals("", XtreamStreamMatcher.digitsOnly("abc"))
    }

    // --------------------------------------------------------------- index

    @Test
    fun indexesOnlyMovieAndSeriesUrlsAndPrefersTheFirstVariant() {
        val matcher = XtreamStreamMatcher(m3uChannels)

        assertEquals(2, matcher.movieCount)
        assertEquals(1, matcher.seriesEpisodeCount)

        val movie = matcher.movie(123L)
        assertEquals("$base/movie/user/pass/123.m3u8", movie?.url)
        // A duplicated id keeps the first M3U entry deterministically.
        assertEquals("tvg:123", movie?.favoriteKey)

        assertEquals("$base/movie/user/pass/456.mkv", matcher.movie(456L)?.url)
        assertEquals("$base/series/user/pass/1001.m3u8", matcher.seriesEpisode("1001")?.url)
        assertNull(matcher.movie(999L))
        assertNull(matcher.seriesEpisode("999"))
    }

    // ------------------------------------------------- merge / catalogue

    private val matcher = XtreamStreamMatcher(m3uChannels)

    private val fallback: (String, String) -> String? = { type, id ->
        buildXtreamStreamUrl("fake.example:8080", "user", "pass", type, id)
    }

    private val apiCatalog = XtreamApiCatalog(
        vodStreams = listOf(
            // Matched: keeps the M3U URL and favorite key verbatim.
            VodStreamDto(name = "Película Uno", streamId = 123, categoryId = "5"),
            // Matched by id even though the M3U entry was .mkv: its URL is used
            // verbatim; no container_extension rewriting happens.
            VodStreamDto(name = "Película Dos", streamId = 456, containerExtension = "avi"),
            // Unmatched: falls back to the provider .m3u8 pattern.
            VodStreamDto(name = "Película Nueva", streamId = 789, containerExtension = "mkv"),
            // No usable id: skipped.
            VodStreamDto(name = "Sin id", streamId = 0),
            // No id at all (malformed item survived decode with defaults).
            VodStreamDto(name = "Sin nada")
        ),
        seriesList = listOf(
            SeriesDto(name = "Serie Buena", seriesId = 77, categoryId = "3"),
            SeriesDto(name = "Sin id", seriesId = 0)
        ),
        vodCategories = listOf(
            CategoryDto(categoryId = "5", categoryName = "Acción"),
            CategoryDto(categoryId = "", categoryName = "Sin id")
        ),
        seriesCategories = listOf(
            CategoryDto(categoryId = "3", categoryName = "Drama"),
            CategoryDto(categoryId = "3", categoryName = "Drama duplicado")
        ),
        warnings = emptyList()
    )

    @Test
    fun mergedMoviesResolveUrlsAndFavoriteKeys() {
        val catalog = buildVodCatalog(apiCatalog, matcher, fallback)

        assertEquals(3, catalog.movies.size)
        val uno = catalog.movies[0]
        assertEquals("$base/movie/user/pass/123.m3u8", uno.playbackUrl)
        assertEquals("tvg:123", uno.favoriteKey)

        val dos = catalog.movies[1]
        assertEquals("$base/movie/user/pass/456.mkv", dos.playbackUrl)
        assertEquals("tvg:456", dos.favoriteKey)

        val nueva = catalog.movies[2]
        assertEquals("$base/movie/user/pass/789.m3u8", nueva.playbackUrl)
        // Unmatched items get the same URL-hash key the parser would build.
        assertEquals("url:" + Integer.toHexString(nueva.playbackUrl.hashCode()), nueva.favoriteKey)
    }

    @Test
    fun neverProducesContainerExtensionUrls() {
        val catalog = buildVodCatalog(apiCatalog, matcher, fallback)

        assertTrue(catalog.movies.isNotEmpty())
        catalog.movies.forEach { item ->
            assertTrue(item.playbackUrl.startsWith("http://"))
            assertTrue(item.playbackUrl.endsWith(".m3u8") || item.playbackUrl.endsWith(".mkv"))
        }
        // The only non-.m3u8 URL is the verbatim M3U match (456.mkv); every
        // constructed fallback URL is .m3u8.
        assertTrue(catalog.movies[2].playbackUrl.endsWith(".m3u8"))
    }

    @Test
    fun unmatchedItemsWithoutAFallbackUrlAreSkipped() {
        val catalog = buildVodCatalog(apiCatalog, matcher, fallbackUrl = { _, _ -> null })

        // Only the two matched movies survive.
        assertEquals(listOf(123L, 456L), catalog.movies.map { it.streamId })
    }

    @Test
    fun seriesUseTheirOwnStableFavoriteKeyAndDeduplicatedCategories() {
        val catalog = buildVodCatalog(apiCatalog, matcher, fallback)

        assertEquals(1, catalog.series.size)
        assertEquals("Serie Buena", catalog.series[0].name)
        assertEquals("series:77", catalog.series[0].favoriteKey)
        assertEquals("serie buena", catalog.series[0].searchKey)

        // A category without id is not dropped; its id falls back to the name.
        assertEquals(
            listOf(VodCategory("5", "Acción"), VodCategory("Sin id", "Sin id")),
            catalog.movieCategories
        )
        // Duplicated ids are collapsed to the first occurrence.
        assertEquals(listOf(VodCategory("3", "Drama")), catalog.seriesCategories)
    }

    @Test
    fun favoriteKeysAreStableAcrossASimulatedRefresh() {
        // A refresh re-parses the same M3U and re-fetches the same API: the
        // movie keys must be identical, or stars would silently disappear.
        val first = buildVodCatalog(apiCatalog, XtreamStreamMatcher(m3uChannels), fallback)
        val second = buildVodCatalog(apiCatalog, XtreamStreamMatcher(m3uChannels), fallback)

        assertEquals(
            first.movies.map { it.streamId to it.favoriteKey },
            second.movies.map { it.streamId to it.favoriteKey }
        )
        assertEquals(
            first.series.map { it.favoriteKey },
            second.series.map { it.favoriteKey }
        )
    }

    @Test
    fun repeatedProviderIdsAreCollapsedSoGridKeysStayUnique() {
        // The poster grid keys cells by `movie:<id>` / `series:<id>`, so a
        // provider repeating an id must not reach the LazyGrid: duplicate keys
        // would crash the list.
        val duplicated = apiCatalog.copy(
            vodStreams = apiCatalog.vodStreams + VodStreamDto(
                name = "Película Uno (repetida)",
                streamId = 123,
                categoryId = "5"
            ),
            seriesList = apiCatalog.seriesList + SeriesDto(
                name = "Serie Buena (repetida)",
                seriesId = 77,
                categoryId = "3"
            )
        )

        val catalog = buildVodCatalog(duplicated, matcher, fallback)

        assertEquals(catalog.movies.size, catalog.movies.map { it.streamId }.toSet().size)
        assertEquals(catalog.series.size, catalog.series.map { it.seriesId }.toSet().size)
        // First occurrence wins, like the M3U matcher.
        assertEquals("Película Uno", catalog.movies.first { it.streamId == 123L }.name)
        assertEquals("Serie Buena", catalog.series.first { it.seriesId == 77L }.name)
    }

    @Test
    fun qualityTagsComeFromTheMatchedChannelOrDefaultToTheApiName() {
        // A name with a quality token: the badge is detected from the API name.
        val named = buildVodCatalog(
            apiCatalog.copy(
                vodStreams = listOf(
                    VodStreamDto(name = "Película Nueva FHD", streamId = 789, containerExtension = "mkv")
                ),
                seriesList = emptyList()
            ),
            matcher,
            fallback
        )
        assertEquals("FHD", named.movies.single().qualityTag)

        // The matched M3U channel wins when it carries a tag; the channels of
        // these fixtures carry none, and "Película Uno" has no token either.
        val matched = buildVodCatalog(apiCatalog, matcher, fallback)
        assertNull(matched.movies.first { it.streamId == 123L }.qualityTag)
        assertNull(matched.movies.first { it.streamId == 789L }.qualityTag)
    }

    // ---------------------------------------------------- episode mapping

    @Test
    fun episodesMapToTheChannelShapeThePlayerConsumes() {
        val matched = matcher.seriesEpisode("1001")!!
        val episode = dev.lluissunol.iptv.data.model.Episode(
            id = "1001",
            season = 1,
            episodeNum = 1,
            title = "Piloto",
            playbackUrl = matched.url,
            favoriteKey = matched.favoriteKey
        )

        val channel = episode.toChannel("Serie Buena")

        assertEquals(SourceType.SERIES, channel.sourceType)
        assertEquals("$base/series/user/pass/1001.m3u8", channel.url)
        assertEquals("S01E01 · Piloto", channel.name)
        assertEquals("Serie Buena", channel.group)
        assertEquals(matched.favoriteKey, channel.favoriteKey)
        assertTrue(channel.searchKey.contains("serie buena"))
        assertTrue(channel.searchKey.contains("piloto"))
    }

    @Test
    fun episodeChannelIdsAreReadableAndUniqueWithoutHashes() {
        // Two seasons can repeat the same raw episode id (providers number
        // episodes per season), so the Channel id must combine the season and
        // episode number with the (season-unique) episode id. The old id was a
        // pair of 32-bit hash hex strings, which could collide and left the
        // player keyed on an ambiguous id.
        fun episode(id: String, season: Int, num: Int, url: String) =
            dev.lluissunol.iptv.data.model.Episode(
                id = id,
                season = season,
                episodeNum = num,
                title = "Piloto",
                playbackUrl = url,
                favoriteKey = "tvg:$id"
            )

        val first = episode("7", 1, 1, "$base/series/user/pass/1007.m3u8")
        val second = episode("7", 2, 1, "$base/series/user/pass/2007.m3u8")
        // Same season, same episode number, but the disambiguated ids differ.
        val third = episode("7#2", 1, 1, "$base/series/user/pass/1007b.m3u8")

        val ids = listOf(first, second, third).map { it.toChannel("Serie").id }

        assertEquals("episode:1:1:7", ids[0])
        assertEquals("episode:2:1:7", ids[1])
        assertEquals("episode:1:1:7#2", ids[2])
        // No two episodes share a Channel id: the player and the lists key on it.
        assertEquals(ids.size, ids.toSet().size)
    }

    // ------------------------------------------------------ URL builders

    @Test
    fun buildsTheProviderOwnStreamUrlPattern() {
        assertEquals(
            "$base/movie/user/pass/789.m3u8",
            buildXtreamStreamUrl("fake.example:8080", "user", "pass", "movie", "789")
        )
        assertEquals(
            "$base/series/u%20ser/p%40ss/12.m3u8",
            buildXtreamStreamUrl("http://fake.example:8080/", "u ser", "p@ss", "series", "12")
        )
        assertNull(buildXtreamStreamUrl("   ", "u", "p", "movie", "1"))
    }

    @Test
    fun buildsThePlayerApiUrlWithCredentialsOnlyInTheQuery() {
        assertEquals(
            "$base/player_api.php?username=user&password=pass&action=get_series_info&series_id=77",
            dev.lluissunol.iptv.data.model.buildXtreamApiUrl(
                "fake.example:8080", "user", "pass", "get_series_info&series_id=77"
            )
        )
    }
}
