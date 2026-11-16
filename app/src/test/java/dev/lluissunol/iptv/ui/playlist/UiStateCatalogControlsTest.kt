package dev.lluissunol.iptv.ui.playlist

import dev.lluissunol.iptv.data.CatalogSort
import dev.lluissunol.iptv.data.LanguageOption
import dev.lluissunol.iptv.data.PanelFixtures
import dev.lluissunol.iptv.data.PanelFixtures.Variant
import dev.lluissunol.iptv.data.SearchNormalizer
import dev.lluissunol.iptv.data.buildCatalogIndex
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.VodCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The state getters behind the results toolbar, the filter sheet and the category chips. */
class UiStateCatalogControlsTest {

    private fun state(
        variant: Variant = Variant.MEASURED,
        tab: SourceType? = SourceType.MOVIE,
        catalog: VodCatalog = PanelFixtures.catalog(variant)
    ) = UiState(
        catalog = catalog,
        catalogIndex = buildCatalogIndex(catalog),
        tab = tab
    )

    @Test
    fun tabIndexFollowsTheActiveTab() {
        val s = state()
        assertEquals(s.catalogIndex!!.movies, s.tabIndex)
        assertEquals(s.catalogIndex!!.series, s.copy(tab = SourceType.SERIES).tabIndex)
        assertNull(s.copy(tab = SourceType.LIVE).tabIndex)
        assertNull(s.copy(tab = null).tabIndex)
        assertNull(UiState(tab = SourceType.MOVIE).tabIndex)
    }

    @Test
    fun currentSortIsPerTab() {
        val s = state().copy(movieSort = CatalogSort.RECENT, seriesSort = CatalogSort.AZ)
        assertEquals(CatalogSort.RECENT, s.currentSort)
        assertEquals(CatalogSort.AZ, s.copy(tab = SourceType.SERIES).currentSort)
        assertEquals(CatalogSort.PROVIDER, s.copy(tab = SourceType.LIVE).currentSort)
    }

    @Test
    fun effectiveLanguageRequiresTheTabToOfferIt() {
        val s = state().copy(catalogLanguage = LanguageOption.ES)
        assertEquals(LanguageOption.ES, s.effectiveLanguage)
        // A language the measured panel does not carry is ignored, not applied.
        assertNull(s.copy(catalogLanguage = LanguageOption.FR).effectiveLanguage)
        // A panel without a language facet ignores the saved preference.
        val bare = state(Variant.BARE).copy(catalogLanguage = LanguageOption.ES)
        assertNull(bare.effectiveLanguage)
        assertEquals(0, bare.activeSheetFilters)
    }

    @Test
    fun activeSheetFiltersCountsOnlyAvailableFilters() {
        val s = state(tab = SourceType.SERIES)
        assertEquals(0, s.activeSheetFilters)
        assertEquals(1, s.copy(catalogLanguage = LanguageOption.EN).activeSheetFilters)
        assertEquals(2, s.copy(catalogLanguage = LanguageOption.EN, minRating7 = true).activeSheetFilters)
        assertEquals(3, s.copy(catalogLanguage = LanguageOption.EN, minRating7 = true, genreFilter = "c:DRAMA").activeSheetFilters)
        // A genre that does not exist on this tab does not count.
        assertEquals(0, s.copy(genreFilter = "c:WESTERN").activeSheetFilters)
        assertNull(s.copy(genreFilter = "c:WESTERN").effectiveGenre)
        // The movies of the measured panel carry no genre at all.
        assertEquals(0, s.copy(tab = SourceType.MOVIE, genreFilter = "c:DRAMA").activeSheetFilters)
        // Rating is a filter only where the catalogue supports it.
        assertFalse(state(Variant.BARE).copy(minRating7 = true).effectiveMinRating)
    }

    @Test
    fun visibleCategoriesAreNarrowedByLanguageWithThePrefixStripped() {
        val s = state()
        assertEquals(14, s.visibleVodCategories.size)
        val spanish = s.copy(catalogLanguage = LanguageOption.ES).visibleVodCategories
        assertEquals(listOf("Acción", "Comedia", "Drama", "Terror", "Familia", "Crimen", "Aventura"), spanish.map { it.name })
        // The id is untouched, so selecting a chip still filters the right category.
        assertEquals((1..7).map { it.toString() }, spanish.map { it.id })
        val english = s.copy(catalogLanguage = LanguageOption.EN).visibleVodCategories
        assertEquals("Action", english.first().name)
        assertEquals((8..14).map { it.toString() }, english.map { it.id })
    }

    @Test
    fun collidingStrippedLabelsKeepEveryFullName() {
        val base = PanelFixtures.catalog(Variant.RICH)
        // `UK - Drama` and `US - Drama` are both English: stripping would give two "Drama" chips.
        val categories = listOf(
            VodCategory("1", "UK - Drama"), VodCategory("2", "US - Drama"),
            VodCategory("3", "ES - Drama"), VodCategory("4", "ES - Comedia")
        )
        val catalog = base.copy(
            movieCategories = categories,
            movies = base.movies.map { it.copy(categoryId = ((it.streamId % 4) + 1).toString()) }
        )
        val s = state(catalog = catalog).copy(catalogLanguage = LanguageOption.EN)
        assertEquals(LanguageOption.EN, s.effectiveLanguage)
        assertEquals(listOf("UK - Drama", "US - Drama"), s.visibleVodCategories.map { it.name })
        // Spanish has no collision, so it is stripped.
        assertEquals(
            listOf("Drama", "Comedia"),
            s.copy(catalogLanguage = LanguageOption.ES).visibleVodCategories.map { it.name }
        )
    }

    @Test
    fun withoutALanguageEveryCategoryShowsItsFullName() {
        val s = state()
        assertEquals(s.vodCategories, s.visibleVodCategories)
        assertTrue(s.vodCategories.first().name.startsWith("ESPAÑA - "))
    }

    @Test
    fun otrosShowsTheUnrecognisedCategories() {
        val s = state(Variant.RICH).copy(catalogLanguage = LanguageOption.OTHER)
        assertEquals(listOf("VOD 4K"), s.visibleVodCategories.map { it.name })
    }

    @Test
    fun outdatedFlagComesFromTheIndex() {
        assertFalse(state().catalogOutdated)
        val old = PanelFixtures.catalog(Variant.MEASURED).copy(schemaVersion = 0)
        assertTrue(state(catalog = old).catalogOutdated)
        assertFalse(UiState().catalogOutdated)
    }

    @Test
    fun seriesGenresUseTheCatalogueTable() {
        val catalog = PanelFixtures.catalog(Variant.MEASURED)
        val card = catalog.series.first { catalog.seriesGenres.getOrNull(it.genreRef) == "Crime /  Drama / Thriller".replace("  ", " ") }
        val s = state(catalog = catalog, tab = SourceType.SERIES).copy(selectedSeries = card)
        assertEquals(3, s.selectedSeriesGenres.size)
        assertTrue(UiState().selectedSeriesGenres.isEmpty())
        // The search key stays the one used for the query (untouched by the new fields).
        assertEquals(SearchNormalizer.key(card.name), card.searchKey)
    }

    @Test
    fun theViewVersionStartsAtZeroAndTheHintStartsHidden() {
        assertEquals(0, UiState().catalogViewVersion)
        // True until the persisted value is read, so the tip never flashes.
        assertTrue(UiState().movieHintSeen)
    }
}
