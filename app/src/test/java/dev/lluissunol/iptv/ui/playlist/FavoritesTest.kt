package dev.lluissunol.iptv.ui.playlist

import dev.lluissunol.iptv.data.model.Channel
import dev.lluissunol.iptv.data.model.Playlist
import dev.lluissunol.iptv.data.model.SeriesCard
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.VodCategory
import dev.lluissunol.iptv.data.model.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure derivation behind the Favorites screen. The interesting cases are
 * the catalogue-only favourites: a series has no single stream URL, and an
 * unmatched movie's fallback URL is absent from the M3U, so neither can come
 * out of the channel list.
 */
class FavoritesTest {

    private fun channel(id: String, key: String, name: String = id) = Channel(
        id = id,
        name = name,
        url = "http://fake.example/live/u/p/$id.m3u8",
        group = "ES - TV",
        logoUrl = null,
        tvgId = null,
        sourceType = SourceType.LIVE,
        searchKey = name.lowercase(),
        qualityTag = null,
        favoriteKey = key
    )

    private val live = channel("c1", "tvg:1", "Canal")
    /** Same `tvg-id`, different name: the v1 "keep every variant" rule. */
    private val liveHd = channel("c2", "tvg:1", "Canal HD")
    private val matchedMovieChannel = channel("c3", "tvg:movie-7", "Peli")
    private val episodeChannel = channel("c4", "tvg:ep-9", "Serie S01E01")

    private val matchedMovie = VodItem(
        streamId = 7,
        name = "Peli",
        posterUrl = null,
        categoryId = "5",
        rating = 0.0,
        searchKey = "peli",
        favoriteKey = "tvg:movie-7",
        playbackUrl = "http://fake.example/movie/u/p/7.m3u8"
    )

    private val unmatchedMovie = VodItem(
        streamId = 12,
        name = "Peli sin M3U",
        posterUrl = null,
        categoryId = "5",
        rating = 0.0,
        searchKey = "peli sin m3u",
        favoriteKey = "url:deadbeef",
        playbackUrl = "http://fake.example/movie/u/p/12.m3u8"
    )

    private val series = SeriesCard(
        seriesId = 2,
        name = "Serie",
        posterUrl = null,
        plot = null,
        categoryId = "3",
        rating = 0.0,
        searchKey = "serie",
        favoriteKey = "series:2"
    )

    private val playlist = Playlist(
        channels = listOf(live, liveHd, matchedMovieChannel, episodeChannel),
        groups = emptyList(),
        warnings = emptyList(),
        bytes = 10,
        counts = mapOf(SourceType.LIVE to 4)
    )

    private val catalog = VodCatalog(
        movies = listOf(matchedMovie, unmatchedMovie),
        series = listOf(series),
        movieCategories = listOf(VodCategory("5", "Acción")),
        seriesCategories = listOf(VodCategory("3", "Drama"))
    )

    @Test
    fun noKeysMeansNoRows() {
        assertTrue(buildFavorites(playlist, catalog, emptySet()).isEmpty())
        assertTrue(buildFavorites(null, null, emptySet()).isEmpty())
    }

    @Test
    fun channelsKeepEveryVariantThatSharesTheKey() {
        val favorites = buildFavorites(playlist, catalog, setOf("tvg:1"))
        assertEquals(listOf("c1", "c2"), favorites.map { it.rowKey })
        assertTrue(favorites.all { it is ChannelFavorite })
    }

    @Test
    fun aStarredSeriesAppearsWithoutAnyChannelBehindIt() {
        val favorites = buildFavorites(playlist, catalog, setOf("series:2"))
        assertEquals(1, favorites.size)
        val entry = favorites.single()
        assertTrue(entry is SeriesFavorite)
        assertEquals("series:2", entry.favoriteKey)
        assertEquals("series:2", entry.rowKey)
        assertEquals(SourceType.SERIES, entry.sourceType)
        assertEquals("Serie", (entry as SeriesFavorite).card.name)
    }

    @Test
    fun anUnmatchedMovieIsPlayableAndUnStarrable() {
        val favorites = buildFavorites(playlist, catalog, setOf("url:deadbeef"))
        val entry = favorites.single()
        assertTrue(entry is ChannelFavorite)
        assertEquals(SourceType.MOVIE, entry.sourceType)
        // Same key the grid cell starred with, so the star can be cleared here.
        assertEquals("url:deadbeef", entry.favoriteKey)
        assertEquals(
            "http://fake.example/movie/u/p/12.m3u8",
            (entry as ChannelFavorite).channel.url
        )
    }

    @Test
    fun aMatchedMovieIsNotListedTwice() {
        val favorites = buildFavorites(playlist, catalog, setOf("tvg:movie-7"))
        assertEquals(listOf("c3"), favorites.map { it.rowKey })
    }

    @Test
    fun everyKindOfFavouriteCoexistsInOneList() {
        val favorites = buildFavorites(
            playlist,
            catalog,
            setOf("tvg:1", "tvg:movie-7", "url:deadbeef", "series:2")
        )
        assertEquals(listOf("c1", "c2", "c3", "movie:12", "series:2"), favorites.map { it.rowKey })
        assertEquals(
            listOf(SourceType.LIVE, SourceType.LIVE, SourceType.LIVE, SourceType.MOVIE, SourceType.SERIES),
            favorites.map { it.sourceType }
        )
        // Row keys are unique, which is what the LazyColumn needs.
        assertEquals(favorites.size, favorites.map { it.rowKey }.toSet().size)
    }

    /**
     * Documents the known gap recorded in `docs/ROADMAP.md`: an episode whose
     * stream is not in the M3U keeps a `url:<hash>` key that no channel and no
     * catalogue row carries, so it cannot be listed here yet.
     */
    @Test
    fun anUnmatchedEpisodeKeyStillShowsNothing() {
        assertTrue(buildFavorites(playlist, catalog, setOf("url:cafe1234")).isEmpty())
    }

    @Test
    fun plainM3uListsKeepWorkingWithoutACatalogue() {
        val favorites = buildFavorites(playlist, null, setOf("tvg:1"))
        assertEquals(2, favorites.size)
    }
}
