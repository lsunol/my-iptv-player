package dev.lluissunol.iptv.data.model

/**
 * A problem found while building the Xtream catalogue, or the fact that the
 * catalogue is missing altogether.
 *
 * It is a typed value, not a string: the data layer never owns user-facing text
 * (AGENTS.md requires every visible string to live in `strings.xml`) and the
 * "skipped N items" case needs a number the UI formats with
 * `catalog_skipped_items`. The UI maps each variant to its resource in
 * `dev.lluissunol.iptv.ui.playlist`.
 *
 * Warnings are per load. They are never persisted (only the merged
 * [VodCatalog] is cached), so a list opened from the disk cache without a
 * catalogue reports "not loaded yet" rather than the failure of a previous
 * session.
 */
sealed interface CatalogWarning {
    /** The API answered without data for at least one endpoint. */
    data object EmptyResponse : CatalogWarning

    /** The API body was not valid JSON. */
    data object InvalidJson : CatalogWarning

    /** The API body was JSON but not the expected array of objects. */
    data object UnexpectedFormat : CatalogWarning

    /** Individual catalogue entries were dropped because their data was unusable. */
    data class SkippedItems(val count: Int) : CatalogWarning

    /**
     * No catalogue at all: the `player_api.php` calls failed, or answered with
     * empty catalogues while the M3U clearly has movies/series. The M3U still
     * works, so the app degrades to the flat list.
     */
    data object Unavailable : CatalogWarning
}
