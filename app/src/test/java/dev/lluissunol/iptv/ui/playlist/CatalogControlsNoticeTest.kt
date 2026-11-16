package dev.lluissunol.iptv.ui.playlist

import dev.lluissunol.iptv.data.PanelFixtures
import dev.lluissunol.iptv.data.PanelFixtures.Variant
import dev.lluissunol.iptv.data.model.CatalogWarning
import dev.lluissunol.iptv.data.model.PlaylistKind
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.VodCatalog
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The notice rows added by the sort/filter feature and their priority. */
class CatalogControlsNoticeTest {

    private val catalog: VodCatalog = PanelFixtures.catalog(Variant.MEASURED)

    private fun notice(
        kind: PlaylistKind = PlaylistKind.XTREAM,
        catalog: VodCatalog? = this.catalog,
        warning: CatalogWarning? = null,
        tab: SourceType? = SourceType.MOVIE,
        searchAll: Boolean = false,
        isLoading: Boolean = false,
        unreachable: Int = 0,
        outdated: Boolean = false,
        reset: Boolean = false,
        hint: Boolean = false
    ) = catalogNotice(
        kind = kind,
        catalog = catalog,
        catalogWarning = warning,
        tab = tab,
        searchAll = searchAll,
        isLoading = isLoading,
        unreachablePosters = unreachable,
        catalogOutdated = outdated,
        controlsReset = reset,
        showMovieHint = hint
    )

    private fun of(kind: CatalogNoticeKind, text: CatalogNoticeText) = CatalogNotice(kind, text)

    @Test
    fun outdatedCacheOffersTheRefresh() {
        assertEquals(
            of(CatalogNoticeKind.ACTION_REFRESH, CatalogNoticeText.CatalogOutdated),
            notice(outdated = true)
        )
        assertEquals(
            of(CatalogNoticeKind.ACTION_REFRESH, CatalogNoticeText.CatalogOutdated),
            notice(outdated = true, tab = SourceType.SERIES)
        )
    }

    @Test
    fun outdatedNoticeIsSuppressedWhileARefreshRuns() {
        // The refresh is the remedy: no "refresh" nag while it is in flight.
        assertEquals(CatalogNotice.None, notice(outdated = true, isLoading = true))
    }

    @Test
    fun controlsResetIsPassive() {
        assertEquals(
            of(CatalogNoticeKind.INFO, CatalogNoticeText.ControlsReset),
            notice(reset = true)
        )
    }

    @Test
    fun movieHintIsDismissibleAndOnlyOnMovies() {
        assertEquals(
            of(CatalogNoticeKind.ACTION_DISMISS, CatalogNoticeText.MovieDetailHint),
            notice(hint = true)
        )
        assertEquals(CatalogNotice.None, notice(hint = true, tab = SourceType.SERIES))
        assertEquals(CatalogNotice.None, notice(hint = true, tab = SourceType.LIVE))
        assertEquals(CatalogNotice.None, notice(hint = true, tab = null))
    }

    @Test
    fun priorityIsWarningOutdatedResetHintThenPosters() {
        val warning = CatalogWarning.SkippedItems(3)
        assertEquals(
            of(CatalogNoticeKind.INFO, CatalogNoticeText.SkippedItems(3)),
            notice(warning = warning, outdated = true, reset = true, hint = true, unreachable = 1)
        )
        assertEquals(
            of(CatalogNoticeKind.ACTION_REFRESH, CatalogNoticeText.CatalogOutdated),
            notice(outdated = true, reset = true, hint = true, unreachable = 1)
        )
        assertEquals(
            of(CatalogNoticeKind.INFO, CatalogNoticeText.ControlsReset),
            notice(reset = true, hint = true, unreachable = 1)
        )
        // The hint outranks the poster notice, but only until it is dismissed.
        assertEquals(
            of(CatalogNoticeKind.ACTION_DISMISS, CatalogNoticeText.MovieDetailHint),
            notice(hint = true, unreachable = 1)
        )
        assertEquals(
            of(CatalogNoticeKind.INFO, CatalogNoticeText.PostersUnreachable(1, catalog.movies.size)),
            notice(hint = false, unreachable = 1)
        )
    }

    @Test
    fun outdatedWhileLoadingFallsThroughToTheNextRow() {
        assertEquals(
            of(CatalogNoticeKind.INFO, CatalogNoticeText.ControlsReset),
            notice(outdated = true, isLoading = true, reset = true)
        )
    }

    @Test
    fun newNoticesNeverAppearWithoutACatalogueOrOnPlainLists() {
        // No catalogue: the existing missing-catalogue rows win, not the new ones.
        assertEquals(
            of(CatalogNoticeKind.ACTION_REFRESH, CatalogNoticeText.CatalogMissing),
            notice(catalog = null, outdated = true, reset = true, hint = true)
        )
        // A plain M3U list is never augmented: never a notice.
        assertEquals(
            CatalogNotice.None,
            notice(kind = PlaylistKind.URL, catalog = null, outdated = true, reset = true, hint = true)
        )
        // Cross-type search shows the existing line only.
        assertEquals(
            of(CatalogNoticeKind.INFO, CatalogNoticeText.SearchAll),
            notice(searchAll = true, outdated = true, hint = true)
        )
    }

    @Test
    fun theNewTextsExistAndAreActionable() {
        val strings = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml"))
            .first { it.isFile }.readText()
        for (name in listOf("catalog_outdated", "controls_reset", "movie_detail_hint", "got_it")) {
            assertEquals(name, 1, Regex("""<string name="$name">""").findAll(strings).count())
        }
        // The outdated text points at the remedy and does not promise a feature a
        // bare panel would not deliver after the refresh.
        val outdated = Regex("""<string name="catalog_outdated">([^<]*)""").find(strings)!!.groupValues[1]
        assertEquals(true, outdated.contains("Actualiza la lista"))
        assertEquals(false, outdated.contains("género"))
    }
}
