package dev.lluissunol.iptv.ui.playlist

import dev.lluissunol.iptv.data.model.CatalogWarning
import dev.lluissunol.iptv.data.model.PlaylistKind
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.VodCatalog

/**
 * Which one-line notice (if any) belongs under the playlist header.
 *
 * - [NONE]: nothing to say; the tab is behaving as documented.
 * - [INFO]: a passive notice, no action offered.
 * - [ACTION_REFRESH]: the state is degraded **and** the user has a remedy, so
 *   the notice carries an "Actualizar" button.
 */
enum class CatalogNoticeKind { NONE, INFO, ACTION_REFRESH }

/**
 * The message of a notice, as a value instead of a sentence.
 *
 * The text lives in `strings.xml` (AGENTS.md: no user-facing string in Kotlin,
 * and `catalog_skipped_items` needs a `%1$d` the UI must format). Keeping the
 * variants typed also lets this file stay free of Compose and Android, so the
 * selection can be unit-tested on the JVM — the only way to verify the
 * first-run/degraded state, since reproducing it on the test device would mean
 * wiping the user's configured list.
 */
sealed interface CatalogNoticeText {
    /** Xtream list, no catalogue in the cache and no refresh has failed yet. */
    data object CatalogMissing : CatalogNoticeText

    /** The last refresh tried the API and it did not answer a usable catalogue. */
    data object CatalogUnavailable : CatalogNoticeText

    /** Part of the catalogue was dropped while decoding. */
    data class SkippedItems(val count: Int) : CatalogNoticeText

    data object EmptyResponse : CatalogNoticeText

    data object InvalidJson : CatalogNoticeText

    data object UnexpectedFormat : CatalogNoticeText

    /** Cross-type search: the flat list is intentional, not a degradation. */
    data object SearchAll : CatalogNoticeText
}

/** A notice: its kind and, when there is one, what it says. */
data class CatalogNotice(
    val kind: CatalogNoticeKind,
    val text: CatalogNoticeText?
) {
    companion object {
        val None = CatalogNotice(CatalogNoticeKind.NONE, null)
    }
}

/**
 * Picks the notice for the current tab. Pure: it reads no ViewModel, no
 * DataStore, no resources and no clock, so every state in the table below can
 * be asserted directly.
 *
 * First match wins:
 * 1. Only the Movies and Series tabs can be degraded; Live and All never are.
 * 2. A cross-type search falls back to the flat list on purpose, so the
 *    missing/failed-catalogue notices are suppressed there (the existing
 *    `catalog_search_all` line covers the case with a catalogue).
 * 3. A plain-URL list is never augmented with API calls (AGENTS.md), so it has
 *    no catalogue and nothing to explain: never a notice.
 * 4. With a catalogue, only a decode warning remains worth showing, passively:
 *    the grid works.
 * 5. Without a catalogue but with a warning, the last refresh failed while the
 *    M3U worked: explain and offer the retry.
 * 6. Without both, the catalogue has simply not been downloaded for this list
 *    yet (first run, an install upgraded from a version without the catalogue
 *    cache, or a corrupt cache file). Same remedy as 5 — unless a refresh is
 *    already running, in which case the progress bar is the better answer.
 */
fun catalogNotice(
    kind: PlaylistKind,
    catalog: VodCatalog?,
    catalogWarning: CatalogWarning?,
    tab: SourceType?,
    searchAll: Boolean,
    isLoading: Boolean
): CatalogNotice {
    if (tab != SourceType.MOVIE && tab != SourceType.SERIES) return CatalogNotice.None
    if (searchAll) {
        return if (catalog != null) {
            CatalogNotice(CatalogNoticeKind.INFO, CatalogNoticeText.SearchAll)
        } else {
            CatalogNotice.None
        }
    }
    if (kind == PlaylistKind.URL) return CatalogNotice.None
    if (catalog != null) {
        return catalogWarning
            ?.let { CatalogNotice(CatalogNoticeKind.INFO, it.toNoticeText()) }
            ?: CatalogNotice.None
    }
    if (catalogWarning != null) {
        return CatalogNotice(
            CatalogNoticeKind.ACTION_REFRESH,
            CatalogNoticeText.CatalogUnavailable
        )
    }
    if (isLoading) return CatalogNotice.None
    return CatalogNotice(CatalogNoticeKind.ACTION_REFRESH, CatalogNoticeText.CatalogMissing)
}

/** The message a decode warning maps to. */
internal fun CatalogWarning.toNoticeText(): CatalogNoticeText = when (this) {
    is CatalogWarning.SkippedItems -> CatalogNoticeText.SkippedItems(count)
    CatalogWarning.EmptyResponse -> CatalogNoticeText.EmptyResponse
    CatalogWarning.InvalidJson -> CatalogNoticeText.InvalidJson
    CatalogWarning.UnexpectedFormat -> CatalogNoticeText.UnexpectedFormat
    CatalogWarning.Unavailable -> CatalogNoticeText.CatalogUnavailable
}
