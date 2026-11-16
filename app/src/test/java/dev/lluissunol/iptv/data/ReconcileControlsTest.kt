package dev.lluissunol.iptv.data

import dev.lluissunol.iptv.data.PanelFixtures.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saved sort/language are checked only against a built, current-schema index,
 * so a valid preference can never be wiped by mistake.
 */
class ReconcileControlsTest {

    private val measured = buildCatalogIndex(PanelFixtures.catalog(Variant.MEASURED))
    private val bare = buildCatalogIndex(PanelFixtures.catalog(Variant.BARE))

    @Test
    fun validChoicesAreKept() {
        val result = reconcileControls(StoredControls("RECENT", "RATING", "es"), measured)
        assertEquals(CatalogSort.RECENT, result.movieSort)
        assertEquals(CatalogSort.RATING, result.seriesSort)
        assertEquals(LanguageOption.ES, result.language)
        assertFalse(result.resetHappened)
        assertFalse(result.writeBack)
    }

    @Test
    fun nothingStoredIsNotAReset() {
        val result = reconcileControls(StoredControls(null, null, null), measured)
        assertEquals(CatalogSort.PROVIDER, result.movieSort)
        assertEquals(CatalogSort.PROVIDER, result.seriesSort)
        assertNull(result.language)
        assertFalse(result.resetHappened)
        assertFalse(result.writeBack)
        // A stored provider order is a valid choice, not a stale one.
        assertFalse(reconcileControls(StoredControls("PROVIDER", "PROVIDER", null), measured).resetHappened)
    }

    @Test
    fun anUnavailableChoiceIsResetAndWrittenBack() {
        // RELEASE does not exist on the measured panel; neither does RECENT for series.
        val result = reconcileControls(StoredControls("RELEASE", "RECENT", "fr"), measured)
        assertEquals(CatalogSort.PROVIDER, result.movieSort)
        assertEquals(CatalogSort.PROVIDER, result.seriesSort)
        assertNull(result.language)
        assertTrue(result.resetHappened)
        assertTrue(result.writeBack)
    }

    @Test
    fun oneInvalidValueDoesNotDiscardTheValidOnes() {
        val result = reconcileControls(StoredControls("RECENT", "RELEASE", "en"), measured)
        assertEquals(CatalogSort.RECENT, result.movieSort)
        assertEquals(CatalogSort.PROVIDER, result.seriesSort)
        assertEquals(LanguageOption.EN, result.language)
        assertTrue(result.resetHappened)
    }

    @Test
    fun unknownEnumStringsAreHandled() {
        val result = reconcileControls(StoredControls("NEWEST_FIRST", "", "klingon"), measured)
        assertEquals(CatalogSort.PROVIDER, result.movieSort)
        assertEquals(CatalogSort.PROVIDER, result.seriesSort)
        assertNull(result.language)
        assertTrue(result.resetHappened)
    }

    @Test
    fun aListWithoutLanguagesResetsAStoredLanguage() {
        val result = reconcileControls(StoredControls("AZ", null, "es"), bare)
        assertEquals(CatalogSort.AZ, result.movieSort)
        assertNull(result.language)
        assertTrue(result.resetHappened)
        assertTrue(result.writeBack)
    }

    @Test
    fun anOutdatedIndexAppliesWhatIsAvailableWithoutWritingBackOrAnnouncing() {
        val catalog = PanelFixtures.catalog(Variant.MEASURED)
        // What an old cache looks like: no schema version and none of the new fields.
        val old = catalog.copy(
            schemaVersion = 0,
            movies = catalog.movies.map { it.copy(added = 0L, releaseKey = 0, genreRef = -1) },
            series = catalog.series.map { it.copy(releaseKey = 0, runtimeMin = 0, genreRef = -1) },
            movieGenres = emptyList(),
            seriesGenres = emptyList()
        )
        val outdated = buildCatalogIndex(old)
        assertTrue(outdated.outdated)
        // The old cache still has ratings (so RATING is valid) but no `added` (RECENT is not).
        val result = reconcileControls(StoredControls("RECENT", "RATING", "es"), outdated)
        assertEquals(CatalogSort.PROVIDER, result.movieSort)
        assertEquals(CatalogSort.RATING, result.seriesSort)
        assertEquals(LanguageOption.ES, result.language)
        assertFalse("the outdated notice covers it", result.resetHappened)
        assertFalse("never wipe a saved preference on an old cache", result.writeBack)
    }

    @Test
    fun theOutdatedMarkerIsTheSchemaVersionNotTheData() {
        val rich = PanelFixtures.catalog(Variant.RICH)
        assertTrue(buildCatalogIndex(rich.copy(schemaVersion = 0)).outdated)
        assertFalse(buildCatalogIndex(rich).outdated)
    }
}
