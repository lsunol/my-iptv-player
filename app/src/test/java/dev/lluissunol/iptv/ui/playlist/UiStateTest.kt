package dev.lluissunol.iptv.ui.playlist

import dev.lluissunol.iptv.data.VodLayout
import dev.lluissunol.iptv.data.model.Channel
import dev.lluissunol.iptv.data.model.Playlist
import dev.lluissunol.iptv.data.model.SeriesCard
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.VodCategory
import dev.lluissunol.iptv.data.model.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure state decisions behind the poster grid: which tab the catalogue
 * drives, the counts the header and the tab pills show, and which provider
 * categories belong to the active tab. No Android or Compose involved.
 */
class UiStateTest {

    private val movie = VodItem(
        streamId = 1,
        name = "Peli",
        posterUrl = null,
        categoryId = "5",
        rating = 0.0,
        searchKey = "peli",
        favoriteKey = "url:1",
        playbackUrl = "http://fake.example/movie/u/p/1.m3u8"
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

    private val catalog = VodCatalog(
        movies = listOf(movie),
        series = listOf(series),
        movieCategories = listOf(VodCategory("5", "Acción")),
        seriesCategories = listOf(VodCategory("3", "Drama"))
    )

    private val channel = Channel(
        id = "c1",
        name = "Canal",
        url = "http://fake.example/live/u/p/1.m3u8",
        group = "ES - TV",
        logoUrl = null,
        tvgId = null,
        sourceType = SourceType.LIVE,
        searchKey = "canal",
        qualityTag = null,
        favoriteKey = "tvg:c1"
    )

    private val playlist = Playlist(
        channels = listOf(channel),
        groups = emptyList(),
        warnings = emptyList(),
        bytes = 10,
        counts = mapOf(SourceType.LIVE to 1, SourceType.MOVIE to 7, SourceType.SERIES to 9)
    )

    private fun state(
        tab: SourceType?,
        catalog: VodCatalog? = this.catalog,
        searchAll: Boolean = false,
        channels: List<Channel> = emptyList(),
        movies: List<VodItem> = emptyList(),
        series: List<SeriesCard> = emptyList()
    ) = UiState(
        playlist = playlist,
        catalog = catalog,
        tab = tab,
        searchAll = searchAll,
        filteredChannels = channels,
        filteredMovies = movies,
        filteredSeries = series
    )

    @Test
    fun catalogueDrivesOnlyTheMoviesAndSeriesTabs() {
        assertTrue(state(SourceType.MOVIE).isCatalogTab)
        assertTrue(state(SourceType.SERIES).isCatalogTab)
        assertFalse(state(SourceType.LIVE).isCatalogTab)
        assertFalse(state(null).isCatalogTab)
        // No catalogue (plain M3U or degraded API): never a grid.
        assertFalse(state(SourceType.MOVIE, catalog = null).isCatalogTab)
        // Cross-type search uses the flat list; the catalogue is per type.
        assertFalse(state(SourceType.MOVIE, searchAll = true).isCatalogTab)
    }

    @Test
    fun displayedCountFollowsTheActiveContent() {
        assertEquals(
            3,
            state(SourceType.MOVIE, movies = listOf(movie, movie, movie)).displayedCount
        )
        assertEquals(
            2,
            state(SourceType.SERIES, series = listOf(series, series)).displayedCount
        )
        // Flat list keeps counting channels.
        assertEquals(
            1,
            state(SourceType.LIVE, channels = listOf(channel)).displayedCount
        )
        assertTrue(state(SourceType.MOVIE).contentIsEmpty)
        assertFalse(state(SourceType.MOVIE, movies = listOf(movie)).contentIsEmpty)
    }

    @Test
    fun tabCountsPreferTheCatalogueWhenItIsLoaded() {
        assertEquals(1, state(SourceType.MOVIE).tabCount(SourceType.MOVIE))
        assertEquals(1, state(SourceType.SERIES).tabCount(SourceType.SERIES))
        // Without a catalogue the parsed M3U counts stay authoritative.
        assertEquals(7, state(SourceType.MOVIE, catalog = null).tabCount(SourceType.MOVIE))
        assertEquals(9, state(SourceType.SERIES, catalog = null).tabCount(SourceType.SERIES))
        // Live and All are always the flat list.
        assertEquals(1, state(SourceType.MOVIE).tabCount(SourceType.LIVE))
        assertEquals(1, state(SourceType.MOVIE).tabCount(null))
    }

    @Test
    fun categoriesFollowTheActiveTab() {
        assertEquals(listOf(VodCategory("5", "Acción")), state(SourceType.MOVIE).vodCategories)
        assertEquals(listOf(VodCategory("3", "Drama")), state(SourceType.SERIES).vodCategories)
        assertTrue(state(SourceType.LIVE).vodCategories.isEmpty())
        assertTrue(state(SourceType.MOVIE, catalog = null).vodCategories.isEmpty())
    }

    @Test
    fun layoutDefaultsToTheGridAndIsPartOfTheState() {
        assertEquals(VodLayout.GRID, UiState().vodLayout)
        assertEquals(VodLayout.LIST, state(SourceType.MOVIE).copy(vodLayout = VodLayout.LIST).vodLayout)
    }
}
