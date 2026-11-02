package dev.lluissunol.iptv.ui.playlist

import dev.lluissunol.iptv.data.model.Channel
import dev.lluissunol.iptv.data.model.Playlist
import dev.lluissunol.iptv.data.model.SeriesCard
import dev.lluissunol.iptv.data.model.SourceType
import dev.lluissunol.iptv.data.model.VodCatalog
import dev.lluissunol.iptv.data.model.toChannel

/**
 * One row of the Favorites screen.
 *
 * A favourite is not always a channel: a series has no single stream URL, so it
 * is kept as a card and tapping it opens the drill-down. Keeping one list with
 * two shapes is what lets a catalogue-only favourite — a starred series, or a
 * movie whose fallback URL never appears in the M3U — show up, be counted by
 * the tab pills and be un-starred from this screen instead of only from the
 * grid cell that starred it.
 */
sealed interface FavoriteEntry {
    /** Stable `LazyColumn` key. Unique across the whole favourites list. */
    val rowKey: String

    /** Key shared with the star buttons and the persisted preferences. */
    val favoriteKey: String

    /** Drives the tab pills and the row `contentType`. */
    val sourceType: SourceType
}

/** A playable favourite: an M3U channel, or a catalogue movie mapped to one. */
data class ChannelFavorite(val channel: Channel) : FavoriteEntry {
    override val rowKey: String get() = channel.id
    override val favoriteKey: String get() = channel.favoriteKey
    override val sourceType: SourceType get() = channel.sourceType
}

/** A starred series: tapping it opens the seasons/episodes drill-down. */
data class SeriesFavorite(val card: SeriesCard) : FavoriteEntry {
    override val rowKey: String get() = "series:" + card.seriesId
    override val favoriteKey: String get() = card.favoriteKey
    override val sourceType: SourceType get() = SourceType.SERIES
}

/**
 * Rows of the Favorites screen for one snapshot of (playlist, catalogue, keys).
 *
 * Channels come first and keep every quality variant sharing a favourite key
 * (that is deliberate in v1). Catalogue items are appended only when no channel
 * already carries their key, so nothing is listed twice: that pass is the only
 * way a starred series (`series:<id>`) and an unmatched movie (whose fallback
 * URL is not in the M3U, so its key is `url:<hash>`) become visible here.
 *
 * Pure, and one linear scan per list: the caller runs it off the main thread.
 */
internal fun buildFavorites(
    playlist: Playlist?,
    catalog: VodCatalog?,
    keys: Set<String>
): List<FavoriteEntry> {
    if (keys.isEmpty()) return emptyList()
    val result = ArrayList<FavoriteEntry>(keys.size)
    val seen = HashSet<String>(keys.size)

    playlist?.channels?.forEach { channel ->
        if (channel.favoriteKey in keys) {
            result += ChannelFavorite(channel)
            seen += channel.favoriteKey
        }
    }
    catalog?.movies?.forEach { item ->
        if (item.favoriteKey in keys && seen.add(item.favoriteKey)) {
            result += ChannelFavorite(item.toChannel())
        }
    }
    catalog?.series?.forEach { card ->
        if (card.favoriteKey in keys && seen.add(card.favoriteKey)) {
            result += SeriesFavorite(card)
        }
    }
    return result
}
