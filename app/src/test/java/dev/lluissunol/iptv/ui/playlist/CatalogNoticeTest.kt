package dev.lluissunol.iptv.ui.playlist

import dev.lluissunol.iptv.data.model.CatalogWarning
import dev.lluissunol.iptv.data.model.PlaylistKind
import dev.lluissunol.iptv.data.model.SeriesCard
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.VodItem
import dev.lluissunol.iptv.data.model.decodeVodCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The notice-selection table of the missing-catalogue fix.
 *
 * This is the only possible verification of the degraded state: on the test
 * device the catalogue cache already exists and reproducing the first-run state
 * would mean wiping the user's configured list, which is off limits. The
 * function under test reads no ViewModel, no DataStore and no resources, so
 * each state of the table can be asserted directly.
 */
class CatalogNoticeTest {

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
        movieCategories = emptyList(),
        seriesCategories = emptyList()
    )

    private fun notice(
        kind: PlaylistKind = PlaylistKind.XTREAM,
        catalog: VodCatalog? = null,
        catalogWarning: CatalogWarning? = null,
        tab: SourceType? = SourceType.MOVIE,
        searchAll: Boolean = false,
        isLoading: Boolean = false
    ) = catalogNotice(
        kind = kind,
        catalog = catalog,
        catalogWarning = catalogWarning,
        tab = tab,
        searchAll = searchAll,
        isLoading = isLoading
    )

    // ------------------------------------------------- the reported defect

    @Test
    fun xtreamCacheStartupWithoutCatalogueOffersARefreshOnMovies() {
        // The defect: startup from the M3U cache, no catalogue file, no
        // failure yet. The tab was flat and silent.
        assertEquals(
            CatalogNotice(CatalogNoticeKind.ACTION_REFRESH, CatalogNoticeText.CatalogMissing),
            notice(tab = SourceType.MOVIE)
        )
    }

    @Test
    fun xtreamCacheStartupWithoutCatalogueOffersARefreshOnSeries() {
        assertEquals(
            CatalogNotice(CatalogNoticeKind.ACTION_REFRESH, CatalogNoticeText.CatalogMissing),
            notice(tab = SourceType.SERIES)
        )
    }

    @Test
    fun corruptCatalogueCacheFallsBackToTheSameActionableNotice() {
        // A corrupt file decodes to null, which is indistinguishable from a
        // missing one; the remedy is the same refresh.
        val decoded = decodeVodCatalog("{no es json")
        assertEquals(null, decoded)
        assertEquals(
            CatalogNotice(CatalogNoticeKind.ACTION_REFRESH, CatalogNoticeText.CatalogMissing),
            notice(catalog = decoded, tab = SourceType.MOVIE)
        )
    }

    // ------------------------------------------------------- untouched tabs

    @Test
    fun liveTabNeverShowsTheNotice() {
        assertEquals(CatalogNotice.None, notice(tab = SourceType.LIVE))
    }

    @Test
    fun allTabNeverShowsTheNotice() {
        assertEquals(CatalogNotice.None, notice(tab = null))
    }

    // -------------------------------------------------- search and refreshing

    @Test
    fun crossTypeSearchSuppressesTheMissingCatalogueNotice() {
        // The flat list is intentional there (it spans every type), so the
        // degradation notice would be noise.
        assertEquals(CatalogNotice.None, notice(searchAll = true, tab = SourceType.MOVIE))
        assertEquals(CatalogNotice.None, notice(searchAll = true, tab = SourceType.SERIES))
    }

    @Test
    fun aRefreshInFlightHidesThePendingNotice() {
        assertEquals(CatalogNotice.None, notice(isLoading = true))
    }

    @Test
    fun aFailedCatalogueStaysVisibleWhileRefreshingButCannotRetryTwice() {
        // The notice explains the list that is still on screen, so it stays;
        // the UI disables the button while `isLoading` (see `NoticeLine`).
        assertEquals(
            CatalogNotice(CatalogNoticeKind.ACTION_REFRESH, CatalogNoticeText.CatalogUnavailable),
            notice(catalogWarning = CatalogWarning.Unavailable, isLoading = true)
        )
    }

    @Test
    fun aFailedCatalogueOffersARetryOnBothCatalogueTabs() {
        for (tab in listOf(SourceType.MOVIE, SourceType.SERIES)) {
            assertEquals(
                CatalogNotice(
                    CatalogNoticeKind.ACTION_REFRESH,
                    CatalogNoticeText.CatalogUnavailable
                ),
                notice(catalogWarning = CatalogWarning.Unavailable, tab = tab)
            )
        }
    }

    // ------------------------------------------------- loaded-catalogue cases

    @Test
    fun aLoadedCatalogueWithAWarningShowsAPassiveNotice() {
        assertEquals(
            CatalogNotice(CatalogNoticeKind.INFO, CatalogNoticeText.SkippedItems(3)),
            notice(catalog = catalog, catalogWarning = CatalogWarning.SkippedItems(3))
        )
    }

    @Test
    fun everyDecodeWarningMapsToItsOwnMessage() {
        val cases = mapOf(
            CatalogWarning.EmptyResponse to CatalogNoticeText.EmptyResponse,
            CatalogWarning.InvalidJson to CatalogNoticeText.InvalidJson,
            CatalogWarning.UnexpectedFormat to CatalogNoticeText.UnexpectedFormat,
            CatalogWarning.Unavailable to CatalogNoticeText.CatalogUnavailable,
            CatalogWarning.SkippedItems(7) to CatalogNoticeText.SkippedItems(7)
        )
        cases.forEach { (warning, text) ->
            assertEquals(
                CatalogNotice(CatalogNoticeKind.INFO, text),
                notice(catalog = catalog, catalogWarning = warning)
            )
        }
    }

    @Test
    fun aLoadedCatalogueWithoutAWarningShowsNothing() {
        assertEquals(CatalogNotice.None, notice(catalog = catalog))
    }

    @Test
    fun crossTypeSearchWithACatalogueKeepsTodaysNotice() {
        assertEquals(
            CatalogNotice(CatalogNoticeKind.INFO, CatalogNoticeText.SearchAll),
            notice(catalog = catalog, searchAll = true, tab = SourceType.MOVIE)
        )
    }

    @Test
    fun everyVisibleNoticeCarriesAMessageAndOnlyDegradedOnesCarryAnAction() {
        // Guards the UI contract: no message, no line; a message with an action
        // only when the state is degraded.
        val notices = listOf(
            notice(),
            notice(tab = SourceType.LIVE),
            notice(catalogWarning = CatalogWarning.Unavailable),
            notice(catalog = catalog, catalogWarning = CatalogWarning.SkippedItems(1)),
            notice(catalog = catalog, searchAll = true),
            notice(catalog = catalog)
        )
        notices.forEach { current ->
            assertEquals(
                "message present iff the kind is not NONE: $current",
                current.kind != CatalogNoticeKind.NONE,
                current.text != null
            )
            if (current.kind == CatalogNoticeKind.ACTION_REFRESH) {
                assertTrue("degraded notice without a message: $current", current.text != null)
            }
        }
    }

    // ------------------------------------------------- plain-URL lists (P1)

    @Test
    fun plainUrlListsNeverShowAnyNotice() {
        // AGENTS.md: a plain-M3U list is never augmented with API calls, so the
        // flat list is the documented behaviour, not a degradation.
        for (tab in listOf(SourceType.MOVIE, SourceType.SERIES, SourceType.LIVE, null)) {
            assertEquals(CatalogNotice.None, notice(kind = PlaylistKind.URL, tab = tab))
            assertEquals(
                CatalogNotice.None,
                notice(
                    kind = PlaylistKind.URL,
                    catalogWarning = CatalogWarning.Unavailable,
                    tab = tab
                )
            )
        }
    }

    @Test
    fun plainUrlListsStaySilentWhileRefreshingAndSearching() {
        assertEquals(
            CatalogNotice.None,
            notice(kind = PlaylistKind.URL, isLoading = true, tab = SourceType.MOVIE)
        )
        assertEquals(
            CatalogNotice.None,
            notice(kind = PlaylistKind.URL, searchAll = true, tab = SourceType.MOVIE)
        )
        assertEquals(
            CatalogNotice.None,
            notice(
                kind = PlaylistKind.URL,
                catalogWarning = CatalogWarning.Unavailable,
                isLoading = true,
                searchAll = true,
                tab = SourceType.SERIES
            )
        )
    }

    // ----------------------------------------------------------- purity

    @Test
    fun theSelectionIsPure() {
        // Same inputs, same result, regardless of call history: no state is
        // read or written (it takes no ViewModel and no DataStore).
        val first = notice(tab = SourceType.MOVIE)
        repeat(3) {
            assertEquals(first, notice(tab = SourceType.MOVIE))
        }
        assertNotNull(first.text)
        // And the inputs still produce their own answer, unaffected by the
        // calls above.
        assertEquals(CatalogNotice.None, notice(catalog = catalog))
    }

    @Test
    fun noticeTextContainsNoUrlOrProviderHost() {
        // The notice must never embed a playlist URL or a provider host, so the
        // message strings themselves are checked, not the call sites.
        val strings = stringsXml()
        val catalogStrings = Regex("""<string name="(catalog_[a-z_]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(strings)
            .associate { it.groupValues[1] to it.groupValues[2] }

        assertTrue(
            "catalog_* strings missing from strings.xml: $catalogStrings",
            catalogStrings.keys.containsAll(
                listOf(
                    "catalog_missing",
                    "catalog_unavailable",
                    "catalog_skipped_items",
                    "catalog_empty_response",
                    "catalog_invalid_json",
                    "catalog_unexpected_format"
                )
            )
        )
        catalogStrings.forEach { (name, value) ->
            val lower = value.lowercase()
            for (forbidden in listOf("http", "://", "www.", "username", "password", "get.php")) {
                assertFalse(
                    "$name embeds '$forbidden': $value",
                    lower.contains(forbidden)
                )
            }
            assertTrue("$name is empty", value.isNotBlank())
        }
        // The action reuses the existing key instead of adding a second
        // "Actualizar" string.
        assertTrue(strings.contains("<string name=\"refresh\">Actualizar</string>"))
        assertEquals(
            1,
            Regex("""<string name="refresh">""").findAll(strings).count()
        )
    }

    /** Reads `strings.xml` from the module directory Gradle runs tests in. */
    private fun stringsXml(): String {
        val candidates = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml")
        )
        val file = candidates.firstOrNull { it.isFile }
        assertTrue(
            "strings.xml not found in ${candidates.map { it.absolutePath }}",
            file != null
        )
        return file!!.readText()
    }
}
